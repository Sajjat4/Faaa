# Grok Bot 60 FPS Particle Animation & Dynamic Island Integration

Seamlessly integrate the Grok Bot reactive visualizer—featuring smooth 60 FPS puffing animations, dynamic neon color transitions, orbital particle physics, and zero-latency SoundPool audio feedback—into both the Jarvis Dynamic Island overlay and the floating Dynamic Overlay Avatar while fully preserving existing avatar styles.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed based on your preferences:

- **Confirmed Decision 1 (Avatar Style Selection)**: Grok Bot is added as a dedicated, fully selectable avatar style ("Grok Bot") in avatar settings and preview selectors, living alongside existing styles (MetaMuse, Droid, Classic, Neon, Cosmic, Minimal, Pixel) without breaking or replacing them.
- **Confirmed Decision 2 (Audio Trigger Timing)**: Micro-interaction audio effects (`sound_idle.mp3`, `sound_working.mp3`, `sound_speaking.mp3`) will trigger cleanly on state transitions (IDLE, WORKING/EXECUTING, SPEAKING) using a high-performance, low-latency `SoundPool` pipeline that respects mute and active voice session settings.
- **Confirmed Decision 3 (Display Surfaces)**: The animated Grok Bot visualizer will be rendered natively in both the top Dynamic Island capsule and the floating Dynamic Overlay Avatar view, as well as the settings preview panel.

---

### 1. Overview & Core Concept

