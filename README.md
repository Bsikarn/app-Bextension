# Bextension

An Android multi-display workspace launcher and input router for tablets connected to external displays. Bextension can launch apps on a selected display, blank individual screens, and provide a virtual mouse bridge for secondary displays.

## Tech Stack

### Language
- Kotlin

### UI
- Jetpack Compose
- Material 3

### Architecture
- MVVM / Clean Architecture
- StateFlow
- Repository pattern
- Dependency container

### Android APIs
- DisplayManager / DisplayListener
- ActivityOptions multi-display launching
- Presentation API
- AccessibilityService
- GestureDescription
- Pointer Capture
- Accessibility mouse MotionEvents on Android 14+

### Build Tools
- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- JDK 17
- Compile SDK 37

---

## Active Features

- **Dynamic Display Detection** — Detects Display 0 and connected external displays in real time, including display name, physical resolution, density, and refresh rate.
- **Per-Display App Routing** — Launches an installed app on a selected display through `ActivityOptions.setLaunchDisplayId()`.
- **Display-Aware Launch Bounds** — Applies bounds calculated from the target display so launched activities use the external display's available resolution.
- **Installed App Picker** — Searchable grid of launchable apps with application icons, labels, and package names.
- **Per-Display Blackout** — Uses a black `Presentation` on external displays and a zero-brightness fullscreen view on the tablet.
- **Main-Screen Wake Gesture** — Double-tap or press Back to leave the tablet blackout screen.
- **Floating Display Controller** — A collapsible `<` tab is attached to the edge of every connected display.
- **Virtual External-Display Cursor** — Draws a compact black cursor with a white outline on the selected external display.
- **Mouse Gesture Routing** — Supports click, long press, live click-and-drag, and queued wheel scrolling through accessibility gestures.
- **Android 14 Global Mouse Relay** — Captures mouse MotionEvents through `AccessibilityService` so they are not delivered to Display 0 while virtual control is active.
- **Moonlight Direct Handoff** — Releases Bextension's virtual control when a Moonlight streaming session takes focus, allowing Moonlight to receive direct mouse and keyboard input.
- **Emergency Stop** — Turning off the tablet screen releases input, closes overlays and blackout presentations, disables the accessibility relay, and stops the app process.
- **Restricted Settings Shortcut** — Opens Bextension's App info page to make Android's “Allow restricted settings” control easier to reach.
- **Custom Launcher Icon** — Uses the Bextension “B” artwork supplied with the project.

---

## Directory Structure

```text
Bextension/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/workspacerouter/
│       │   ├── BextensionApp.kt                 # Application entry point
│       │   ├── data/
│       │   │   ├── apps/AndroidAppRepository.kt # Launchable app discovery
│       │   │   ├── display/AndroidDisplayRepository.kt # Display detection
│       │   │   └── executor/AndroidWorkspaceCommandExecutor.kt # Launch commands
│       │   ├── di/AppContainer.kt               # Dependency container
│       │   ├── domain/
│       │   │   ├── executor/                    # Workspace command contract
│       │   │   ├── model/                       # Display and routing models
│       │   │   └── repository/                  # Repository contracts
│       │   ├── inputrelay/
│       │   │   ├── InputRelayAccessibilityService.kt # Mouse relay and overlays
│       │   │   └── InputCaptureActivity.kt      # Cursor capture/hiding fallback
│       │   └── presentation/
│       │       ├── MainActivity.kt              # Compose host and blackout UI
│       │       ├── MainViewModel.kt             # Reactive application state
│       │       ├── blackout/                    # External black Presentation
│       │       └── commandcenter/               # Dashboard and app picker
│       └── res/
│           ├── drawable/ic_launcher.png
│           ├── values/                          # Strings and themes
│           └── xml/input_relay_accessibility_service.xml
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradlew.bat
```

---

## Requirements

- Android tablet with Android 8.0 or newer
- USB-C DisplayPort Alt Mode or another Android-supported external display connection
- JDK 17 for local builds
- Android SDK 37
- Android 14 or newer for the preferred global mouse relay

Multi-display activity behavior depends on the Android build and device manufacturer. Bextension has been developed around a Xiaomi Pad 6 with HyperOS; other devices may route focus, pointer icons, or secondary-display activities differently.

---

## Build

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run Android Lint with:

```powershell
.\gradlew.bat lintDebug
```

---

## Setup

1. Install the debug APK on the tablet.
2. Open Bextension.
3. Tap **Allow restricted settings**.
4. On the App info screen, open the overflow menu and select **Allow restricted settings**.
5. Return to Bextension and open **Accessibility settings**.
6. Enable **Bextension Input Relay**.
7. Connect the external display and configure each display card.
8. Tap **Launch Workspace**.
9. Expand the floating `<` tab and select **Virtual control** for the external display.

Press `Esc` to leave virtual control. Press `Ctrl + Alt + Space` to reclaim it. Turning off the tablet screen performs the emergency stop and disables the relay; enable it again before the next session.

---

## Permissions & Security

Bextension uses an Accessibility Service to observe mouse input and inject touch gestures on an external display. Accessibility access is sensitive because it can read screen content and interact with other apps. Only install builds from source you trust.

The project also includes `AndroidHiddenApiBypass` to make a best-effort attempt at hiding the OEM system cursor on Xiaomi/HyperOS. Hidden Android APIs are not guaranteed across OS updates and may be blocked by a manufacturer. The app restores the normal pointer icon whenever virtual control stops.

`QUERY_ALL_PACKAGES` and `SYSTEM_ALERT_WINDOW` are not requested. Launchable applications are discovered through a launcher intent query, and display overlays are provided by the enabled Accessibility Service.

---

## Known Limitations

- Some applications do not support launching on secondary displays.
- OEM desktop-mode and focus-routing behavior may differ from stock Android.
- Perfectly hiding the physical system cursor is not guaranteed when the ROM blocks internal pointer APIs.
- Android versions below 14 use the focused Capture Activity fallback instead of global Accessibility mouse MotionEvents.
- Virtual input is translated to accessibility touch gestures; it is not equivalent to privileged system-level input injection.
- Moonlight direct input depends on Moonlight successfully receiving focus on the external display.

