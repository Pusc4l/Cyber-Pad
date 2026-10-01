using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Linq;
using System.Net.NetworkInformation;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Threading;
using CyberPad.Server.Services;

namespace CyberPad.Server.UI;

public partial class MainWindow : Window
{
    private const int UdpPort = 8888;
    private const string ConnectionMode = "xbox";

    private enum StatusLevel
    {
        Pending,
        Ok,
        Fault
    }

    private readonly FirewallService _firewall = new(UdpPort);
    private readonly ControllerManager _controllers = new();
    private readonly UdpListenerService _udp;
    private readonly ObservableCollection<SlotViewModel> _slots = new();
    private readonly ObservableCollection<LocalAddress> _addresses = new();
    private readonly DispatcherTimer _statsTimer;
    private readonly DispatcherTimer _networkDebounce;

    private string _roomId = PairingService.CreateRoomId();
    private string _payload = string.Empty;
    private bool _updatingAddresses;
    private long _lastStatsTimestamp;

    public MainWindow()
    {
        InitializeComponent();

        _udp = new UdpListenerService(UdpPort, _controllers.ProcessFrame);

        for (int id = 1; id <= ControllerManager.MaxPlayers; id++)
        {
            _slots.Add(new SlotViewModel(id));
        }

        SlotsItems.ItemsSource = _slots;
        AddressList.ItemsSource = _addresses;
        RoomText.Text = _roomId;

        _controllers.SlotChanged += OnSlotChanged;
        _udp.ListenerFaulted += OnListenerFaulted;

        _statsTimer = new DispatcherTimer(DispatcherPriority.Background)
        {
            Interval = TimeSpan.FromMilliseconds(500)
        };
        _statsTimer.Tick += OnStatsTick;

        _networkDebounce = new DispatcherTimer(DispatcherPriority.Background)
        {
            Interval = TimeSpan.FromMilliseconds(800)
        };
        _networkDebounce.Tick += OnNetworkDebounceTick;

        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;

        Loaded += OnLoaded;
        Closed += OnClosed;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        _lastStatsTimestamp = Stopwatch.GetTimestamp();
        _statsTimer.Start();

        RefreshAddresses();

        if (_controllers.Initialize(out string? driverError))
        {
            SetStatus(DriverDot, DriverText, StatusLevel.Ok, "VIGEMBUS // READY");
        }
        else
        {
            SetStatus(DriverDot, DriverText, StatusLevel.Fault, "VIGEMBUS // MISSING");
            ShowWarning((driverError ?? "ViGEmBus driver not available.") +
                        " Install ViGEmBusRuntime (v1.21.442), then restart CyberPad.");
        }

        if (_udp.Start(out string? udpError))
        {
            SetStatus(UdpDot, UdpText, StatusLevel.Ok, $"UDP // 0.0.0.0:{UdpPort} LISTENING");
        }
        else
        {
            SetStatus(UdpDot, UdpText, StatusLevel.Fault, "UDP // BIND FAILED");
            ShowWarning(udpError ?? "UDP listener failed to start.");
        }

        SetStatus(FirewallDot, FirewallText, StatusLevel.Pending, "FIREWALL // CHECKING");
        FirewallResult firewall = await _firewall.EnsureRuleAsync();

        if (firewall.Success)
        {
            SetStatus(FirewallDot, FirewallText, StatusLevel.Ok, $"FIREWALL // UDP {UdpPort} OPEN");
        }
        else
        {
            SetStatus(FirewallDot, FirewallText, StatusLevel.Fault, "FIREWALL // BLOCKED");
            ShowWarning(firewall.Message);
        }
    }

    private void OnClosed(object? sender, EventArgs e)
    {
        NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        _statsTimer.Stop();
        _networkDebounce.Stop();

        _udp.Dispose();
        _controllers.Dispose();
    }

    // ---------- Slot hub ----------

    private void OnSlotChanged(int playerId, bool isActive, string endpoint)
    {
        if (Dispatcher.HasShutdownStarted)
        {
            return;
        }

        Dispatcher.InvokeAsync(() =>
        {
            int index = playerId - 1;
            if ((uint)index < (uint)_slots.Count)
            {
                _slots[index].Apply(isActive, endpoint);
            }
        });
    }

