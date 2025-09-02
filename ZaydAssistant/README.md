# Zayd Assistant - JARVIS-Style Voice Assistant

A comprehensive Android voice assistant built in Kotlin that implements JARVIS-style functionality with wake word detection, speech processing, and AI-powered responses.

## Features

### 🎙️ **Wake Word Detection**
- Listens for configurable wake word: "zayd"
- Uses Porcupine for efficient, battery-optimized wake word detection
- Fallback to energy-based detection when Porcupine is unavailable

### 🗣️ **Voice Activity Detection (VAD)**
- Energy-based voice activity detection
- Automatically detects when user starts and stops speaking
- Configurable sensitivity settings
- Minimizes unnecessary processing and API calls

### 🧠 **AI Integration**
- **Gemini API integration** for natural language understanding
- Extensible LLM provider architecture (easy to add OpenAI, Claude, etc.)
- Context-aware conversations with memory
- Intent recognition and command interpretation

### 🔧 **Device Control**
- **Flashlight control** - Turn device flashlight on/off with voice commands
- **Phone integration** - Make calls by number or contact name, check phone status
- **Contact lookup** - Find contacts by name and call them directly
- **KASA HS200 smart light control** implementation
- Extensible toolbox system for adding new devices/functions
- REST API integration for IoT devices
- Plugin-style architecture for custom commands

### 🎯 **Real-Time Processing**
- Asynchronous processing with Kotlin coroutines
- Low-latency speech recognition and response
- Background service for continuous listening
- Foreground service with notification controls

### 🔒 **Security & Privacy**
- Secure storage for API keys using Android EncryptedSharedPreferences
- No logging of sensitive voice data
- Graceful network error handling
- Configurable privacy settings

## Architecture

### Modular Design
```
📦 Zayd Assistant
├── 🎧 Audio Module
│   ├── AudioManager (recording & streaming)
│   ├── WakeWordDetector (Porcupine integration)
│   └── VoiceActivityDetector (energy-based VAD)
├── 🗣️ Speech Module
│   ├── SpeechToTextManager (Android ASR)
│   └── TextToSpeechManager (Android TTS)
├── 🧠 LLM Module
│   ├── LLMProvider (abstraction layer)
│   └── GeminiProvider (Google Gemini integration)
├── 🔧 Toolbox Module
│   ├── ToolboxManager (plugin registry)
│   └── Tools (SmartLightTool, etc.)
├── 🎯 Orchestrator
│   └── AssistantOrchestrator (main coordinator)
└── 🖥️ UI Module
    ├── MainActivity (main interface)
    └── SettingsActivity (configuration)
```

## Setup Instructions

