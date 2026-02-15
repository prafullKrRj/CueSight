# CueSight App Architecture - Complete Revamp

## Overview
This document describes the completely revamped CueSight app architecture that replaces TCP/WebSocket communication with HTTP-based communication and introduces a modern, teacher-friendly UI.

## Key Changes

### 1. Communication Protocol Change
- ❌ **Removed:** TCP Socket communication (`TcpFrameService`)
- ❌ **Removed:** WebSocket communication
- ✅ **Added:** HTTP-based MJPEG streaming
- ✅ **Added:** HTTP command sending

### 2. Architecture Overview

```
ESP32_CAM (192.168.4.1)
├── Port 80: MJPEG Stream endpoint (/stream)
└── Port 81: Command endpoint (/cmd?v=X)

Android App
├── Core Layer
│   ├── network/
│   │   ├── HttpMjpegStreamService.kt    # Parse MJPEG from http://192.168.4.1/stream
│   │   ├── HttpCommandSender.kt         # Send commands to http://192.168.4.1:81/cmd?v=X
│   │   └── ConnectionManager.kt         # Hybrid WiFi connection (Android 10+/9-)
│   └── util/
│       └── EmotionMapper.kt             # Map emotions to command codes
│
└── Feature Modules (Separated)
    ├── splash/                          # Animated splash screen
    ├── connection/                      # WiFi connection flow
    ├── teaching/                        # HTTP streaming + emotion detection
    └── practice/                        # Teacher-controlled feedback
```

## Core Services

### HttpMjpegStreamService
**Location:** `core/network/HttpMjpegStreamService.kt`

Parses MJPEG stream from ESP32 camera.

```kotlin
// Usage
val streamService = HttpMjpegStreamService()
streamService.streamFrames("192.168.4.1")
    .collect { bitmap ->
        // Process frame
    }
```

**Features:**
- Efficient MJPEG parsing
- Automatic frame decoding
- Flow-based API
- Connection testing

### HttpCommandSender
**Location:** `core/network/HttpCommandSender.kt`

Sends HTTP commands to ESP32 with retry logic.

```kotlin
// Usage
val commandSender = HttpCommandSender()

// Send emotion
commandSender.sendEmotion("Happy")  // Sends v=1

// Send practice command
commandSender.sendPracticeCommand(PracticeCommandType.CORRECT)  // Sends v=11
```

**Emotion Codes:**
- 1 = Happy
- 2 = Sad
- 3 = Angry
- 4 = Surprise
- 5 = Neutral
- 6 = Disgust
- 7 = Fear

**Practice Codes:**
- 10 = Question mark (?)
- 11 = Correct (✓)
- 12 = Wrong (✗)

### ConnectionManager
**Location:** `core/network/ConnectionManager.kt`

Handles WiFi connection to ESP32_CAM with platform-specific implementations.

```kotlin
// Usage
val connectionManager = ConnectionManager(context)

// Connect (handles Android version differences automatically)
when (val result = connectionManager.connectToESP32()) {
    is ConnectionResult.Success -> { /* Connected */ }
    is ConnectionResult.NeedsUserAction -> { /* Open WiFi settings */ }
    is ConnectionResult.Error -> { /* Show error */ }
}
```

**Android 10+ (Q and above):**
- Uses `WifiNetworkSuggestion`
- Requires user approval through WiFi settings
- More privacy-focused

**Android 9- (Pie and below):**
- Uses `WifiConfiguration`
- Can connect automatically
- Legacy approach

## Feature Modules

### 1. Splash Screen
**Location:** `feature/splash/SplashScreen.kt`

- Beautiful animated gradient background (Purple → Blue)
- 2-second display
- Auto-navigates to Connection Screen

### 2. Connection Flow
**Location:** `feature/connection/`

Multi-step connection wizard:

1. **Check WiFi Status**
   - Verifies WiFi is enabled
   - Checks if already connected to ESP32_CAM

2. **Connect to ESP32_CAM**
   - Android 10+: Shows WiFi settings with network suggestion
   - Android 9-: Auto-connects programmatically

3. **Test HTTP Connection**
   - Tests connection to `http://192.168.4.1/frame`
   - Verifies ESP32 is responsive

4. **Success Animation**
   - Shows success checkmark
   - Auto-navigates to Students screen

### 3. Teaching Module (NEW)
**Location:** `feature/teaching/NewTeachingViewModel.kt`

**Complete rewrite** using HTTP streaming instead of TCP.

**Features:**
- HTTP MJPEG streaming from `http://192.168.4.1/stream`
- Emotion detection using MLKit face detection
- Send emotion commands ONLY when emotion changes
- In-memory session tracking (NO database logging)
- Real-time frame display

**Key Differences from Legacy:**
- ❌ No TCP socket connection
- ❌ No database emotion logging
- ✅ HTTP streaming
- ✅ In-memory stats only
- ✅ Cleaner, simpler code

```kotlin
// Usage
viewModel.startSession(studentId, studentName)
viewModel.startStreaming()
// Automatic emotion detection and command sending
viewModel.endSession()
```

### 4. Practice Module (NEW)
**Location:** `feature/practice/NewPracticeViewModel.kt`

**Complete redesign** - now teacher-controlled instead of camera-based.

**Features:**
- NO camera streaming
- Teacher selects: "Which emotion did you show?"
- Teacher selects: "What did student guess?"
- Compares answers automatically
- Sends feedback to ESP32 (✓ or ✗)
- Tracks session stats in memory
- NO database logging

