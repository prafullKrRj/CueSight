# CueSight Migration Guide - TCP to HTTP

## Overview
This guide helps developers migrate from the old TCP-based architecture to the new HTTP-based architecture.

## Quick Reference

### Old vs New

| Feature | Old (TCP) | New (HTTP) |
|---------|-----------|------------|
| **Communication** | TCP Sockets | HTTP REST |
| **Streaming** | Custom TCP protocol | MJPEG over HTTP |
| **Commands** | Binary over TCP port 82 | HTTP GET with query params |
| **Connection** | Manual socket binding | WiFi manager with auto-retry |
| **Session Tracking** | Database logging | In-memory state |
| **Service Class** | `TcpFrameService` | `HttpMjpegStreamService` + `HttpCommandSender` |

## Step-by-Step Migration

### 1. Replace TcpFrameService

#### Old Code (❌ Don't use)
```kotlin
class MyViewModel(
    private val tcpService: TcpFrameService
) : ViewModel() {
    
    fun connect() {
        tcpService.setFrameCallback { frameBytes ->
            // Process frame
        }
        tcpService.connect("192.168.4.1")
    }
    
    fun sendEmotion(emotion: String) {
        tcpService.sendEmotionCode(emotion)
    }
}
```

#### New Code (✅ Use this)
```kotlin
class MyViewModel(
    private val streamService: HttpMjpegStreamService,
    private val commandSender: HttpCommandSender
) : ViewModel() {
    
    fun connect() {
        viewModelScope.launch {
            streamService.streamFrames("192.168.4.1")
                .collect { bitmap ->
                    // Process frame as Bitmap (already decoded)
                    onFrameReceived(bitmap)
                }
        }
    }
    
    suspend fun sendEmotion(emotion: String) {
        commandSender.sendEmotion(emotion)
    }
}
```

### 2. Update Dependency Injection

#### Old (❌)
```kotlin
val appModule = module {
    single { TcpFrameService() }
    viewModel { MyViewModel(get()) }
}
```

#### New (✅)
```kotlin
val appModule = module {
    // New HTTP services
    single { HttpMjpegStreamService() }
    single { HttpCommandSender() }
    single { ConnectionManager(androidContext()) }
    
    // Updated ViewModel
    viewModel { MyViewModel(get(), get()) }
}
```

### 3. Update Frame Processing

#### Old (❌)
```kotlin
// Received raw JPEG bytes
tcpService.setFrameCallback { jpegBytes ->
    val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    if (bitmap != null) {
        _frame.value = bitmap
    }
}
```

#### New (✅)
```kotlin
// Received already-decoded Bitmap
streamService.streamFrames()
    .collect { bitmap ->
        // No need to decode - already a Bitmap!
        _frame.value = bitmap
    }
```

### 4. Update Command Sending

#### Old (❌)
```kotlin
// Binary emotion codes
tcpService.sendEmotionCode(emotion, critical = true)

// String commands
tcpService.sendCommand("EMOTION:Happy")
```

#### New (✅)
```kotlin
// Emotion commands (automatically mapped to codes)
commandSender.sendEmotion("Happy")  // Sends v=1

// Practice commands
commandSender.sendPracticeCommand(PracticeCommandType.CORRECT)  // Sends v=11
```

### 5. Update WiFi Connection

#### Old (❌)
```kotlin
// Manual socket binding to network
val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        socketFactory = network.socketFactory
        tcpService.bindToNetwork(network)
    }
}
```

#### New (✅)
```kotlin
// Automatic platform-aware connection
val connectionManager = ConnectionManager(context)

when (val result = connectionManager.connectToESP32()) {
    is ConnectionResult.Success -> {
        // Connected automatically
        startStreaming()
    }
    is ConnectionResult.NeedsUserAction -> {
        // Show WiFi settings
        connectionManager.openWifiSettings()
    }
    is ConnectionResult.Error -> {
        // Show error
    }
}
```

### 6. Update Session Management

#### Old (❌)
```kotlin
// Database logging
viewModelScope.launch {
    val emotionLog = EmotionLog(
        sessionId = sessionId,
        emotion = emotion,
        timestamp = System.currentTimeMillis()
    )
    emotionLogRepository.insert(emotionLog)
}
```

