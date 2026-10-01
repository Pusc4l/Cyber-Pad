using System;
using System.Threading;
using System.Windows;

namespace CyberPad.Server;

public partial class App : Application
{
    private Mutex? _singleInstance;
    private bool _ownsMutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        _singleInstance = new Mutex(true, @"Local\CyberPad.Server.SingleInstance", out bool createdNew);
        _ownsMutex = createdNew;

        if (!createdNew)
        {
            MessageBox.Show(
                "CyberPad Server is already running.",
                "CyberPad Server",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            Shutdown();
            return;
        }

        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show(
                args.Exception.Message,
                "CyberPad Server - Unexpected Error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            args.Handled = true;
        };

        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_ownsMutex)
        {
            try { _singleInstance?.ReleaseMutex(); }
            catch (ApplicationException) { }
        }

        _singleInstance?.Dispose();
        base.OnExit(e);
    }
}
