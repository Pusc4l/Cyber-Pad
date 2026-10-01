using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace CyberPad.Server.Services;

public sealed record LocalAddress(string Ip, string AdapterName, bool IsUsbTether)
{
    public string Label => IsUsbTether ? "USB TETHER" : "WI-FI / LAN";
    public string Caption => Label + " | " + AdapterName;
}

public static class NetworkInfoService
{
    private const string UsbTetherPrefix = "192.168.42.";

    private static readonly string[] VirtualMarkers =
    {
        "vEthernet", "VMware", "VirtualBox", "Hyper-V", "Virtual", "Loopback",
        "Pseudo", "TAP-", "Bluetooth", "Tailscale", "ZeroTier", "Hamachi"
    };

    /// <summary>Active IPv4 addresses, USB tethering (192.168.42.x) first.</summary>
    public static IReadOnlyList<LocalAddress> GetLocalAddresses()
    {
        var result = new List<LocalAddress>();

        try
        {
            foreach (NetworkInterface nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up ||
                    nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel)
                {
                    continue;
                }

                foreach (UnicastIPAddressInformation unicast in nic.GetIPProperties().UnicastAddresses)
                {
                    if (unicast.Address.AddressFamily != AddressFamily.InterNetwork)
                    {
                        continue;
                    }

                    string ip = unicast.Address.ToString();
                    if (ip.StartsWith("169.254.", StringComparison.Ordinal))
                    {
                        continue;
                    }

                    bool usb = ip.StartsWith(UsbTetherPrefix, StringComparison.Ordinal);
                    if (!usb && IsVirtual(nic))
                    {
                        continue;
                    }

                    if (result.Any(a => a.Ip == ip))
                    {
                        continue;
                    }

                    result.Add(new LocalAddress(ip, nic.Name, usb));
                }
            }
        }
        catch (NetworkInformationException)
        {
            return Array.Empty<LocalAddress>();
        }

        return result
            .OrderByDescending(a => a.IsUsbTether)
            .ThenBy(a => a.Ip, StringComparer.Ordinal)
            .ToList();
    }

    private static bool IsVirtual(NetworkInterface nic)
    {
        foreach (string marker in VirtualMarkers)
        {
            if (nic.Name.Contains(marker, StringComparison.OrdinalIgnoreCase) ||
                nic.Description.Contains(marker, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }

        return false;
    }
}
