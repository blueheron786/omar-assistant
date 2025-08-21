# Omar Assistant - Privacy-First Voice Assistant

Omar is a fully-functional Android voice assistant built with Kotlin that prioritizes privacy by processing everything locally on-device. No data is sent to the cloud, ensuring maximum privacy while providing powerful voice control capabilities.

## Features

### 🎤 Voice Processing (100% Local)
- **Wake Word Detection**: Responds to "Omar" using local pattern matching
- **Voice Activity Detection (VAD)**: Efficiently detects when you're speaking
- **Speech-to-Text**: Uses Android's built-in offline speech recognition
- **Text-to-Speech**: Natural voice responses using Android TTS

### 🏠 Smart Home Integration
- Control smart lights via REST API
- Extensible architecture for adding IoT devices
- Local command processing for instant response

### 🧠 Local AI Processing
- Pattern-based Natural Language Understanding (NLU)
- Intent recognition and entity extraction
- No cloud dependencies for core functionality

### 🔧 Modular Toolbox System
- Easily extensible command system
- Pre-built tools for common tasks
- Simple API for adding custom commands

### 🔒 Privacy-First Design
- All processing happens on-device
- No data logging or cloud transmission
- Secure storage of any configuration data

## Built-in Commands

### Smart Home
- "Turn on the lights"
- "Turn off the lights"
- "Turn on WiFi" (opens settings)
- "Open camera"

### Information
- "What time is it?"
- "What's the weather?" (simulated data)

### Social
- "Hello" / "Hi"
- "Goodbye" / "Bye"

### System
- "Help" - Shows available commands
- "Open settings"

## Installation

1. **Clone or download this project**
   ```
   git clone https://github.com/yourusername/omar-assistant.git
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the `OmarAssistant` folder

3. **Build and run**
   - Connect your Android device or start an emulator
   - Click "Run" in Android Studio

## Requirements

- **Android API Level 24+ (Android 7.0)**
- **Microphone permission** - Required for wake word detection
- **Internet permission** - Optional, only for IoT device control
- **Foreground service permission** - For continuous listening

## Configuration

### Smart Light Control
To control real smart lights, update the configuration in `LightControlTool.kt`:

```kotlin
private val lightApiBaseUrl = "http://your-smart-hub-ip:port/api"
private val lightApiKey = "your_api_key_here"
```

### Wake Word Customization
Modify wake words in `WakeWordDetector.kt`:

```kotlin
private val wakeWords = listOf("omar", "عمر", "3umar", "your-custom-word")
```

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   MainActivity  │    │  OmarAssistant   │    │  ToolboxManager │
│   (UI Layer)    │◄──►│  (Orchestrator)  │◄──►│  (Command Exec) │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
        ┌──────────────┐ ┌─────────────┐ ┌─────────────┐
        │ AudioProcessor│ │ SpeechToText│ │TextToSpeech │
        │ (Recording)   │ │ (Local ASR) │ │ (Android)   │
        └──────────────┘ └─────────────┘ └─────────────┘
                │
        ┌───────┼───────┐
        ▼       ▼       ▼
   ┌────────┐ ┌───┐ ┌─────────┐
   │WakeWord│ │VAD│ │LocalNLU │
   │Detector│ │   │ │Processor│
   └────────┘ └───┘ └─────────┘
```

## Core Components

### Audio Processing (`audio/`)
- **AudioProcessor**: Manages microphone input and audio buffering
- **WakeWordDetector**: Local wake word detection using spectral analysis
- **VoiceActivityDetector**: Determines when user is speaking

### Speech Processing (`speech/`)
- **SpeechToTextProcessor**: Converts speech to text using Android's offline ASR
- **TextToSpeechProcessor**: Speaks responses with customizable voice parameters

### Natural Language Understanding (`nlp/`)
- **LocalNLUProcessor**: Understands user intents using pattern matching
- **Intent**: Data structure for parsed commands

### Toolbox System (`toolbox/`)
- **ToolboxManager**: Manages and executes commands
- **Tool Interface**: Base interface for all command tools
- **Built-in Tools**: Light control, time info, greetings, help, system control

### Service (`service/`)
- **VoiceAssistantService**: Background service for continuous listening

## Adding Custom Commands

Create a new tool by implementing the `Tool` interface:

```kotlin
class MyCustomTool(private val context: Context) : Tool {
    override val name = "my_tool"
    override val description = "My custom functionality"
    override val supportedActions = listOf("custom_action")
    override val supportedEntities = listOf("custom_entity")
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        // Your custom logic here
        return CommandResult(
            success = true,
            response = "Custom command executed!"
        )
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action in supportedActions && entity in supportedEntities
    }
}
```

Register your tool in `ToolboxManager`:

```kotlin
fun registerCustomTools() {
    registerTool(MyCustomTool(context))
}
```

## Privacy Features

- ✅ **No cloud processing** - Everything runs locally
- ✅ **No data logging** - Voice data is not stored
- ✅ **Secure configuration** - API keys stored securely
- ✅ **Minimal permissions** - Only essential permissions required
- ✅ **No analytics** - No usage tracking or telemetry

## Battery Optimization

- Efficient wake word detection using energy-based pre-filtering
- Voice Activity Detection to minimize processing overhead
- Optimized audio buffering with circular buffer design
- Background service runs only when actively listening

## Troubleshooting

### Wake Word Not Detected
1. Check microphone permission is granted
2. Verify background service is running
3. Try speaking louder or closer to device
4. Check audio buffer statistics in logs

### Speech Recognition Fails
1. Ensure device has offline speech recognition
2. Check internet connection if offline ASR unavailable
3. Verify microphone is working properly

### Commands Not Working
1. Say "help" to see available commands
2. Check logs for intent recognition results
3. Verify tool registration in ToolboxManager

## Development

### Building
```bash
./gradlew assembleDebug
```

### Testing
```bash
./gradlew test
```

### Debugging
Enable verbose logging by setting log level to DEBUG in each component.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add your enhancement
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Credits

- Built with Kotlin and Android SDK
- Uses Android's built-in Speech Recognition and TTS
- Inspired by JARVIS and other voice assistants
- Created with privacy-first principles

---

**Omar Assistant** - Your privacy-focused voice companion! 🎤🔒
