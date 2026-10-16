# Premium System Edge RGB Border Lighting & Background Overlay Engine

Update Jarvis Assistant to feature a system-level Edge RGB Border Lighting Overlay that activates exclusively when the user exits the app. While using other apps or on the home screen during background sessions or automation tasks, the left and right screen edges illuminate with a dynamic, theme-synced RGB pulse. When inside the app, the standard UI remains clean and focused.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following requirements and user decisions are incorporated into this revision:
> - **In-App vs. Background Behavior**:
>   - **Inside App**: The edge lighting remains inactive, keeping the in-app experience clean and undistracted.
>   - **Outside App (Background)**: When the user exits the app (`onUserLeaveHint`, Home press, or switching to another app) while Jarvis is active or running background tasks, the system edge lighting overlay automatically activates along both the left and right screen borders.
> - **Touch Pass-Through**: The background overlay uses `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE` so the user can interact freely with other apps without any touch blocking.
> - **Theme Harmonization & Voice Reactivity**: The edge lighting adapts to the active app theme color palette and pulses dynamically with speech amplitude (mic input and Jarvis audio output).
> - **Overlay Permission Handling**: Uses Android's `Settings.canDrawOverlays` (`SYSTEM_ALERT_WINDOW`) with a seamless user prompt in Settings and when starting background services.

---

## 1. Overview & Core Concept

### What It Does
1. **Background System Edge Overlay (`SystemEdgeLightingOverlay`)**:
   - Manages a full-screen, touch-transparent WindowManager overlay (`TYPE_APPLICATION_OVERLAY`) with two vertical luminous RGB border glow strips on the left and right edges of the display.
   - Automatically shows when the user exits the app while Jarvis is active in the background.
   - Automatically dismisses when the user re-enters the app or terminates the background session.
2. **Audio & Automation Reactive Illumination**:
   - Streams color flow along screen edges with speed and brightness responding to audio amplitude (RMS energy when listening, TTS volume when speaking).
   - Flashes a notification sweep across the screen borders when a multi-step background task finishes or a milestone update occurs.
3. **Dynamic Theme Harmony**:
   - Colors dynamically sync with the active theme chosen in the app (Sunset Ember, Cyberpunk Neon, Emerald Obsidian, Midnight Onyx, Pearl White, or Custom theme color).
4. **Performance & Safety**:
   - Hardware-accelerated drawing on custom View using double-buffered Canvas paths and linear shaders.
   - Consumes minimal battery by throttling frame rates during silence and pausing drawing when the screen is turned off (`ACTION_SCREEN_OFF`).

### Target Audience & Persona
Users running background AI automations, voice tasks, or screen analysis who want continuous, non-intrusive visual awareness of Jarvis's background status directly on their phone screen.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **App Exit & Activation Flow**:
   - User starts Jarvis or gives a multi-step task, then presses Home or switches to WhatsApp, YouTube, or Chrome.
   - Instantly, subtle, vibrant left and right edge lights glide onto the screen borders.
   - The user seamlessly uses their phone while the glowing edges indicate Jarvis is active and listening.

2. **Active Speech / Background Voice Flow**:
   - When the user speaks a command or wake word in the background, the edge glow broadens and pulses brighter with speech volume.
   - When Jarvis replies with audio, the edges ripple in harmonic rhythm.

3. **Background Task Completion Flow**:
   - When an autonomous task or tool call finishes, a celebratory color wave sweeps along the borders, letting the user know without needing to pull down notifications.

4. **Return to App Flow**:
   - User taps the notification or opens Jarvis from Recents.
   - The system edge overlay cleanly fades out and detaches from WindowManager.
   - The in-app screen displays the standard frosted glass UI without duplicated borders.

### Visual Styling & Lighting Geometry
- **Border Geometry**:
  - Left Border Glow: `0` to `12dp` width with soft Gaussian/radial falloff into the screen.
  - Right Border Glow: Mirrored `12dp` luminous strip with soft alpha fade.
  - Curvature: Conforms to display cutout and rounded screen corners on modern Android flagships.
