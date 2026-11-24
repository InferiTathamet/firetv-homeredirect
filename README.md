# Fire TV Autonomous Home Redirect

An autonomous, lightweight, non-root background redirection engine engineered specifically for **Amazon Fire TV devices (FireOS 6 & 7)** to permanently enforce custom launchers (such as **Projectivy Launcher**, **Wolf Launcher**, or **FLauncher**).

Tested and verified on **Amazon Fire TV Stick 4K** (`AFTMM` / `mantis`, FireOS 6.7.0.4, Android 7.1.2).

---

## 📖 The Story & The Problem

Custom launchers on Amazon Fire TV have traditionally relied on tools like *Launcher Manager* or accessibility hooks (Button Mapper). However, on newer FireOS updates (specifically FireOS 6.2.8.0+ and 6.7.0.0+), Amazon implemented aggressive lockdowns that broke existing methods:

### 1. The Protected Launcher Lockdown
Executing `pm disable-user com.amazon.tv.launcher` returns:
```text
java.lang.SecurityException: Cannot disable a protected package: com.amazon.tv.launcher
```
Amazon explicitly locked its proprietary launcher inside system privileges, preventing user-space package disabling.

### 2. Accessibility Services Disabled
FireOS 6.7 effectively blocks third-party accessibility services. Even after granting permissions via ADB, `dumpsys accessibility` returns empty service sets (`services:{}`), completely disabling accessibility-based key interceptors like Button Mapper.

### 3. The 5-Second Freeze Lockout (`ActivityManager.stopAppSwitches()`)
When the physical **Home** button on the remote is pressed, Android's `PhoneWindowManager` invokes `ActivityManager.stopAppSwitches()`. This sets a strict **5000 ms (5-second)** lockout window: any background `startActivity` intent from a non-whitelisted third-party app is quarantined in `mPendingActivityLaunches` and delayed by 5 full seconds. The permission `STOP_APP_SWITCHES` cannot be granted via ADB (`signature|privileged` restricted).

### 4. The Logcat Event Filter Blindspot
Earlier redirection scripts and apps only monitored `am_resume_activity:I`. However, when Amazon Launcher is already resident in RAM and the user presses Home from inside an app or presses Home repeatedly, Android **does not emit `am_resume_activity`**. Instead, it emits `am_new_intent` and `am_focused_activity`. Because previous tools ignored these events, the service failed to detect repeat Home presses, dropping the user back onto Amazon's ad-filled home screen.

---

## 💡 How We Solved It (The Engine Architecture)

`HomeRedirect` solves these constraints natively on the device with zero root requirement and zero PC dependency:

```
Physical Remote / App (Home Key)
       │
       ▼
ActivityManager routes intent
       │
       ├──► am_new_intent (Triggered at <1ms)
       │         │
       │         ▼
       │   RedirectService (Logcat Multi-Event Stream)
       │         │
       │         ├──► Instant SYSTEM_ALERT_WINDOW Mask (<20ms)
       │         │    [Completely shields screen with #0A0C10 overlay]
       │         │    [Absorbs d-pad keys; Amazon UI receives nothing]
       │         │
       │         └──► Pulsed Launch Retries (+0ms, +350ms, +1000ms, +2200ms, +4800ms)
       │                    │
       │                    ▼
       │              Target Launcher Takes Focus
       │                    │
       │                    ▼
       └──► am_resume_activity (Target Launcher Active)
                 │
                 ▼
           Overlay Mask Automatically Dismissed
```

1. **Multi-Event Kernel Watcher:**  
   Listens directly to raw activity events:
   `logcat -b events -v raw -T 1 am_resume_activity:I am_relaunch_resume_activity:I am_focused_activity:I am_new_intent:I *:S`  
   This captures the Home intent at **`am_new_intent`** within **<1 millisecond**, before Amazon's UI can draw a single frame.

2. **Zero-Latency Visual Masking (`SYSTEM_ALERT_WINDOW`):**  
   Because Android's internal `stopAppSwitches` delay cannot be eliminated without custom kernel privileges, the engine instantly attaches a dark `#0A0C10` overlay window (<20ms). The user **never sees the stock Amazon UI**, preventing unwanted visual flash and absorbing accidental remote clicks.

3. **Pulsed Takeover:**  
   The service pulses launch intents across the switch window. As soon as the delay window expires, the custom launcher takes the foreground, and the overlay auto-dismisses immediately.

4. **100% Standalone & Cold-Boot Resilient:**  
   - Registered with a high-priority (`priority=1000`) `BootReceiver` listening for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, and `QUICKBOOT_POWERON`.
   - Whitelisted from Android Doze / battery optimization.
   - Protected with an `AlarmManager` repeating heartbeat to guarantee sticky revival if Android's LowMemoryKiller ever intervenes.
   - Works completely offline after initial setup—no ongoing PC or ADB connection required.

---

## 🎯 Supporting Other Custom Launchers

