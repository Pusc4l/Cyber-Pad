using System;
using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using CyberPad.Server.Models;

namespace CyberPad.Server.Services;

/// <summary>
/// Dedicated-thread UDP receiver on 0.0.0.0:port. Parses 12-byte frames and hands them to a
/// delegate. The receive loop allocates nothing: one reusable buffer, one reusable SocketAddress,
/// a struct frame, and a pre-created delegate.
/// </summary>
public sealed class UdpListenerService : IDisposable
{
    public delegate void FrameHandler(in GamepadFrame frame, ulong endpointKey);

    private const int ReceiveBufferBytes = 256 * 1024;
    private const int ScratchBufferBytes = 512;

    private readonly int _port;
    private readonly FrameHandler _onFrame;

    private Socket? _socket;
    private Thread? _thread;
    private volatile bool _running;
    private long _packetsAccepted;
    private long _packetsDropped;

    public UdpListenerService(int port, FrameHandler onFrame)
    {
        _port = port;
        _onFrame = onFrame ?? throw new ArgumentNullException(nameof(onFrame));
    }

    /// <summary>Raised (on the receive thread) if the loop dies unexpectedly.</summary>
    public event Action<string>? ListenerFaulted;

    public int Port => _port;
    public bool IsRunning => _running;
    public long PacketsAccepted => Interlocked.Read(ref _packetsAccepted);
    public long PacketsDropped => Interlocked.Read(ref _packetsDropped);

    public bool Start(out string? error)
    {
        if (_running)
        {
            error = null;
            return true;
        }

        var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        try
        {
            socket.ReceiveBufferSize = ReceiveBufferBytes;
            socket.Bind(new IPEndPoint(IPAddress.Any, _port));
        }
        catch (Exception ex)
        {
            socket.Dispose();
            error = $"Cannot bind UDP 0.0.0.0:{_port} - {ex.Message}";
            return false;
        }

        _socket = socket;
        _running = true;
        _thread = new Thread(ReceiveLoop)
        {
            Name = "CyberPad-UDP-RX",
            IsBackground = true,
            Priority = ThreadPriority.AboveNormal
        };
        _thread.Start();

        error = null;
        return true;
    }

    public void Stop()
    {
        if (!_running)
        {
            return;
        }

        _running = false;

        Socket? socket = _socket;
        _socket = null;
        try { socket?.Close(); }
        catch (SocketException) { }

        Thread? thread = _thread;
        _thread = null;
        if (thread is not null && thread != Thread.CurrentThread)
        {
            thread.Join(1000);
        }
    }

    public void Dispose() => Stop();

    /// <summary>Formats an endpoint key produced by this service as "a.b.c.d:port" (not hot path).</summary>
    public static string FormatEndpoint(ulong key) =>
        $"{(key >> 40) & 0xFF}.{(key >> 32) & 0xFF}.{(key >> 24) & 0xFF}.{(key >> 16) & 0xFF}:{key & 0xFFFF}";

    private void ReceiveLoop()
    {
        Socket? socket = _socket;
        if (socket is null)
        {
            return;
        }

        byte[] buffer = new byte[ScratchBufferBytes];
        var sender = new SocketAddress(AddressFamily.InterNetwork);

        while (_running)
        {
            int received;
            try
            {
                received = socket.ReceiveFrom(buffer, SocketFlags.None, sender);
            }
            catch (SocketException ex) when (ex.SocketErrorCode is SocketError.MessageSize or SocketError.ConnectionReset)
            {
                Interlocked.Increment(ref _packetsDropped);
                continue;
            }
            catch (SocketException ex)
            {
                if (_running)
                {
                    _running = false;
                    ListenerFaulted?.Invoke("UDP listener stopped: " + ex.Message);
                }

                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }

            if (!TryParse(buffer.AsSpan(0, received), out GamepadFrame frame))
            {
                Interlocked.Increment(ref _packetsDropped);
                continue;
            }

            Interlocked.Increment(ref _packetsAccepted);
            _onFrame(in frame, ReadEndpointKey(sender));
        }
    }

    /// <summary>Strict 12-byte little-endian parse. Rejects wrong length and Player_ID outside 1..4.</summary>
    private static bool TryParse(ReadOnlySpan<byte> data, out GamepadFrame frame)
    {
        frame = default;

        if (data.Length != GamepadFrame.Size)
        {
            return false;
        }

        byte playerId = data[0];
        if (playerId < 1 || playerId > 4)
        {
            return false;
        }

        frame = new GamepadFrame(
            playerId,
            data[1],
            data[2],
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(3, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(5, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(7, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(9, 2)),
            data[11]);
        return true;
    }

    /// <summary>Packs IPv4 + port from a sockaddr_in layout into a ulong without allocating.</summary>
    private static ulong ReadEndpointKey(SocketAddress address)
    {
        ulong ip = ((ulong)address[4] << 24) | ((ulong)address[5] << 16) | ((ulong)address[6] << 8) | address[7];
        ulong port = ((ulong)address[2] << 8) | address[3];
        return (ip << 16) | port;
    }
}