#### New (✅)
```kotlin
// In-memory tracking
private val emotionHistory = mutableListOf<EmotionRecord>()

fun onEmotionDetected(emotion: String) {
    emotionHistory.add(EmotionRecord(
        emotion = emotion,
        timestamp = System.currentTimeMillis(),
        confidence = 0.85f
    ))
    
    _state.value = _state.value.copy(
        emotionCount = emotionHistory.size
    )
}
```

## Migration Checklist

### For Each ViewModel Using TCP

- [ ] Replace `TcpFrameService` with `HttpMjpegStreamService` + `HttpCommandSender`
- [ ] Update constructor parameters
- [ ] Replace `setFrameCallback` with Flow collection
- [ ] Update `sendEmotionCode` to `sendEmotion`
- [ ] Update `sendCommand` to appropriate HTTP method
- [ ] Replace database logging with in-memory state
- [ ] Update DI module
- [ ] Test thoroughly

### For Each Screen Using TCP

- [ ] Update ViewModel injection
- [ ] Update state observation
- [ ] Update UI to show Bitmap (not raw bytes)
- [ ] Update error handling
- [ ] Test on device

## Common Patterns

### Pattern 1: Streaming with Emotion Detection

```kotlin
class TeachingViewModel(
    private val streamService: HttpMjpegStreamService,
    private val commandSender: HttpCommandSender
) : ViewModel() {
    
    private var lastSentEmotion: String? = null
    
    fun startStreaming() {
        viewModelScope.launch {
            streamService.streamFrames()
                .collect { bitmap ->
                    // Detect emotion
                    val emotion = detectEmotion(bitmap)
                    
                    // Send only if changed
                    if (emotion != lastSentEmotion) {
                        commandSender.sendEmotion(emotion)
                        lastSentEmotion = emotion
                    }
                    
                    // Update UI
                    _state.value = _state.value.copy(
                        currentFrame = bitmap,
                        currentEmotion = emotion
                    )
                }
        }
    }
}
```

### Pattern 2: Practice Mode Feedback

```kotlin
class PracticeViewModel(
    private val commandSender: HttpCommandSender
) : ViewModel() {
    
    suspend fun checkAnswer(teacher: String, student: String) {
        val isCorrect = teacher.equals(student, ignoreCase = true)
        
        val command = if (isCorrect) {
            PracticeCommandType.CORRECT
        } else {
            PracticeCommandType.WRONG
        }
        
        commandSender.sendPracticeCommand(command)
        
        // Update stats
        _state.value = _state.value.copy(
            correctCount = if (isCorrect) state.value.correctCount + 1 else state.value.correctCount,
            totalCount = state.value.totalCount + 1
        )
    }
}
```

### Pattern 3: Connection with Error Handling

```kotlin
class ConnectionViewModel(
    private val connectionManager: ConnectionManager,
    private val streamService: HttpMjpegStreamService
) : ViewModel() {
    
    fun connect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            // Step 1: Connect WiFi
            when (val result = connectionManager.connectToESP32()) {
                is ConnectionResult.Success -> {
                    // Step 2: Test HTTP
                    testConnection()
                }
                is ConnectionResult.NeedsUserAction -> {
                    _state.value = _state.value.copy(
                        needsUserAction = true,
                        message = result.message,
                        isLoading = false
                    )
                }
                is ConnectionResult.Error -> {
                    _state.value = _state.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private suspend fun testConnection() {
        val isConnected = streamService.testConnection()
        
        _state.value = if (isConnected) {
            _state.value.copy(
                isConnected = true,
                isLoading = false
            )
        } else {
            _state.value.copy(
                error = "Failed to connect to camera",
                isLoading = false
            )
        }
    }
}
```

## Testing After Migration

### 1. Unit Tests
```kotlin
class HttpCommandSenderTest {
    @Test
    fun `sendEmotion sends correct HTTP request`() = runTest {
        val commandSender = HttpCommandSender()
        // Mock HTTP client
        // Verify correct URL and parameters
    }
}
```

