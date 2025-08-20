# OMAR Voice Assistant 🎤🤖

A fully-functional Android voice assistant built in Kotlin that implements wake word detection, voice activity detection, cloud-based LLM processing, and smart home control.

## Features ✨

### 🎤 Wake Word Detection
- Configurable wake word ("Omar" by default)
- Continuous listening with battery-efficient processing
- Voice Activity Detection (VAD) to minimize false triggers
- Real-time audio processing with energy-based pattern matching

### 🧠 AI-Powered Command Processing
- Google Gemini AI integration for natural language understanding
- Speech-to-Text processing
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

## Architecture 🏗️

### Core Modules

1. **Audio Processing** (`audio/`)
   - `AudioProcessor`: Wake word detection and VAD
   - `VoiceRecorder`: Command recording with automatic silence detection

2. **API Integration** (`api/`)
   - `GeminiApiService`: Google Gemini AI integration for text processing

3. **Orchestration** (`orchestrator/`)
   - `VoiceAssistantOrchestrator`: Main coordinator for all components
   - `ToolboxManager`: Function registration and execution system
   - `BuiltInFunctions`: Pre-built command implementations

4. **Text-to-Speech** (`tts/`)
   - `TextToSpeechEngine`: Android TTS integration with audio feedback

5. **Configuration** (`config/`)
   - `ConfigManager`: Encrypted settings storage and management

6. **Service Layer** (`service/`)
   - `VoiceAssistantService`: Background service for continuous operation

7. **UI Layer** (`ui/`)
   - `MainActivity`: Primary user interface
   - `SettingsActivity`: Configuration management interface

## Setup Instructions 🚀

### Prerequisites
- Android Studio Arctic Fox or later
- Android API 26+ (Android 8.0)
- Google Gemini API key

### 1. Clone and Open Project
```bash
git clone <repository-url>
cd OmarAssistant
```
Open the project in Android Studio.

### 2. Get Gemini API Key
1. Go to [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create a new API key
3. Copy the key (starts with "AIza...")

### 3. Configure the App
1. Build and install the app on your device
2. Open OMAR Assistant
3. Go to Settings
4. Enter your Gemini API key
5. Test the API connection
6. Adjust sensitivity settings as needed

### 4. Grant Permissions
The app requires:
- **Microphone**: For voice input and wake word detection
- **Notifications**: For background service status
- **Camera**: For flashlight control (optional)

### 5. Start Using OMAR
1. Tap "Start Assistant" on the main screen
2. Say "Omar" to activate the assistant
3. Give voice commands like:
   - "Turn on the lights"
   - "What's the weather?"
   - "Set volume to 50%"
   - "Turn on flashlight"

## Available Commands 📋

### Smart Home Control
- **Lights**: "Turn on/off the lights", "Dim the lights to 50%"
- **Thermostat**: "Set temperature to 72", "Increase temperature"

### System Control
- **Volume**: "Set volume to 50%", "Increase volume"
- **Flashlight**: "Turn on/off flashlight"

### Information
- **Weather**: "What's the weather?" (simulated)
- **Timer**: "Set a timer for 5 minutes"

### General Conversation
- Ask questions, have conversations
- Request calculations or information

## Customization 🛠️

### Adding New Functions
1. Create a new class extending `ToolboxFunction`
2. Implement the `execute()` method
3. Register it in `ToolboxManager`

Example:
```kotlin
class CustomFunction : ToolboxFunction() {
    override val name = "custom_command"
    override val description = "Custom functionality"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        // Your implementation here
        return ExecutionResult(success = true, message = "Command executed")
    }
}
```

### Modifying Wake Word Detection
- Adjust sensitivity in Settings
- Modify detection algorithm in `AudioProcessor.kt`
- For production use, consider integrating ML-based keyword spotting

### Extending Smart Home Integration
- Update API endpoints in settings
- Modify `LightControlFunction` for your specific smart home system
- Add new device control functions

## Configuration Options ⚙️

### Voice Settings
- **Wake Word**: Customize the activation phrase
- **Language**: Multiple language support
- **Continuous Listening**: Enable/disable always-on listening

### Sensitivity Settings
- **Wake Word Sensitivity**: Adjust detection threshold
- **VAD Sensitivity**: Control voice activity detection
- **Voice Volume**: TTS output volume

### API Configuration
- **Gemini API Key**: Required for AI processing
- **Smart Home URLs**: Optional smart home integration

## Development Notes 💻

### Testing
- Use manual text input for testing without voice
- Monitor logs for debugging audio processing
- Test API connectivity with the built-in test button

### Performance Optimization
- Audio processing runs on background threads
- Configurable sensitivity to balance accuracy vs battery life
- Automatic silence detection to minimize API calls

### Security Considerations
- API keys stored in encrypted preferences
- No audio data persisted to storage
- Network calls only when necessary

## Known Limitations ⚠️

1. **Speech-to-Text**: Currently uses simplified simulation - integrate with Google Speech-to-Text API for production
2. **Wake Word Detection**: Basic energy pattern matching - consider ML-based solutions for better accuracy
3. **Smart Home**: Simulated responses - integrate with actual smart home APIs
4. **Offline Capability**: Requires internet for AI processing

## Future Enhancements 🔮

- [ ] Google Speech-to-Text API integration
- [ ] ML-based wake word detection
- [ ] Offline command processing
- [ ] Multiple language support for commands
- [ ] Visual feedback and animations
- [ ] Command history and learning
- [ ] Integration with more smart home protocols

## Troubleshooting 🔧

### Common Issues

**"No API key configured"**
- Ensure you've entered a valid Gemini API key in Settings
- Test the API connection

**Wake word not detected**
- Adjust wake word sensitivity in Settings
- Ensure microphone permission is granted
- Try speaking closer to the device

**Commands not working**
- Check internet connection
- Verify API key is valid
- Monitor logs for error messages

**Background service stops**
- Check battery optimization settings
- Ensure notification permission is granted
- Verify continuous listening is enabled

## License 📄

This project is for educational and development purposes. Please ensure you comply with Google's API usage policies and any applicable terms of service.

## Contributing 🤝

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support 💬

For issues and questions:
1. Check the troubleshooting section
2. Review the logs for error messages
3. Create an issue with detailed information about your problem

---

**Built with ❤️ for the Android community**
