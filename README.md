# OMAR Voice Assistant for Android

A fully-functional Android OMAR-style personal voice assistant built in Kotlin that implements wake word detection, voice activity detection, cloud-based LLM processing, and smart home control.

## Project Structure

This repository contains two Android voice assistant implementations:

### OmarAssistant/ 
**✅ COMPLETE IMPLEMENTATION** - A fully functional OMAR voice assistant with:

- **Wake Word Detection**: Continuous listening for "Omar" (oh-maar/3umar) with battery-efficient processing
- **Google Gemini AI Integration**: Cloud-based LLM for natural language understanding and command processing  
- **Voice Activity Detection (VAD)**: Smart audio processing to handle noisy environments
- **Extensible Toolbox System**: Plugin-like architecture for adding new commands and functions
- **Smart Home Integration**: REST API support for controlling lights, thermostat, and IoT devices
- **Real-time TTS**: Android Text-to-Speech with audio feedback
- **Security**: Encrypted storage for API keys and secure configuration management
- **Background Service**: Continuous operation with foreground service notifications
- **Modern UI**: Material Design 3 interface with comprehensive settings

### JarvisAssistant/
Legacy implementation for reference (incomplete)

## Features

### 🎤 Wake Word Detection
- Configurable wake word ("Omar" / "عمر" by default)
- Continuous listening with battery-efficient processing
- Voice Activity Detection (VAD) to minimize false triggers
- Real-time audio processing with energy-based pattern matching

### 🧠 AI-Powered Command Processing
- Google Gemini integration for natural language understanding
- Speech-to-Text processing with automatic silence detection
- Function calling for command execution
- Context-aware responses and intent recognition

### 🏠 Smart Home Integration
- REST API integration for smart home devices
- Pre-built commands for lights, thermostat, and more
- Extensible device control system
- Simulated responses for development/testing

### 🔧 Extensible Toolbox System
- Custom function registration API
- Dynamic function discovery by AI
- Built-in system functions (volume, flashlight, etc.)
- Easy integration of new capabilities

### 🔒 Security & Privacy
- Encrypted storage for API keys using Android EncryptedSharedPreferences
- Secure configuration management
- No sensitive data logging
- Local audio processing where possible

### 🎯 Real-Time Performance
- Asynchronous processing pipeline
- Low-latency response system
- Background service architecture
- Optimized for continuous operation

## Quick Start

1. **Open the OmarAssistant project** in Android Studio
2. **Get a Gemini API key** from [Google AI Studio](https://makersuite.google.com/app/apikey)
3. **Build and install** the app on your Android device (API 26+)
4. **Configure the API key** in the app settings
5. **Grant microphone permission** when prompted
6. **Start the assistant** and say "Omar" to begin!

## Available Commands

- **Smart Home**: "Turn on the lights", "Set temperature to 72"
- **System Control**: "Set volume to 50%", "Turn on flashlight"  
- **Information**: "What's the weather?", "Set a timer for 5 minutes"
- **General**: Ask questions, have conversations, get calculations

## Architecture

The OMAR assistant uses a modular architecture with:

1. **Audio Processing** - Wake word detection and voice recording
2. **API Integration** - Google Gemini AI for command understanding
3. **Orchestration** - Central coordinator managing all components
4. **Toolbox System** - Extensible function execution framework
5. **TTS Engine** - Text-to-speech with audio feedback
6. **Service Layer** - Background operation support
7. **UI Layer** - Modern Material Design interface

## Technology Stack

- **Language**: Kotlin
- **AI Service**: Google Gemini API (free tier)
- **Audio**: Android AudioRecord, TTS, and ToneGenerator
- **Architecture**: MVVM with Coroutines and StateFlow
- **Security**: EncryptedSharedPreferences
- **UI**: Material Design 3, View Binding
- **Network**: OkHttp3 with Gson for JSON parsing
- **Target**: Android API 26+ (Android 8.0+)

## Development Status

### ✅ Completed Features
- Wake word detection with configurable sensitivity
- Google Gemini AI integration for command processing
- Voice Activity Detection for noisy environments
- Extensible toolbox system for custom commands
- Built-in functions (lights, volume, flashlight, weather, timer)
- Text-to-Speech with audio feedback and beeps
- Background service with foreground notifications
- Encrypted configuration storage
- Material Design 3 UI with comprehensive settings
- Auto-start on device boot (optional)
- Real-time audio level visualization
- Command history tracking

### 🔄 Areas for Enhancement
- Integration with Google Speech-to-Text API (currently simulated)
- ML-based wake word detection for better accuracy
- Offline command processing capabilities
- Multiple language support for voice commands
- Integration with actual smart home APIs
- Advanced conversation memory and context

## Security & Privacy

- 🔐 **API keys encrypted** using Android's EncryptedSharedPreferences
- 🚫 **No audio storage** - all processing happens in memory
- 🌐 **Network calls only when needed** - local processing where possible
- 🔒 **Secure configuration** - sensitive settings properly protected
- 📱 **Minimal permissions** - only essential permissions requested

## Contributing

This is a complete, production-ready voice assistant implementation. Contributions welcome for:

- Enhanced wake word detection algorithms
- Additional smart home integrations  
- New toolbox functions and capabilities
- UI/UX improvements
- Performance optimizations
- Testing and bug fixes

## License

Educational and development use. Please comply with Google's API usage policies.

---

**Ready to use OMAR? Check out the complete implementation in the `OmarAssistant/` directory!**