By default, the engine is configured for **Projectivy Launcher**. You can target any custom launcher by modifying two constants in:
- `app/src/main/java/org/firetv/homeredirect/RedirectService.java`
- `app/src/main/java/org/firetv/homeredirect/BootReceiver.java`

```java
// Default: Projectivy Launcher
private static final String TARGET_PKG = "com.spocky.projengmenu";
private static final String TARGET_CLS = "com.spocky.projengmenu.ui.home.MainActivity";
```

### Popular Launcher Targets:

| Launcher | `TARGET_PKG` | `TARGET_CLS` |
| :--- | :--- | :--- |
| **Projectivy Launcher** | `com.spocky.projengmenu` | `com.spocky.projengmenu.ui.home.MainActivity` |
| **Wolf Launcher** | `com.wolf.firelauncher` | `com.wolf.firelauncher.MainActivity` |
| **FLauncher** | `me.efdev.flauncher` | `me.efdev.flauncher.MainActivity` |
| **ATV Launcher Pro** | `ca.dstudio.atvlauncher.pro` | `ca.dstudio.atvlauncher.pro.MainActivity` |
| **Leanback Launcher** | `com.google.android.leanbacklauncher` | `com.google.android.leanbacklauncher.MainActivity` |

---

## 🛠️ How to Build from Source

You can build the APK using either standard CLI tools (Java + Android SDK) or via Android Studio.

### Prerequisites:
- JDK 8 or higher
- Android SDK (`android.jar` for API 25 / Android 7.1.2)
- `d8` (or `r8.jar`)
- `apktool` (or `aapt2`)
- `jarsigner` / `apksigner`

### Compilation Steps:

```bash
# 1. Compile Java sources against Android 7.1.2 SDK
mkdir -p build/classes build/dex
javac --release 8 -cp /path/to/android.jar -d build/classes \
    $(find app/src/main/java -name "*.java")

# 2. Convert classes to DEX format
d8 --min-api 25 --output build/dex $(find build/classes -name "*.class")

# 3. Assemble resources and build APK package
apktool b app/src/main -o build/unaligned.apk

# 4. Insert compiled classes.dex into APK
cd build/dex && zip -u ../unaligned.apk classes.dex && cd ../..

# 5. Sign the APK with your release key
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore test.keystore -storepass password unaligned.apk keyalias

# 6. Copy to final release output
cp build/unaligned.apk release/HomeRedirect.apk
```

---

## 📲 Installation & ADB Setup

1. **Enable Developer Options on Fire TV:**
   - Go to `Settings -> My Fire TV -> About`.
   - Click your device name 7 times until "You are now a developer" appears.
   - Go to `Settings -> My Fire TV -> Developer Options`.
   - Turn **ADB Debugging** to **ON**.

2. **Connect via ADB:**
   ```bash
   adb connect <FIRE_TV_IP>:5555
   ```

3. **Install and Grant System Permissions:**
   ```bash
   # Install APK
   adb install -r release/HomeRedirect_v11.apk

   # Grant log reading and system overlay permissions
   adb shell "pm grant org.firetv.homeredirect android.permission.READ_LOGS"
   adb shell "pm grant org.firetv.homeredirect android.permission.SYSTEM_ALERT_WINDOW"
   adb shell "pm grant org.firetv.homeredirect android.permission.WRITE_SECURE_SETTINGS"

   # Whitelist from Doze / Battery Optimization
   adb shell "dumpsys deviceidle whitelist +org.firetv.homeredirect"

   # Start the service
   adb shell "am startservice org.firetv.homeredirect/.RedirectService"
   ```

4. **Verify:**
   - Press the **Home** button on your physical remote or mobile app.
   - The screen will transition seamlessly into your custom launcher.
   - Perform a full power reboot (`adb reboot`) to verify cold-boot persistence.

---

## 🔄 How to Revert or Uninstall

If you ever wish to return to the stock Amazon UI:

- **Temporary Pause (from TV UI):**  
  Go to `Settings -> Applications -> Manage Installed Applications -> Home Redirect -> Force Stop`.
- **Permanent Removal:**  
  Uninstall the app directly from `Manage Installed Applications` or run:
  ```bash
  adb uninstall org.firetv.homeredirect
  ```
  The Home button will immediately revert to stock Amazon behavior.

---

## 🗺️ Roadmap

- [x] Multi-event kernel logcat watcher (`am_new_intent` + `am_focused_activity` interception).
- [x] Zero-latency `SYSTEM_ALERT_WINDOW` dark visual mask.
- [x] Autonomous cold-boot survival with priority `BootReceiver`.
- [ ] On-device GUI settings activity to switch target launchers without recompilation.
- [ ] Customizable overlay visual styles (pitch black OLED, subtle branding, or blurred snapshot).
- [ ] Support for newer FireOS 8 (Android 10/11 based) event structures.

---

## 📄 License

This project is licensed under the MIT License - feel free to modify, distribute, and adapt it for your own setups.