### Prerequisites
1. **Android Studio** (latest version)
2. **Android device** with API level 21+ (Android 5.0+)
3. **Gemini API Key** from [Google AI Studio](https://makersuite.google.com/app/apikey)
4. **Microphone permissions** on target device

### Installation

1. **Clone and Build**
   ```bash
   git clone <repository-url>
   cd ZaydAssistant
   ./gradlew build
   ```

2. **Install on Device**
   ```bash
   ./gradlew installDebug
   ```

3. **Configure API Key**
   - Open the app
   - Go to Settings (menu button)
   - Enter your Gemini API key
   - Adjust sensitivity settings as needed
   - Save settings

4. **Grant Permissions**
   - Allow microphone access
   - Allow notification access (for background service)

### First Run
1. **Test TTS**: Use "Test Text-to-Speech" button to verify audio output
2. **Start Assistant**: Tap "Start Assistant" to begin wake word detection
3. **Test Wake Word**: Say "Zayd" followed by a command
4. **Manual Testing**: Use the manual input field for testing without wake words

## Usage Examples

### Basic Commands
- **"Zayd, what time is it?"** - Get current time
- **"Zayd, tell me a joke"** - Request entertainment
- **"Zayd, turn on the light"** - Control smart home devices

### Flashlight Control
- **"Turn on the flashlight"** - Turn on device flashlight/torch
- **"Turn off the flashlight"** - Turn off device flashlight
- **"Toggle the flashlight"** - Switch flashlight state

### Phone Functions
- **"Call 555-1234"** - Make a phone call to the number
- **"Call Alia"** - Look up "Alia" in contacts and call her
- **"Dial mom"** - Open dialer with mom's number from contacts
- **"Dial 555-1234"** - Open dialer with specific number
- **"Phone status"** - Get current phone/network status

### Smart Home Control
- **"Turn on the lights"** - Controls KASA HS200 smart switch
- **"Turn off the light"** - Switches off connected devices
- **"Toggle the lights"** - Switches state of smart devices

### Conversation
- Maintains context across multiple interactions
- Supports follow-up questions
- Remembers previous commands in the session

## Configuration

### API Settings
- **LLM Provider**: Currently supports Gemini (extensible)
- **API Key**: Securely stored and encrypted
- **Model Parameters**: Temperature, tokens, safety settings

### Audio Settings
- **Wake Word Sensitivity**: Adjustable threshold (0-100%)
- **VAD Sensitivity**: Voice activity detection threshold (0-100%)
- **Audio Format**: 16kHz, 16-bit, mono PCM

### Smart Home Settings
- **Device IPs**: Configure smart device network addresses
- **API Endpoints**: Customize REST API URLs for different devices

## Extending the Assistant

### Adding New Tools
```kotlin
class MyCustomTool : Tool {
    override val name = "my_tool"
    override val description = "Does something useful"
    override val parameters = mapOf("param1" to "Description")
    
    override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
        // Your implementation here
        return ToolExecutionResult(success = true, message = "Done!")
    }
    
    override fun validateParameters(parameters: Map<String, Any>): Boolean {
        return parameters.containsKey("param1")
    }
}

// Register in ServiceLocator
toolboxManager.registerTool(MyCustomTool())
```

### Adding New LLM Providers
```kotlin
class MyLLMProvider(private val apiKey: String) : LLMProvider {
    override suspend fun processInput(
        userInput: String,
        availableTools: List<Tool>,
        conversationHistory: List<ConversationMessage>
    ): LLMResponse {
        // Your LLM integration here
    }
    
    override fun isConfigured(): Boolean = apiKey.isNotBlank()
    override fun getProviderName(): String = "MyProvider"
}
```

## Technical Specifications

### Minimum Requirements
- **Android API 21+** (Android 5.0 Lollipop)
- **1GB RAM** minimum
- **50MB storage** for app
- **Microphone** access required
- **Internet connection** for AI processing

### Recommended Specifications
- **Android API 30+** (Android 11)
- **4GB+ RAM** for optimal performance
- **High-quality microphone** for better wake word detection
- **Stable WiFi** for fast AI responses

### Performance Characteristics
- **Wake word detection**: ~50ms latency
- **Speech recognition**: 1-3 seconds
- **AI processing**: 2-5 seconds (depends on network)
- **Response generation**: 1-2 seconds
- **Battery usage**: Optimized for continuous listening

## Troubleshooting

### Common Issues

1. **Wake Word Not Detected**
   - Check microphone permissions
   - Adjust wake word sensitivity in settings
   - Try speaking closer to device
   - Test with manual input first

2. **API Errors**
   - Verify Gemini API key is correct
   - Check internet connection
   - Ensure API quota is not exceeded

3. **Audio Issues**
   - Test TTS functionality
   - Check device audio settings
   - Verify app has audio permissions

4. **Performance Issues**
   - Close other audio apps
   - Restart the assistant service
   - Check available RAM

### Debug Mode
Enable developer options in Android settings to access:
- Audio debugging logs
- Network request monitoring
- Performance metrics

## Security Considerations

### Data Protection
- **API keys encrypted** using Android Keystore
- **No voice data stored** locally
- **Secure network transmission** (HTTPS)
- **No analytics or tracking**

### Permissions
- **Microphone**: Required for voice input
- **Network**: Required for AI processing
- **Foreground Service**: For continuous listening
- **Notifications**: For service status

### Privacy
- Voice data is processed in real-time and not stored
- API calls are made directly to providers
- No third-party analytics or data collection

## Contributing

### Development Setup
1. Fork the repository
2. Create feature branch
3. Follow Kotlin coding standards
4. Add tests for new functionality
5. Update documentation
6. Submit pull request

### Code Style
- **SOLID principles** - maintainable, extensible code
- **KISS principle** - keep implementations simple
- **YAGNI principle** - avoid premature optimization
- **Kotlin conventions** - follow official style guide

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- **Porcupine** by Picovoice for wake word detection
- **Google Gemini** for AI processing
- **Android Jetpack** for modern Android development
- **Material Design** for UI components

## Support

For issues, questions, or contributions:
- Create an issue on GitHub
- Check the troubleshooting section
- Review the documentation

---

**Built with ❤️ using Kotlin and modern Android development practices**