- **Color Gradients**:
  - *Sunset*: Coral Red (`#FF2A6D`) ➔ Warm Amber (`#FF8C00`) ➔ Gold.
  - *Cyberpunk*: Electric Cyan (`#00F0FF`) ➔ Neon Violet (`#9D00FF`) ➔ Hot Pink (`#FF007F`).
  - *Emerald*: Mint Aurora (`#00FFA3`) ➔ Deep Aqua (`#00B8D4`).
  - *Midnight*: Stellar Cobalt (`#2979FF`) ➔ Laser Blue (`#00E5FF`).

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Background-Only Activation Lifecycle
- **Chosen Approach**: Register lifecycle observer in `MainActivity` and bind to `JarvisConversationService`.
  - When `MainActivity` transitions to `STOPPED` (or `onUserLeaveHint`), if `isSessionOn` or background task is running, request `SystemEdgeLightingOverlay.show()`.
  - When `MainActivity` returns to `STARTED` / `RESUMED`, immediately invoke `SystemEdgeLightingOverlay.hide()`.
- **Why**: Strictly honors the user's explicit requirement: inside the app, the UI stays normal; outside the app, the edge lighting lights up.
- **Alternatives Considered**: Always-on edge lighting (inside and outside). Passed over because user specifically requested that inside the app the interface remains clean.

### Decision 2: Touch Passthrough Architecture
- **Chosen Approach**: WindowManager parameters:
  `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`.
- **Why**: Zero interference with keyboard, gestures, navigation pill, or app taps. The user's phone remains 100% usable.

### Decision 3: Audio Amplitude Synchronization
- **Chosen Approach**: Direct Coroutine flow collection from `JarvisConversationService.voiceVolumeFlow` within `SystemEdgeLightingOverlay`.
- **Why**: Zero latency visual synchronization with voice audio without needing extra permissions or audio recorders.

---

## 4. Technical Architecture & System Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android System Screen                      │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │     SystemEdgeLightingOverlay (WindowManager TYPE_OVERLAY)│  │
│  │                                                           │  │
│  │  [Left Border RGB Glow]         [Right Border RGB Glow]   │  │
│  │  - Width: 8dp - 14dp dynamic    - Width: 8dp - 14dp       │  │
│  │  - Alpha: Voice Amplitude       - Alpha: Voice Amplitude  │  │
│  │  - Touch: FLAG_NOT_TOUCHABLE    - Theme-derived Gradient  │  │
│  └─────────────────────────────▲─────────────────────────────┘  │
│                                │                                │
│                   show() on App Exit / hide() on App Enter      │
│                                │                                │
│  ┌─────────────────────────────┴─────────────────────────────┐  │
│  │                        Lifecycle Bridge                   │  │
│  │  - MainActivity: onUserLeaveHint() / onStop() / onResume()│  │
│  │  - JarvisConversationService: background state & mic flow │  │
│  └─────────────────────────────▲─────────────────────────────┘  │
│                                │                                │
│  ┌─────────────────────────────┴─────────────────────────────┐  │
│  │                     App Preferences & Theme               │  │
│  │  - AppPreferences: edgeLightingEnabled, pulseIntensity   │  │
│  │  - ThemeManager: active theme colors & palette extraction │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Components to Create / Modify
1. **`SystemEdgeLightingOverlay.kt`** (`ui/agent/SystemEdgeLightingOverlay.kt`):
   - WindowManager-based full-screen touch-through overlay.
   - Dual-edge Canvas rendering with gradient shader phase animation.
   - State listener reacting to `JarvisConversationService.connectionStatus` and voice volume.
2. **`MainActivity.kt` & `JarvisConversationService.kt`**:
   - Manage the overlay trigger on app backgrounding (`onUserLeaveHint` / `onStop`) and dismiss on return (`onResume`).
   - Coordinate with foreground service lifecycle.
3. **`AppPreferences.kt` & `SettingsFragment.kt`**:
   - Toggle to enable/disable background edge lighting.
   - Sensitivity and style preference options.
4. **Overlay Permission Flow**:
   - Seamless check for `Settings.canDrawOverlays(context)` with guidance if disabled.