    private void OnStatsTick(object? sender, EventArgs e)
    {
        long now = Stopwatch.GetTimestamp();
        double seconds = (now - _lastStatsTimestamp) / (double)Stopwatch.Frequency;
        _lastStatsTimestamp = now;

        if (seconds <= 0)
        {
            return;
        }

        foreach (SlotViewModel slot in _slots)
        {
            SlotSnapshot snapshot = _controllers.GetSnapshot(slot.PlayerId);
            long delta = snapshot.PacketCount >= slot.LastPacketCount
                ? snapshot.PacketCount - slot.LastPacketCount
                : snapshot.PacketCount;

            slot.LastPacketCount = snapshot.PacketCount;
            slot.Sync(snapshot.IsActive, snapshot.Endpoint, delta / seconds);
        }

        RxText.Text = $"RX {_udp.PacketsAccepted} | DROP {_udp.PacketsDropped} | CLASH {_controllers.RejectedFrames}";
    }

    private void OnListenerFaulted(string message)
    {
        if (Dispatcher.HasShutdownStarted)
        {
            return;
        }

        Dispatcher.InvokeAsync(() =>
        {
            SetStatus(UdpDot, UdpText, StatusLevel.Fault, "UDP // LISTENER STOPPED");
            ShowWarning(message);
        });
    }

    // ---------- Network / QR ----------

    private void OnNetworkAddressChanged(object? sender, EventArgs e)
    {
        if (Dispatcher.HasShutdownStarted)
        {
            return;
        }

        Dispatcher.InvokeAsync(() =>
        {
            _networkDebounce.Stop();
            _networkDebounce.Start();
        });
    }

    private void OnNetworkDebounceTick(object? sender, EventArgs e)
    {
        _networkDebounce.Stop();
        RefreshAddresses();
    }

    private void RefreshAddresses()
    {
        string? previousIp = (AddressList.SelectedItem as LocalAddress)?.Ip;
        var previousIps = new HashSet<string>(_addresses.Select(a => a.Ip));
        IReadOnlyList<LocalAddress> fresh = NetworkInfoService.GetLocalAddresses();

        _updatingAddresses = true;
        try
        {
            _addresses.Clear();
            foreach (LocalAddress address in fresh)
            {
                _addresses.Add(address);
            }

            // A newly appeared USB tether interface wins; otherwise keep the previous choice.
            LocalAddress? pick =
                fresh.FirstOrDefault(a => a.IsUsbTether && !previousIps.Contains(a.Ip))
                ?? fresh.FirstOrDefault(a => a.Ip == previousIp)
                ?? fresh.FirstOrDefault();

            AddressList.SelectedItem = pick;
        }
        finally
        {
            _updatingAddresses = false;
        }

        EmptyAddressText.Visibility = fresh.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        RebuildQr();
    }

    private void RebuildQr()
    {
        if (AddressList.SelectedItem is LocalAddress address)
        {
            _payload = PairingService.BuildPayload(address.Ip, UdpPort, ConnectionMode, _roomId);
            QrImage.Source = PairingService.RenderQr(_payload);
            QrPlaceholder.Visibility = Visibility.Collapsed;
            PayloadText.Text = _payload;
        }
        else
        {
            _payload = string.Empty;
            QrImage.Source = null;
            QrPlaceholder.Visibility = Visibility.Visible;
            PayloadText.Text = "NO ACTIVE NETWORK INTERFACE";
        }
    }

    private void AddressList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_updatingAddresses)
        {
            return;
        }

        RebuildQr();
    }

    // ---------- Buttons ----------

    private void NewRoomButton_Click(object sender, RoutedEventArgs e)
    {
        _roomId = PairingService.CreateRoomId();
        RoomText.Text = _roomId;
        RebuildQr();
    }

    private async void CopyLinkButton_Click(object sender, RoutedEventArgs e)
    {
        if (_payload.Length == 0)
        {
            return;
        }

        try
        {
            Clipboard.SetText(_payload);
            CopyLinkButton.Content = "COPIED";
        }
        catch (System.Runtime.InteropServices.ExternalException)
        {
            CopyLinkButton.Content = "CLIPBOARD BUSY";
        }

        await Task.Delay(1200);
        CopyLinkButton.Content = "COPY LINK";
    }

    private void MinimizeButton_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void CloseButton_Click(object sender, RoutedEventArgs e) => Close();

    // ---------- Status helpers ----------

    private void ShowWarning(string message)
    {
        WarningText.Text = WarningBanner.Visibility == Visibility.Visible && WarningText.Text.Length > 0
            ? WarningText.Text + Environment.NewLine + message
            : message;
        WarningBanner.Visibility = Visibility.Visible;
    }

    private void SetStatus(Ellipse dot, TextBlock label, StatusLevel level, string message)
    {
        string brushKey = level switch
        {
            StatusLevel.Ok => "NeonCyanBrush",
            StatusLevel.Fault => "ElectricPinkBrush",
            _ => "CyberYellowBrush"
        };

        var brush = (Brush)FindResource(brushKey);
        dot.Fill = brush;
        label.Foreground = brush;
        label.Text = message;
    }
}