- **What It Does**: Renders an interactive, futuristic Grok Bot visualizer driven by physics-based pulse scaling, dynamic radial neon glows, lateral eye/head swaying offsets, and six orbiting/puffing particle dots. When the assistant switches states (Idle ➔ Working ➔ Speaking), the color scheme smoothly transitions (Neon Cyan/Purple ➔ Neon Pink/White ➔ Neon Green/Cyan) accompanied by crisp, low-latency sound cues.
- **Target Audience / Persona**: Users seeking a responsive, cybernetic companion experience that provides glanceable assistant activity feedback anchored under the front camera or floating over any application.
- **Key Value**: Delivers fluid 60 FPS micro-animations and instantaneous tactile/acoustic feedback without blocking the UI thread or interfering with active Gemini Live conversations.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Avatar Selection*: User opens Avatar Settings, selects "Grok Bot" from the Avatar Style menu, views the real-time animated preview with puffing particles and sound feedback, and taps "Save Settings".
  2. *Dynamic Island Presentation*: When assistant voice session or background tasks are active, the Dynamic Island capsule displays the Grok Bot glow and animated pulsating core. Tapping expands control buttons (Pause, Hang Up, Open App).
  3. *State Morphing*:
     - **IDLE**: Gentle pulsing (0.85x – 1.05x), calm neon cyan (#00F0FF) glow, purple (#9D00FF) core, slow particle drift, subtle `sound_idle` trigger.
     - **WORKING / THINKING / EXECUTING**: High-tempo puffing (0.85x – 1.25x), neon pink (#FF007F) energetic glow, white (#FFFFFF) core, expanded particle radius, `sound_working` trigger.
     - **SPEAKING**: Rhythmic breathing, neon green (#00FF66) radiant glow, cyan (#00F0FF) center, audio-level reactive amplitude, `sound_speaking` trigger.

- **Visual Identity & Theme**:
  - *Color Palette Tokens*:
    - Idle: Primary `#00F0FF`, Secondary `#9D00FF`, Glow `#8000F0FF`
    - Working: Primary `#FF007F`, Secondary `#FFFFFF`, Glow `#80FF007F`
    - Speaking: Primary `#00FF66`, Secondary `#00F0FF`, Glow `#8000FF66`
  - *Motion Curve*: Spring physics with damped oscillations (`sin(pulseScale * 5 + i) * 6f`) and smooth linear interpolation (`ArgbEvaluator`) for color transitions.
  - *Touch Feedback*: Subtle haptic ticks and M3 ripple states on expanded pill controls.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Surface Native Canvas Rendering**
  - *Chosen Approach*: Implement a shared, reusable Grok Bot drawing engine (`drawGrokBotAvatar`) that draws directly on Android `Canvas` within `DynamicOverlayAvatarView` and a dedicated `GrokBotCanvasView` widget in `GrokDynamicIslandOverlay`.
  - *Why*: Guarantees consistent 60+ FPS performance, minimal memory footprint, and exact coordinate scaling across both floating WindowManager overlays and in-app Composables/Views.
  - *Alternatives Considered*: Pure Jetpack Compose in WindowManager overlays was considered, but embedding Compose in system overlays requires ComposeView hosting overhead and lifecycle bridges; native Canvas delivers superior lightweight responsiveness in system overlay windows.

- **Decision 2: Low-Latency SoundPool Engine**
  - *Chosen Approach*: Build `GrokBotSoundManager` using `SoundPool` with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` and preloaded streams for `sound_idle`, `sound_working`, and `sound_speaking`.
  - *Why*: Unlike `MediaPlayer`, `SoundPool` keeps decoded audio in memory, eliminating playback start delay and CPU spikes during frequent state changes.
  - *Alternatives Considered*: Standard `MediaPlayer` was evaluated, but it suffers 50–150ms buffer initialization latency on state changes.

- **Decision 3: Non-Destructive Style Extension**
  - *Chosen Approach*: Retain existing avatar styles (`MetaMuse`, `Droid`, `Classic`, `Neon`, `Cosmic`, `Minimal`, `Pixel`) in `AvatarSettingsDataStore` and add `"Grok Bot"` as a first-class choice with full persistence.
  - *Why*: Ensures users who prefer MetaMuse or Droid keep their favorite aesthetics, while Grok Bot fans get the exact requested behavior.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SessionStateManager                             │
│                  (VoiceSessionState / AvatarState)                     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
┌─────────────────────────────────┐         ┌─────────────────────────────────┐
│     GrokDynamicIslandOverlay    │         │    DynamicOverlayAvatarView     │
│   (WindowManager Top Anchor)    │         │    (WindowManager Float / View) │
│                                 │         │                                 │
│  ┌───────────────────────────┐  │         │  ┌───────────────────────────┐  │
│  │     GrokBotCanvasView     │  │         │  │   drawGrokBotAvatar()     │  │
│  │   - Radial Neon Glow      │  │         │  │   - Spring Puffing Scale  │  │
│  │   - Center Head Sway      │  │         │  │   - Orbiting 6 Dots       │  │
│  │   - 6 Orbit Wave Dots     │  │         │  │   - Smooth Argb Palette   │  │
│  └───────────────────────────┘  │         │  └───────────────────────────┘  │
└────────────────┬────────────────┘         └────────────────┬────────────────┘
                 │                                           │
                 └─────────────────────┬─────────────────────┘
                                       │
                                       ▼
                    ┌─────────────────────────────────────┐
                    │        GrokBotSoundManager          │
                    │   - SoundPool (Low Latency)         │
                    │   - sound_idle / working / speaking │
                    │   - Volume & Session Interlock      │
                    └─────────────────────────────────────┘
```

#### Core Components & Responsibilities

1. **`com.jarvis.assistant.ui.grok.GrokBotDrawingEngine`**:
   - Reusable renderer calculating center glow, eye sway offset, continuous pulse scaling, and the 6 peripheral blowing particles based on time and audio amplitude.
2. **`com.jarvis.assistant.ui.grok.GrokBotSoundManager`**:
   - SoundPool instance with priority streaming, respecting `isAudioFeedbackEnabled` and conversation speech state.
3. **`DynamicOverlayAvatarView.kt`**:
   - Add `"grok bot"` / `"grok"` branch to `drawAvatarStyle()` using the drawing engine.
4. **`GrokDynamicIslandOverlay.kt`**:
   - Embed the animated Grok Bot visualizer into the island capsule header for compact and expanded states.
5. **`OverlayAvatarActivity.kt` & `activity_overlay_avatar.xml`**:
   - Register "Grok Bot" in avatar style options, bind live preview animations, and persist selection in `AvatarSettingsDataStore`.
