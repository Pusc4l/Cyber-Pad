# CyberPad 🎮

> **Turn your Android smartphone into an ultra-low-latency virtual Xbox 360 controller for PC gaming.**

CyberPad is an open-source, zero-cost solution designed to make local multiplayer PC gaming accessible to everyone without requiring expensive physical gamepads.

---

## 💡 Why CyberPad? (Motivation)

Buying physical controllers (like official Xbox or PlayStation pads) can cost \$50–$80 each. Hosting a local 4-player co-op gaming night with friends (playing games like *Overcooked*, *Party Animals*, *FC24*, or *Mortal Kombat*) often means buying multiple controllers that rarely get used afterwards.

**CyberPad solves this problem** by converting smartphones—devices everyone already owns—into responsive, zero-root virtual gamepads that connect to your PC via high-speed local UDP network or USB Tethering.

---

## ⚡ Key Features

* **Ultra-Low Latency Streaming:** Powered by an unpadded 12-byte binary UDP payload running at **60 Hz (\~16.6ms cycle)** for real-time button response.
* **Instant QR Code Pairing:** Simply scan the QR code generated on your PC screen with your smartphone camera to connect instantly—no manual IP configuration required.
* **Native Xbox 360 Emulation:** Powered by the industry-standard **ViGEmBus** driver, allowing your PC and games (Steam, Epic Games, Emulators) to detect your smartphone as an authentic physical Xbox 360 controller.
* **Up to 4 Simultaneous Players:** Supports up to 4 virtual controller slots (P1 through P4) on a single PC for local multiplayer.
* **Cyberpunk Futuristic UI:** Designed with isolated multi-touch gesture handlers (`pointerInput`) in Jetpack Compose to prevent touch ghosting and multi-finger locking.
* **Zero-Lag USB Tethering Fallback:** Works smoothly even if Wi-Fi is unstable by using Android USB Tethering (`192.168.42.x`).

---

## 🛠️ Tech Stack & Architecture

CyberPad consists of two core applications working in sync over your local network:

### 1. 🖥️ **Windows PC Server (`/cyberpad-server`)**
* **Language & Framework:** C# (.NET 8.0 WPF)
* **Virtual Controller Engine:** `Nefarius.ViGEm.Client` (ViGEmBus SDK)
* **Networking:** Asynchronous `System.Net.Sockets.UdpClient` listening on Port `8888`
* **QR Generation:** `QRCoder`

### 2. 📱 **Android Mobile Client (`/cyberpad-android`)**
* **Language & Framework:** Kotlin (JVM Target 17+) & Jetpack Compose
* **Camera & QR Scanning:** AndroidX CameraX & Google Code Scanner
* **Networking:** Native Java `DatagramSocket` (UDP)

---

## 📋 Prerequisites

Before running CyberPad, make sure you have:

1. **Windows PC:**
   * Operating System: **Windows 10 or Windows 11 (64-bit)**
   * Runtime: [.NET 8.0 Desktop Runtime](https://dotnet.microsoft.com/download/dotnet/8.0)
   * Driver: [ViGEmBus Driver Runtime](https://github.com/nefarius/ViGEmBus/releases) *(Mandatory for Windows to emulate physical controllers)*
2. **Android Smartphone:**
   * Operating System: **Android 8.0 (Oreo) or higher**
3. **Network Connection:**
   * Both PC and Android phone must be connected to the **same Wi-Fi network** (or connected via USB cable with **USB Tethering** enabled).

---

## 🚀 Easy Setup & Usage Guide (For End-Users & Beginners)

> **Note for Beginners:** You **do NOT need to code** or install programming software (like Visual Studio or Android Studio) to use CyberPad! Follow the step-by-step guide below.

### **Step 1: Download the Application Files**
1. Navigate to the **[Releases](../../releases)** tab on the right side of this GitHub repository.
2. Under the latest release (e.g., `v1.0.0`), download two files:
   * **`CyberPad-Server-v1.0.0.zip`** (for your PC)
   * **`CyberPad-v1.0.0.apk`** (for your Android Phone)

---

### **Step 2: Set Up the PC Server**
1. Download and install the **[ViGEmBus Driver](https://github.com/nefarius/ViGEmBus/releases)** on your PC if you haven't already.
2. Extract **`CyberPad-Server-v1.0.0.zip`** to any folder on your PC.
3. Open the extracted folder and double-click **`CyberPad.Server.exe`** to launch the server application.
   > *If Windows Firewall prompts you on first launch, click **Allow Access** for Private Networks.*
4. A window will pop up showing a QR Code along with your PC's local IP address and server status.

---

### **Step 3: Set Up the Android App**
1. Transfer or download **`CyberPad-v1.0.0.apk`** directly to your Android device.
2. Tap the `.apk` file to install it.
   > *If prompted by Android, enable **"Allow installation from unknown sources"** for your file manager.*
3. Open the **CyberPad** app on your smartphone and grant Camera permissions when requested.

---

### **Step 4: Scan, Connect, and Play!**
1. On your phone, tap the **"Scan QR"** button.
2. Point your phone camera at the QR code displayed on your PC screen.
3. Once scanned, your phone status will change to **`CONNECTED`** and show an assigned Player Slot (e.g., `P1`).
4. Windows will play a device connection sound and treat your smartphone as an official **Xbox 360 Controller**!
5. Open any PC game (Steam, Epic, emulators, etc.) and start playing!

---

## 🔧 Building from Source & Publishing (For Developers)

If you want to inspect, modify, or compile the executable `.exe` and `.apk` files yourself:

### **1. Building the PC Server Executable (`.exe`)**

To generate the standalone `CyberPad.Server.exe` executable:

```powershell
# Navigate to the server project folder
cd cyberpad-server/CyberPad.Server

# Publish a single-file executable for 64-bit Windows
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./publish
```
> The compiled **`CyberPad.Server.exe`** and its dependencies will be generated inside the `./publish` folder.

---

### **2. Building the Android Client APK (`.apk`)**

1. Open the `cyberpad-android` directory in **Android Studio**.
2. Let Gradle sync project dependencies.
3. Build the APK via terminal or GUI:
   ```bash
   ./gradlew assembleDebug
   ```
> The output APK will be generated at `cyberpad-android/app/build/outputs/apk/debug/app-debug.apk`.

---

## ❓ Troubleshooting & FAQ

* **Q: Where do I find `CyberPad.Server.exe`?**  
  * **Answer:** If you downloaded the release `.zip` file from the **Releases** tab, `CyberPad.Server.exe` is inside that extracted folder. If you cloned the repository source code, you need to compile it using the `dotnet publish` command shown above.

* **Q: Games don't detect any controller when I press buttons!**  
  * **Answer:** Make sure you installed the **ViGEmBus Driver** on your PC. Without ViGEmBus, Windows cannot emulate the Xbox 360 hardware layer.

* **Q: The mobile app cannot connect to the PC!**  
  * **Answer:** Ensure both your PC and smartphone are on the exact same Wi-Fi network. Also check that **Windows Defender Firewall** isn't blocking UDP Port `8888`.

* **Q: Wi-Fi latency is laggy or inconsistent.**  
  * **Answer:** Switch to **USB Tethering Mode**: Connect your Android phone to your PC via a USB cable, turn on *USB Tethering* in Android Settings, and scan the updated QR code on the server screen.

---

## 📄 License & Copyright

Copyright (c) 2026 **Muhammad Triarso Pascal**. All rights reserved.

This project is open-source and licensed under the [BSD-3-Clause License](LICENSE).