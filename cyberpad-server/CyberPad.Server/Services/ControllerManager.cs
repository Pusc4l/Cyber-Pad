using System;
using System.Diagnostics;
using System.Threading;
using CyberPad.Server.Models;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace CyberPad.Server.Services;

public readonly record struct SlotSnapshot(int PlayerId, bool IsActive, string Endpoint, long PacketCount);

/// <summary>
/// Maps Player_ID (1..4) to virtual Xbox 360 controllers. A slot is claimed by the first endpoint
/// that sends its Player_ID, and recycled after 3000 ms without frames.
/// </summary>
public sealed class ControllerManager : IDisposable
{
    public const int MaxPlayers = 4;
    public const int HeartbeatTimeoutMs = 3000;
    private const int SweepIntervalMs = 250;

    private sealed class Slot
    {
        public readonly object Sync = new();
        public IXbox360Controller? Pad;
        public ulong EndpointKey;
        public long LastSeenTimestamp;
        public long PacketCount;
        public string Endpoint = string.Empty;
        public bool Active;
    }

    private readonly Slot[] _slots = new Slot[MaxPlayers];
    private readonly long _timeoutTicks = Stopwatch.Frequency * HeartbeatTimeoutMs / 1000;

    private ViGEmClient? _client;
    private Timer? _sweeper;
    private volatile bool _disposed;
    private long _rejectedFrames;

    public ControllerManager()
    {
        for (int i = 0; i < MaxPlayers; i++)
        {
            _slots[i] = new Slot();
        }
    }

    /// <summary>Raised when a slot connects or disconnects: (playerId, isActive, endpoint).</summary>
    public event Action<int, bool, string>? SlotChanged;

    public bool IsDriverAvailable => _client is not null;

    /// <summary>Frames rejected because another endpoint already owns that Player_ID.</summary>
    public long RejectedFrames => Interlocked.Read(ref _rejectedFrames);

    public bool Initialize(out string? error)
    {
        if (_client is not null)
        {
            error = null;
            return true;
        }

        try
        {
            _client = new ViGEmClient();
            _sweeper = new Timer(Sweep, null, SweepIntervalMs, SweepIntervalMs);
            error = null;
            return true;
        }
        catch (Exception ex)
        {
            _client = null;
            error = "ViGEmBus driver not available: " + ex.Message;
            return false;
        }
    }

    /// <summary>Hot path (60 Hz per player). Allocation-free except once per slot activation.</summary>
    public void ProcessFrame(in GamepadFrame frame, ulong endpointKey)
    {
        if (_disposed)
        {
            return;
        }

        int index = frame.PlayerId - 1;
        if ((uint)index >= MaxPlayers)
        {
            return;
        }

        Slot slot = _slots[index];
        bool activated = false;
        string endpoint = string.Empty;

        lock (slot.Sync)
        {
            if (slot.Active)
            {
                if (slot.EndpointKey != endpointKey)
                {
                    Interlocked.Increment(ref _rejectedFrames);
                    return;
                }
            }
            else
            {
                ViGEmClient? client = _client;
                if (client is null)
                {
                    return;
                }

                IXbox360Controller pad;
                try
                {
                    pad = client.CreateXbox360Controller();
                    pad.AutoSubmitReport = false;
                    pad.Connect();
                }
                catch (Exception)
                {
                    return;
                }

                slot.Pad = pad;
                slot.EndpointKey = endpointKey;
                slot.Endpoint = UdpListenerService.FormatEndpoint(endpointKey);
                slot.PacketCount = 0;
                slot.Active = true;
                activated = true;
                endpoint = slot.Endpoint;
            }

            slot.LastSeenTimestamp = Stopwatch.GetTimestamp();
            slot.PacketCount++;

            IXbox360Controller? active = slot.Pad;
            if (active is not null)
            {
                Apply(active, in frame);
            }
        }

        if (activated)
        {
            SlotChanged?.Invoke(frame.PlayerId, true, endpoint);
        }
    }

