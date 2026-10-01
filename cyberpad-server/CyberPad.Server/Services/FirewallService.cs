using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Globalization;
using System.Security.Principal;
using System.Threading.Tasks;

namespace CyberPad.Server.Services;

public sealed record FirewallResult(bool Success, bool AlreadyPresent, string Message);

/// <summary>
/// Ensures the inbound UDP rule "CyberPadUDP" exists in Windows Defender Firewall via netsh.
/// If the process is not elevated, only the netsh call is elevated (one UAC prompt).
/// </summary>
public sealed class FirewallService
{
    public const string RuleName = "CyberPadUDP";

    private const int ErrorCancelledByUser = 1223;
    private const int NetshTimeoutMs = 15000;

    private readonly int _port;

    public FirewallService(int port)
    {
        _port = port;
    }

    public Task<FirewallResult> EnsureRuleAsync() => Task.Run(EnsureRule);

    private FirewallResult EnsureRule()
    {
        try
        {
            if (RuleExists())
            {
                return new FirewallResult(true, true, $"Rule '{RuleName}' already registered");
            }

            string addArgs =
                $"advfirewall firewall add rule name=\"{RuleName}\" dir=in action=allow protocol=UDP localport={_port}";

            bool elevate = !IsElevated();
            (int exitCode, string output) = RunNetsh(addArgs, elevate);

            if (RuleExists())
            {
                return new FirewallResult(true, false, $"Rule '{RuleName}' registered for UDP {_port}");
            }

            string detail = string.IsNullOrWhiteSpace(output) ? $"netsh exit code {exitCode}" : output.Trim();
            return new FirewallResult(false, false, "Firewall rule could not be registered: " + detail);
        }
        catch (Win32Exception ex) when (ex.NativeErrorCode == ErrorCancelledByUser)
        {
            return new FirewallResult(false, false, "Administrator approval was declined. Phones may be blocked until UDP " + _port + " is allowed.");
        }
        catch (Exception ex)
        {
            return new FirewallResult(false, false, "Firewall check failed: " + ex.Message);
        }
    }

    private bool RuleExists()
    {
        (int exitCode, string output) = RunNetsh($"advfirewall firewall show rule name=\"{RuleName}\"", elevate: false);
        return exitCode == 0 && output.Contains(_port.ToString(CultureInfo.InvariantCulture), StringComparison.Ordinal);
    }

    private static bool IsElevated()
    {
        using WindowsIdentity identity = WindowsIdentity.GetCurrent();
        return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
    }

    private static (int ExitCode, string Output) RunNetsh(string arguments, bool elevate)
    {
        var psi = new ProcessStartInfo("netsh.exe", arguments);

        if (elevate)
        {
            psi.UseShellExecute = true;
            psi.Verb = "runas";
            psi.WindowStyle = ProcessWindowStyle.Hidden;
        }
        else
        {
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
        }

        using Process process = Process.Start(psi)
            ?? throw new InvalidOperationException("netsh.exe could not be started.");

        string output = elevate ? string.Empty : process.StandardOutput.ReadToEnd();

        if (!process.WaitForExit(NetshTimeoutMs))
        {
            try { process.Kill(); }
            catch (InvalidOperationException) { }
            return (-1, "netsh timed out");
        }

        return (process.ExitCode, output);
    }
}