### 2. Integration Tests
```kotlin
class StreamingIntegrationTest {
    @Test
    fun `streaming flow processes frames correctly`() = runTest {
        val streamService = HttpMjpegStreamService()
        // Mock HTTP response
        // Verify frame emission
    }
}
```

### 3. Manual Testing
- [ ] WiFi connection works
- [ ] HTTP streaming displays frames
- [ ] Commands send successfully
- [ ] Error handling works
- [ ] Session stats accurate

## Troubleshooting

### Issue: No frames received

**Cause:** ESP32 not responding or wrong IP
**Solution:**
```kotlin
// Test connection first
val isConnected = streamService.testConnection("192.168.4.1")
if (!isConnected) {
    // Show error, check ESP32
}
```

### Issue: Commands not sending

**Cause:** ESP32 command port blocked
**Solution:**
```kotlin
// Use retry logic
commandSender.sendCommand(code, retries = 5)
```

### Issue: WiFi connection fails on Android 10+

**Cause:** User approval required
**Solution:**
```kotlin
// Open WiFi settings for user
connectionManager.openWifiSettings()

// Wait for user to connect
// Then test connection
```

## Rollback Plan

If migration causes issues:

1. **Quick Fix:** Use legacy ViewModels temporarily
```kotlin
// In navigation
composable(Screen.Teaching.route) {
    // Use old ViewModel
    TeachingSessionScreen(...)  // Old screen
}
```

2. **Parallel Running:** Keep both implementations
```kotlin
// User can choose in settings
val useLegacyMode = settingsRepository.getUseLegacyMode()

val viewModel = if (useLegacyMode) {
    TeachingViewModel(get(), get(), get())  // Old
} else {
    NewTeachingViewModel(get(), get())  // New
}
```

3. **Full Rollback:** Remove new code, restore old
```
git revert <commit-hash>
```

## Performance Considerations

### HTTP vs TCP

| Metric | TCP | HTTP |
|--------|-----|------|
| Latency | Lower | Slightly higher |
| Reliability | Custom retry | Built-in retry |
| Debugging | Harder | Easier |
| Code Complexity | Higher | Lower |
| Maintenance | Harder | Easier |

### Optimization Tips

1. **Reduce frame rate** if battery drains quickly
```kotlin
// Add delay between frames
streamService.streamFrames()
    .collect { bitmap ->
        processFrame(bitmap)
        delay(100)  // 10 FPS instead of max
    }
```

2. **Reuse bitmaps** to reduce memory allocation
```kotlin
private var reusableBitmap: Bitmap? = null

fun processFrame(bitmap: Bitmap) {
    reusableBitmap?.recycle()
    reusableBitmap = bitmap
    // Use bitmap
}
```

3. **Batch commands** if sending multiple
```kotlin
// Instead of individual sends
listOf("Happy", "Sad", "Angry").forEach {
    commandSender.sendEmotion(it)
}

// Batch with delay
listOf("Happy", "Sad", "Angry").forEach {
    commandSender.sendEmotion(it)
    delay(50)  // Small delay between
}
```

## FAQ

**Q: Can I use both TCP and HTTP?**
A: Yes, during migration period. But choose one for production.

**Q: Which is better for battery?**
A: Both similar. HTTP might use slightly more due to headers, but negligible.

**Q: What about offline mode?**
A: Neither works offline (need ESP32 connection). HTTP same as TCP.

**Q: Is HTTP secure?**
A: Local network only, security not critical. Both TCP and HTTP are unencrypted on local WiFi.

**Q: Performance impact?**
A: Negligible. HTTP overhead is minimal for local network. Benefits outweigh costs.

## Support

For migration help:
1. Read this guide thoroughly
2. Check NEW_ARCHITECTURE.md for details
3. Review example implementations in feature modules
4. Test incrementally, one ViewModel at a time

## Conclusion

The HTTP migration makes CueSight:
- ✅ Simpler to maintain
- ✅ Easier to debug
- ✅ More reliable
- ✅ Better documented
- ✅ Future-proof

Take time to understand the new patterns, test thoroughly, and migrate incrementally.

---

**Happy Migrating! 🚀**