    public SlotSnapshot GetSnapshot(int playerId)
    {
        int index = playerId - 1;
        if ((uint)index >= MaxPlayers)
        {
            return new SlotSnapshot(playerId, false, string.Empty, 0);
        }

        Slot slot = _slots[index];
        lock (slot.Sync)
        {
            return new SlotSnapshot(playerId, slot.Active, slot.Endpoint, slot.PacketCount);
        }
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        _disposed = true;
        _sweeper?.Dispose();
        _sweeper = null;

        for (int i = 0; i < MaxPlayers; i++)
        {
            ReleaseSlot(i, onlyIfExpired: false);
        }

        _client?.Dispose();
        _client = null;
    }

    private static void Apply(IXbox360Controller pad, in GamepadFrame f)
    {
        byte lo = f.ButtonsLow;
        byte hi = f.ButtonsHigh;

        pad.SetButtonState(Xbox360Button.A, (lo & ProtocolBits.A) != 0);
        pad.SetButtonState(Xbox360Button.B, (lo & ProtocolBits.B) != 0);
        pad.SetButtonState(Xbox360Button.X, (lo & ProtocolBits.X) != 0);
        pad.SetButtonState(Xbox360Button.Y, (lo & ProtocolBits.Y) != 0);
        pad.SetButtonState(Xbox360Button.LeftShoulder, (lo & ProtocolBits.LeftShoulder) != 0);
        pad.SetButtonState(Xbox360Button.RightShoulder, (lo & ProtocolBits.RightShoulder) != 0);
        pad.SetButtonState(Xbox360Button.Back, (lo & ProtocolBits.Back) != 0);
        pad.SetButtonState(Xbox360Button.Start, (lo & ProtocolBits.Start) != 0);

        pad.SetButtonState(Xbox360Button.LeftThumb, (hi & ProtocolBits.LeftThumb) != 0);
        pad.SetButtonState(Xbox360Button.RightThumb, (hi & ProtocolBits.RightThumb) != 0);
        pad.SetButtonState(Xbox360Button.Up, (hi & ProtocolBits.DPadUp) != 0);
        pad.SetButtonState(Xbox360Button.Down, (hi & ProtocolBits.DPadDown) != 0);
        pad.SetButtonState(Xbox360Button.Left, (hi & ProtocolBits.DPadLeft) != 0);
        pad.SetButtonState(Xbox360Button.Right, (hi & ProtocolBits.DPadRight) != 0);

        pad.SetAxisValue(Xbox360Axis.LeftThumbX, f.ThumbLX);
        pad.SetAxisValue(Xbox360Axis.LeftThumbY, f.ThumbLY);
        pad.SetAxisValue(Xbox360Axis.RightThumbX, f.ThumbRX);
        pad.SetAxisValue(Xbox360Axis.RightThumbY, f.ThumbRY);

        pad.SetSliderValue(Xbox360Slider.LeftTrigger, f.LeftTrigger);
        pad.SetSliderValue(Xbox360Slider.RightTrigger, f.RightTrigger);

        pad.SubmitReport();
    }

    private void Sweep(object? state)
    {
        if (_disposed)
        {
            return;
        }

        for (int i = 0; i < MaxPlayers; i++)
        {
            ReleaseSlot(i, onlyIfExpired: true);
        }
    }

    private void ReleaseSlot(int index, bool onlyIfExpired)
    {
        Slot slot = _slots[index];
        IXbox360Controller? pad;

        lock (slot.Sync)
        {
            if (!slot.Active)
            {
                return;
            }

            if (onlyIfExpired && Stopwatch.GetTimestamp() - slot.LastSeenTimestamp <= _timeoutTicks)
            {
                return;
            }

            pad = slot.Pad;
            slot.Pad = null;
            slot.Active = false;
            slot.EndpointKey = 0;
            slot.Endpoint = string.Empty;
        }

        DisconnectPad(pad);

        if (!_disposed)
        {
            SlotChanged?.Invoke(index + 1, false, string.Empty);
        }
    }

    private static void DisconnectPad(IXbox360Controller? pad)
    {
        if (pad is null)
        {
            return;
        }

        try { pad.Disconnect(); }
        catch (Exception) { }

        try { (pad as IDisposable)?.Dispose(); }
        catch (Exception) { }
    }
}
