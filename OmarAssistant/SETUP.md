# Quick Setup Guide - Omar Assistant

## 🚀 Get Started in 5 Minutes

### Step 1: Prerequisites
- **Android Studio** (Arctic Fox or newer)
- **Android device** with API 21+ (Android 5.0+)
- **Gemini API key** (free from Google AI Studio)

### Step 2: Get Your API Key
1. Go to [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Click "Create API key"
3. Copy the generated key (you'll need this later)

### Step 3: Build & Install
```bash
# Clone the project
git clone <your-repo-url>
cd OmarAssistant

# Build and install
./gradlew assembleDebug
./gradlew installDebug
```

### Step 4: Configure the App
1. **Open the app** on your device
2. **Grant permissions** when prompted (microphone, notifications)
3. **Go to Settings** (menu → Settings)
4. **Enter your Gemini API key**
5. **Save settings**

### Step 5: Test the Assistant
1. **Test TTS**: Tap "Test Text-to-Speech" button
2. **Start Assistant**: Tap "Start Assistant"
3. **Say wake word**: "Omer" or "3umar"
4. **Give a command**: "What time is it?" or "Turn on the light"

## 🎯 First Commands to Try

### Basic Interaction
- **"Omer, hello"** - Simple greeting
- **"3umar, what time is it?"** - Get current time
- **"Omer, tell me a joke"** - Request entertainment

### Smart Home (KASA HS200)
- **"Turn on the light"** - Control smart switch
- **"Turn off the lights"** - Switch off devices
- **"Toggle the light"** - Change device state

### Testing Without Wake Words
Use the **Manual Input** field in the app to test commands without speaking.

## ⚙️ Troubleshooting

### Wake Word Issues
- **Not detecting**: Increase wake word sensitivity in settings
- **False triggers**: Decrease sensitivity, speak closer to device
- **No response**: Check microphone permissions

### API Issues
- **"Not configured"**: Enter valid Gemini API key in settings
- **Network errors**: Check internet connection
- **Quota errors**: Check your Google AI Studio usage

### Audio Issues
- **No speech**: Test TTS button, check device volume
- **Recognition fails**: Speak clearly, reduce background noise

## 🔧 Advanced Configuration

### Porcupine Wake Words (Optional)
For production use, replace the placeholder Porcupine access key:

1. **Get Porcupine Console access** at [picovoice.ai](https://console.picovoice.ai/)
2. **Train custom wake words** for "omer" and "3umar"
3. **Download keyword files** (.ppn files)
4. **Update WakeWordDetector.kt** with your access key and keyword files

### Smart Home Setup
1. **Find your KASA device IP**: Check your router's admin panel
2. **Update SmartLightTool.kt**: Replace `DEFAULT_KASA_IP` with your device's IP
3. **Test connection**: Use manual input "turn on the light"

### Custom Tools
Add your own commands by implementing the `Tool` interface:
```kotlin
class MyTool : Tool {
    override val name = "my_command"
    override val description = "Does something useful"
    // ... implement execute() method
}
```

## 📱 Device Recommendations

### Minimum Specs
- Android 5.0+ (API 21)
- 1GB RAM
- Decent microphone

### Optimal Experience
- Android 11+ (API 30)
- 4GB+ RAM
- High-quality microphone
- Stable WiFi connection

## 🆘 Get Help

### Documentation
- **Full README**: See README.md for complete documentation
- **Code comments**: Detailed explanations in source code
- **Architecture guide**: Check README.md architecture section

### Common Issues
1. **Permissions denied**: Go to Android Settings → Apps → Omar Assistant → Permissions
2. **Service won't start**: Restart app, check background app restrictions
3. **Poor recognition**: Use in quiet environment, speak clearly

### Development
- **Add logging**: Enable developer options for debug logs
- **Test components**: Use manual input for isolated testing
- **Performance**: Monitor battery usage in Android settings

---

**🎉 You're ready to go! Say "Omer" and start talking to your assistant!**
