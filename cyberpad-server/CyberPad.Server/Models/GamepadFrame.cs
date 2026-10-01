namespace CyberPad.Server.Models;

/// <summary>Bitmask constants for the 12-byte CyberPad frame (PRD section 4.1).</summary>
public static class ProtocolBits
{
    // Byte 01 - Buttons_Low
    public const byte A = 0x01;
    public const byte B = 0x02;
    public const byte X = 0x04;
    public const byte Y = 0x08;
    public const byte LeftShoulder = 0x10;
    public const byte RightShoulder = 0x20;
    public const byte Back = 0x40;
    public const byte Start = 0x80;

    // Byte 02 - Buttons_High
    public const byte LeftThumb = 0x01;
    public const byte RightThumb = 0x02;
    public const byte DPadUp = 0x04;
    public const byte DPadDown = 0x08;
    public const byte DPadLeft = 0x10;
    public const byte DPadRight = 0x20;
}

/// <summary>Parsed 12-byte frame. Immutable value type: no heap allocation per packet.</summary>
public readonly struct GamepadFrame
{
    public const int Size = 12;

    public readonly byte PlayerId;
    public readonly byte ButtonsLow;
    public readonly byte ButtonsHigh;
    public readonly short ThumbLX;
    public readonly short ThumbLY;
    public readonly short ThumbRX;
    public readonly short ThumbRY;
    public readonly byte Triggers;

    public GamepadFrame(
        byte playerId,
        byte buttonsLow,
        byte buttonsHigh,
        short thumbLX,
        short thumbLY,
        short thumbRX,
        short thumbRY,
        byte triggers)
    {
        PlayerId = playerId;
        ButtonsLow = buttonsLow;
        ButtonsHigh = buttonsHigh;
        ThumbLX = thumbLX;
        ThumbLY = thumbLY;
        ThumbRX = thumbRX;
        ThumbRY = thumbRY;
        Triggers = triggers;
    }

    /// <summary>L2 (lower nibble 0..15) scaled to the XInput range 0..255.</summary>
    public byte LeftTrigger => (byte)((Triggers & 0x0F) * 17);

    /// <summary>R2 (upper nibble 0..15) scaled to the XInput range 0..255.</summary>
    public byte RightTrigger => (byte)((Triggers >> 4) * 17);
}
