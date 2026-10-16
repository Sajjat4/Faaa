# SessionStateManager, FloatingVoiceController & VoiceSessionService Architecture

A synchronized, multi-layered voice interaction architecture for JARVIS featuring a unified `SessionStateManager`, a high-performance `FloatingVoiceController` with Material vector action buttons and curved pill borders, and an optimized `VoiceSessionService` ensuring persistent background microphone streaming.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following confirmed decisions guide this architecture:

- **Unified State Management (`SessionStateManager`)**: Centralized singleton repository exposing observable `StateFlow` streams for voice states (`IDLE`, `CONNECTING`, `LISTENING`, `THINKING`, `SPEAKING`, `PAUSED`, `ENDED`), microphone mute/pause states, and audio frequency telemetry. Both the main app UI and the floating overlay observe and dispatch actions through this manager.
- **Floating Controller (`FloatingVoiceController`)**: A dedicated `WindowManager` controller managing the creation, positioning, edge snapping, and animation of the custom overlay view.
  - **No Emoji Buttons**: Uses pure Android Vector Drawables (`ic_pause_vector`, `ic_play_vector`, `ic_call_end_vector`, `ic_open_app_vector`) with Material ripple effects and 48dp touch targets.
  - **Curved Border Side (Curbi Border)**: Specular rounded capsule with adaptive border strokes, flowing ambient neon glows, and Material 3 dynamic colors.
- **Voice Session Service (`VoiceSessionService`)**: Dedicated voice session engine linked directly with the existing `JarvisConversationService`. Maintains continuous foreground service execution with `microphone` and `mediaPlayback` types so recording never drops when switching apps.

---

## 1. Overview & Core Concept

- **What It Does**: Unifies voice interaction state handling across the entire app ecosystem. When conversation begins, `VoiceSessionService` maintains an unbroken audio stream with Gemini Live. When the user leaves JARVIS, `FloatingVoiceController` presents a curved Material pill displaying real-time animated state waves (Listening, Thinking, Speaking) with vector action buttons to Pause, Resume, Hang Up, or Return to App.
- **Target Audience**: Power users needing reliable, continuous hands-free voice assistance while navigating other applications on their device.
- **Key Value**: Eliminates state desynchronization between UI layers and guarantees background audio continuity with clean Material 3 aesthetics.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Voice Session Start & Sync**:
   - Starting a conversation in `MainActivity` immediately dispatches a `START` intent to `VoiceSessionService`.
   - `SessionStateManager` shifts from `IDLE` -> `CONNECTING` -> `LISTENING`. Both the main Arc HUD and any active controllers update synchronously.
2. **App Exit & Floating Pill Emergence**:
   - When the user presses Home, `FloatingVoiceController` displays the curved pill over the current screen.
   - The pill's border and indicator light dynamically adopt Material colors:
     - **Listening**: Neon Cyan (`#00E5FF`) with pulsating aura.
     - **Thinking**: Amber Gold (`#F59E0B`) with subtle scanning sweep.
     - **Speaking**: Emerald (`#10B981`) with audio-reactive bouncing height.
     - **Paused**: Muted Slate (`#94A3B8`).
3. **Material Vector Controls (No Emojis)**:
   - **Pause / Resume**: Vector `ic_pause` / `ic_play` in a circular Material button with ripple. Tapping halts microphone audio chunks without closing the socket.
   - **Hang Up**: Crimson circular vector button with phone hang-up glyph (`ic_call_end`) that terminates the session immediately.
   - **Return to App**: Primary accent vector button (`ic_open_in_full`) bringing `MainActivity` to the front.
4. **Draggable & Curved Edge Snapping**:
   - Smoothly draggable along any screen coordinate with spring physics snapping to the nearest screen edge on release.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: SessionStateManager as Central Source of Truth**
  - *Chosen Approach*: `SessionStateManager` holds the definitive `StateFlow<VoiceState>` and `StateFlow<Boolean>` (isPaused) that drives both `MainActivity`/`HomeFragment` and `FloatingVoiceController`.
  - *Why*: Eliminates state fragmentation or out-of-sync indicators when the user pauses or hangs up from the floating overlay.
- **Decision 2: Pure Vector Drawables over Text/Emojis**
  - *Chosen Approach*: Created dedicated XML vector assets with standard Material design shapes instead of Unicode emojis.
  - *Why*: Guarantees crisp rendering across all screen densities, respects dynamic theme color tinting, and avoids inconsistent emoji glyph rendering across different Android OS versions.
- **Decision 3: VoiceSessionService Integration with Existing Foreground Service**
  - *Chosen Approach*: `VoiceSessionService` acts as the dedicated voice engine and coordinator, delegating audio pipeline management to the established foreground architecture while keeping clean separation of concerns.
  - *Why*: Preserves existing system automations (WhatsApp, YouTube, Screen capture) while fulfilling the requested class architecture.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     SessionStateManager                         │
│  - voiceState: StateFlow<VoiceSessionState>                     │
│    (IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED)    │
│  - audioLevel: StateFlow<Float>                                 │
│  - actions: pauseSession(), resumeSession(), endSession()       │
└───────────────▲───────────────────────────────▲─────────────────┘
                │ Dispatches / Observes         │ Dispatches / Observes
                │                               │
┌───────────────┴───────────────┐ ┌─────────────┴─────────────────┐
│      VoiceSessionService      │ │    FloatingVoiceController    │
│  (Foreground Voice Engine)    │ │    (WindowManager Overlay)    │
│  - Gemini Live WebSocket      │ │  - Curved Glass Pill Container│
│  - AudioRecord low-latency    │ │  - Motion Material Colors     │
│  - Persistent background lock │ │  - Vector Buttons (No Emojis) │
│  - AudioTrack playback        │ │  - Smooth Edge Snapping       │
└───────────────────────────────┘ └───────────────────────────────┘
```

### Proposed Source Changes

1. **`SessionStateManager.kt`** (New File in `com.jarvis.assistant.automation.session`):
   - Defines `VoiceSessionState` enum.
   - Exposes shared reactive state flows and unified commands: `requestPause()`, `requestResume()`, `requestHangUp()`, `requestStart()`.
2. **`FloatingVoiceController.kt`** (New File in `com.jarvis.assistant.ui.agent`):
   - Full WindowManager overlay with collapsible/expandable curved pill layout.
   - Vector drawables: pause, play, hang-up, expand to app.
   - Dynamic motion Material color transitions using `ValueAnimator` and `ArgbEvaluator`.
3. **`VoiceSessionService.kt`** (New File in `com.jarvis.assistant.background`):
   - Implements persistent background voice connectivity and bridges to `SessionStateManager`.
4. **Vector Drawable Assets**:
   - Create `ic_voice_pause.xml`, `ic_voice_play.xml`, `ic_voice_hangup.xml`, `ic_voice_expand.xml` in `res/drawable/`.
