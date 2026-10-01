using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Media.Imaging;
using QRCoder;

namespace CyberPad.Server.Services;

public static class PairingService
{
    // Unambiguous alphabet (no 0/O, 1/I).
    private const string RoomAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private const int RoomIdLength = 6;
    private const int PixelsPerModule = 16;

    // Dark modules = ObsidianDark (#0D0E15); light background keeps the code reliably scannable.
    private static readonly byte[] DarkRgba = { 0x0D, 0x0E, 0x15, 0xFF };
    private static readonly byte[] LightRgba = { 0xE8, 0xFD, 0xFF, 0xFF };

    public static string CreateRoomId()
    {
        var sb = new StringBuilder(RoomIdLength);
        for (int i = 0; i < RoomIdLength; i++)
        {
            sb.Append(RoomAlphabet[RandomNumberGenerator.GetInt32(RoomAlphabet.Length)]);
        }

        return sb.ToString();
    }

    public static string BuildPayload(string ip, int port, string mode, string roomId) =>
        $"cyberpad://connect?ip={ip}&port={port}&mode={mode}&room={roomId}";

    public static BitmapSource RenderQr(string payload)
    {
        var generator = new QRCodeGenerator();
        QRCodeData data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        byte[] png = new PngByteQRCode(data).GetGraphic(PixelsPerModule, DarkRgba, LightRgba, true);

        var image = new BitmapImage();
        using (var stream = new MemoryStream(png))
        {
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
        }

        image.Freeze();
        return image;
    }
}