**Flow:**
1. Start: Send "?" to OLED (`v=10`)
2. Teacher selects emotion they showed
3. Teacher enters what student guessed
4. Compare → Send feedback (`v=11` correct or `v=12` wrong)
5. Wait 3 seconds → Reset to "?" for next round

```kotlin
// Usage
viewModel.startSession(studentId, studentName)
viewModel.onTeacherEmotionSelected("Happy")
viewModel.onStudentGuessSelected("Sad")
// Automatic comparison and feedback
```

## Navigation Flow

```
Splash (2s)
  ↓
Connection (Multi-step)
  ├─ Check WiFi
  ├─ Connect to ESP32_CAM
  ├─ Test HTTP
  └─ Success
    ↓
Students List
  ↓
Student Detail
  ├─ Teaching Mode → NewTeachingViewModel (HTTP streaming)
  └─ Practice Mode → NewPracticeViewModel (Teacher feedback)
```

## Design System

### Colors
```kotlin
object CueSightColors {
    val Purple = Color(0xFF6C63FF)
    val Blue = Color(0xFF4A90E2)
    val Green = Color(0xFF4CAF50)
    val Orange = Color(0xFFFF9800)
    val Red = Color(0xFFFF5252)
    
    // Emotion colors
    val EmotionHappy = Color(0xFFFDD835)
    val EmotionSad = Color(0xFF42A5F5)
    val EmotionAngry = Color(0xFFFF6B6B)
    // ... etc
}
```

### Typography
- Display: 48sp, Bold (Splash screen)
- Headline: 32sp, SemiBold (Page titles)
- Title: 24sp, SemiBold (Section headers)
- Body: 16sp, Normal (Content)

## Dependencies

### New Dependencies Added
```gradle
// OkHttp for HTTP streaming
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Lottie animations
implementation("com.airbnb.android:lottie-compose:6.1.0")
```

### Existing Dependencies (Kept)
- MLKit Face Detection (for emotion detection)
- Room Database (for student data only)
- Koin (Dependency Injection)
- Jetpack Compose (UI)

## Migration Strategy

### Phase 1: Parallel Implementation ✅ DONE
- Created new HTTP services alongside legacy TCP
- Created new feature modules (splash, connection)
- Created new ViewModels (NewTeachingViewModel, NewPracticeViewModel)
- Both old and new code coexist

### Phase 2: Testing & Validation (NEXT)
- Test WiFi connection flow on different Android versions
- Test HTTP streaming with actual ESP32
- Test emotion detection accuracy
- Test command sending reliability

### Phase 3: Full Migration (FUTURE)
- Replace legacy Teaching/Practice screens with new ones
- Remove TcpFrameService completely
- Remove database logging from sessions
- Remove Developer/Test modes
- Update all references

### Phase 4: Cleanup (FUTURE)
- Remove all TCP/WebSocket code
- Remove unused ViewModels
- Remove database tables for emotion logs
- Final optimization

## Testing

### Unit Tests
- `HttpCommandSenderTest`: Test command sending with mocked HTTP
- `ConnectionManagerTest`: Test WiFi connection logic
- `EmotionMapperTest`: Test emotion code mapping

### Integration Tests
- Test complete connection flow
- Test HTTP streaming with real data
- Test emotion detection pipeline

### Manual Testing Checklist
- [ ] Splash screen displays for 2 seconds
- [ ] Connection flow works on Android 10+
- [ ] Connection flow works on Android 9-
- [ ] HTTP streaming displays frames
- [ ] Emotion commands sent when emotion changes
- [ ] Practice mode feedback works
- [ ] Session stats calculated correctly

## Known Limitations

1. **Emotion Detection**: Currently placeholder - needs MLKit implementation
2. **Network Binding**: May not work if mobile data is active (needs network binding)
3. **Frame Quality**: Depends on WiFi signal strength
4. **Battery Usage**: Continuous streaming may drain battery quickly

## Future Enhancements

1. **Add MLKit Integration**: Implement actual emotion detection
2. **Analytics Module**: Create comprehensive analytics dashboard
3. **Settings Screen**: Add connection settings, preferences
4. **Bottom Navigation**: Add navigation bar for main screens
5. **Lottie Animations**: Add loading and success animations
6. **Error Recovery**: Improve error handling and auto-recovery
7. **Offline Mode**: Cache data for offline viewing

## Troubleshooting

### Connection Issues
- Ensure ESP32_CAM is powered on
- Verify WiFi SSID is "ESP32_CAM"
- Check ESP32_CAM is on 192.168.4.1
- Disable mobile data during connection

### Streaming Issues
- Check network signal strength
- Verify ESP32 stream endpoint is working
- Try restarting ESP32_CAM
- Check firewall settings

### Command Sending Issues
- Verify ESP32 command port (81) is accessible
- Check for network firewall blocking
- Ensure ESP32 firmware is up to date

## References

- ESP32 Camera Streaming: [ESP32-CAM MJPEG Streaming](https://github.com/espressif/esp32-camera)
- Android WiFi APIs: [Android WiFi Documentation](https://developer.android.com/reference/android/net/wifi/WifiManager)
- Jetpack Compose: [Compose Documentation](https://developer.android.com/jetpack/compose)
- MLKit Face Detection: [MLKit Documentation](https://developers.google.com/ml-kit/vision/face-detection)

## Contributing

When adding new features:
1. Follow the existing architecture patterns
2. Use HTTP-based communication (not TCP)
3. Implement in-memory state management (avoid database)
4. Write clear, teacher-friendly UI text
5. Add proper error handling
6. Document your changes

## License

[Your License Here]
