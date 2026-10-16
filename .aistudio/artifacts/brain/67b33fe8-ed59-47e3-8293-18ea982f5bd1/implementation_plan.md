# Revised Implementation Plan: Exact Screenshot Alignment (MetaMuse UI & Dynamic Island)

This revision directly details the exact visual elements, button styling, layout structures, motion physics, and color rules presented in your uploaded screenshot (`MetaMuse Style Avatar – All States & Animations`).

---

## 1. Exact Visual Elements from the Screenshot

### A. Dynamic Island: Compact View vs. Expanded View
As illustrated on the bottom right and top left of the screenshot:
1. **Compact View (`~170dp x 38dp`)**:
   - **Left**: Circular avatar frame containing the cute 3D MetaMuse character face, bordered by a soft glowing neon halo matching the current state color.
   - **Center**: Glowing state indicator icon + status text (`"Listening..."`, `"Idle"`, `"Thinking..."`, `"Speaking"`).
   - **Right**: Multi-bar audio waveform (6–8 gradient bars that react dynamically to microphone and voice output levels).
   - **Background**: OLED jet-black rounded capsule (`#070B14`, 19dp corner radius) with an interactive tap response.

2. **Expanded View (`~280dp x 92dp`)**:
   - **Top Row**:
     - MetaMuse character badge inside glowing circle.
     - State icon + text (`"Listening..."`).
     - Real-time reactive audio waveform.
   - **Bottom Row (Action Buttons - Exact Button Styling from Screenshot)**:
     - **Button 1 (Pause)**: Circular dark surface button (`#141B2D`) with centered white vector pause icon (`||`), with text label `"Pause"` (or `"Resume"`) directly underneath.
     - **Button 2 (End)**: Circular crimson red button (`#E11D48` / `#FF1744`) with phone hang up icon, with text label `"End"` directly underneath.
     - **Button 3 (More)**: Circular dark surface button (`#141B2D`) with three horizontal dots icon (`···`) or expand icon, with text label `"More"` directly underneath (opens full app).
   - **Footer Subtitle**: `"Controls work with real session state (Pause / Resume / End / More)"`.

---

### B. MetaMuse Character Face & All 12 States
The avatar renders the exact 3D character shown in the 12 center cards:
- **Head & Ears**: Soft rounded cyber-cat contours, white/light ceramic head with glowing inner ear accent pads.
- **Eyes & Visor**: Dark glass visor screen with glowing expressive pill/oval LED eyes that blink and shift gaze.
- **Mouth**: Expressive smiling mouth line in Idle/Listening, opening in Speaking, flat in Thinking, and sad curve in Failed.
- **State Animations & Motions (from "Animation Highlights")**:
  - **Particle Motion**: Orbiting particles and sparks circling the head during `Thinking` and `Executing`.
  - **Waveform Motion**: Rhythmic audio wave expansion during `Listening` and `Speaking`.
  - **Glow Effect**: Radial neon halo breathing behind the character across all states.
  - **Orbit Rotation**: Smooth 360-degree orbital ring rotation during `Reconnecting`.
  - **Shake Motion**: Alert pulse and physical shake during `Failed`.
  - **Status Badges**: Mini overlay badges (green checkmark for `Success`, orange `||` for `Paused`, red `!` for `Failed`).

---

### C. Strict Color Palette (Exact HEX Values from Screenshot)
| State | Primary HEX | Secondary HEX | Visual Description |
|---|---|---|---|
| **Idle** | `#287BFF` | `#00F0FF` | Electric Blue & Cyan calm glow |
| **Listening** | `#00F0FF` | `#287BFF` | Neon Cyan with Blue wave |
| **Thinking** | `#9D00FF` | `#FF36D1` | Deep Purple & Magenta particles |
| **Speaking** | `#FF36D1` | `#00F0FF` | Hot Pink with Cyan speech wave |
| **Executing** | `#287BFF` | `#00F0FF` | Blue with rapid orbital energy |
| **Verifying** | `#00F0FF` | `#9D00FF` | Cyan & Purple verification pulse |
| **Success** | `#00E676` | `#00F0FF` | Vibrant Emerald Green glow with checkmark |
| **Retrying** | `#FF6D00` | `#FFB020` | Fiery Amber/Orange pulse |
| **Paused** | `#FFB020` | `#FFD166` | Warm Golden Amber slow glow |
| **Reconnecting** | `#287BFF` | `#9D00FF` | Blue & Purple orbital sweep |
| **Failed** | `#FF1744` | `#FF6D00` | Crimson Red alert with shake |
| **Ended** | `#FF1744` | `#FF36D1` | Soft Crimson fade out |
| **Disconn.** | `#AAB4C3` | `#AAB4C3` | Muted slate gray |

*Rule: Smooth `ArgbEvaluator` transitions between all states. No unauthorized hex values.*

---

### D. Settings Panel & Size Adjustment Slider
Matching the right panel in the screenshot:
- **Avatar Style**: Dropdown showing `"MetaMuse"` as default option.
- **Animation Speed**: Options (`Slow`, `Normal`, `Fast`).
- **Color Theme**: Palette presets (`Blue`, `Purple`, `Green`, `Pink`, `Orange`).
- **Feature Toggles**:
  - Particle Animation: `ON`
  - Audio Waveform: `ON`
  - Audio Feedback: `ON`
  - Haptic Feedback: `OFF`
  - Show Status Text: `ON`
  - Reduce Motion: `OFF`
- **Size Adjustment Slider Card**:
  - Left icon: Circular badge with MetaMuse character head.
  - Middle: Continuous smooth slider (50% to 150%).
  - Right: Live percentage indicator (e.g. `"70%"`).
  - Subtitle: `"Drag the slider to adjust the avatar size (Works in all states)"`.
  - Live scale update applies immediately to both floating overlay and preview!

---

## 2. Execution Steps

1. **`FloatingVoiceController.kt` Refactor**:
   - Implement the exact dual-tier Dynamic Island:
     - Compact view: MetaMuse face badge + state text + waveform.
     - Expanded view: Top row info + Bottom row with Pause (`||`), End (Red phone), and More (`···`) buttons with labels below.
   - Anchor strictly at top center below the camera punch hole.

2. **`DynamicOverlayAvatarView.kt` MetaMuse Character Engine**:
   - Build custom canvas renderer for the MetaMuse 3D cat-eared cyber character (ears, visor, glowing eyes, mouth, particle motion, orbit rotation, and glow halo).
   - Add size scaling factor tied to user's size adjustment slider.

3. **Settings Screen Updates (`activity_overlay_avatar.xml` & `OverlayAvatarActivity.kt`)**:
   - Add Size Adjustment slider card with live percentage text (`70%`).
   - Wire live slider events to `DynamicOverlayAvatarView` and save to `AvatarSettingsDataStore`.

4. **Audio & Sound Verification**:
   - Ensure `sound_idle.mp3`, `sound_working.mp3`, `sound_speaking.mp3`, `sound_paused.mp3`, `sound_success.mp3`, `sound_error.mp3`, `sound_reconnecting.mp3` play only when Audio Feedback is ON and never interrupt Gemini Live voice conversations.

---

## 3. Verification Plan
- **Build Verification**: Run `compile_applet` to confirm error-free compilation.
- **Unit Test Verification**: Run `gradle :app:testDebugUnitTest`.
- **Visual & Functional Verification**:
  - Confirm Dynamic Island compact layout and expanded buttons match the screenshot.
  - Confirm size slider adjusts avatar size from 50% to 150% and persists across app restarts.
  - Confirm color transitions strictly use the approved 12-state palette.
