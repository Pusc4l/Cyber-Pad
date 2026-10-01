using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.Runtime.CompilerServices;

namespace CyberPad.Server.UI;

public sealed class SlotViewModel : INotifyPropertyChanged
{
    private const string IdleEndpoint = "AWAITING SIGNAL";

    private bool _isActive;
    private string _status = "STANDBY";
    private string _endpoint = IdleEndpoint;
    private string _rate = "0 HZ";

    public SlotViewModel(int playerId)
    {
        PlayerId = playerId;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public int PlayerId { get; }

    public string Badge => "P" + PlayerId.ToString(CultureInfo.InvariantCulture);

    /// <summary>Last packet counter seen by the stats timer (used to derive packets/second).</summary>
    public long LastPacketCount { get; set; }

    public bool IsActive
    {
        get => _isActive;
        private set => SetField(ref _isActive, value);
    }

    public string Status
    {
        get => _status;
        private set => SetField(ref _status, value);
    }

    public string Endpoint
    {
        get => _endpoint;
        private set => SetField(ref _endpoint, value);
    }

    public string Rate
    {
        get => _rate;
        private set => SetField(ref _rate, value);
    }

    public void Apply(bool active, string endpoint)
    {
        IsActive = active;
        Status = active ? "LINKED" : "STANDBY";
        Endpoint = active && endpoint.Length > 0 ? endpoint : IdleEndpoint;

        if (!active)
        {
            Rate = "0 HZ";
        }
    }

    public void Sync(bool active, string endpoint, double packetsPerSecond)
    {
        Apply(active, endpoint);

        if (active)
        {
            Rate = packetsPerSecond.ToString("0", CultureInfo.InvariantCulture) + " HZ";
        }
    }

    private void SetField<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return;
        }

        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }
}
