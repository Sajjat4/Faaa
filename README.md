# 🤖 JARVIS AI Voice Assistant & Automation System

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-brightgreen.svg)](https://gradle.org)

An advanced AI Voice Assistant featuring Gemini Live real-time bidirectional audio streaming, offline Vosk speech recognition, system automations, memory database, and an interactive floating voice controller.

---

## ⚡ Core Features

- **Live Voice Control Overlay**: Interactive floating dynamic pill with real-time audio reactive animations and Material 3 vector controls.
- **Gemini Live Bidi Audio Streaming**: Real-time bidirectional WebSocket streaming for low-latency voice conversation with barge-in support.
- **Offline Wake Word Engine**: Local Vosk acoustic model and speaker verification for instant offline hotword detection.
- **Memory Core Database**: Room SQLite database for persistent contextual memory, personal notes, contact preferences, and routines.
- **System Automation & Accessibility**: Hands-free WhatsApp messaging, YouTube playback control, device volume/brightness manipulation, web navigation, and phone operations.
- **Screen Grounding & Vision Orchestrator**: Multimodal screen reasoning and task orchestration for complex multi-step mobile automation.
- **Interactive Floating Companions**: Autonomous overlay companions and toys (Ghost, Alien, Mech, Hexagon, and more).

---

## 🚀 Getting Started

1. **API Key Setup**:
   - Obtain a Google Gemini API Key.
   - Enter your API Key in the **Settings** menu within the app or configure it via AI Studio Secrets.
2. **Build & Run**:
   - The application compiles cleanly with Gradle and Android SDK 34+.
   - Debug APK output is generated at `app/build/outputs/apk/debug/app-debug.apk`.
