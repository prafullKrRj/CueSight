# CueSight Revamp - Developer Quickstart

## 🚀 Get Started in 5 Minutes

### Step 1: Understand What Changed
Read this first: **README_REVAMP.md**
- Overview of the complete revamp
- What's been implemented (45%)
- What still needs to be done

### Step 2: Understand the Architecture
Read this second: **NEW_ARCHITECTURE.md**
- How HTTP services work
- Feature module structure
- Code examples

### Step 3: Start Coding
Pick a task from **IMPLEMENTATION_SUMMARY.md**

---

## 🎯 Top Priority Tasks

### Task 1: Implement MLKit Emotion Detection
**File:** `app/src/main/java/com/cuegight/cuesight/feature/teaching/NewTeachingViewModel.kt`

**What to do:**
```kotlin
// Line ~75-80
private fun detectEmotion(bitmap: Bitmap): String? {
    // TODO: Implement MLKit face detection
    
    // 1. Initialize FaceDetector (do once in init)
    val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
    
    // 2. Process bitmap
    val image = InputImage.fromBitmap(bitmap, 0)
    val task = detector.process(image)
    
    // 3. Get faces
    val faces = Tasks.await(task)
    if (faces.isEmpty()) return null
    
    // 4. Classify emotion from facial features
    val face = faces[0]
    val smilingProb = face.smilingProbability ?: 0f
    
    return when {
        smilingProb > 0.7f -> "Happy"
        smilingProb < 0.3f -> "Sad"
        else -> "Neutral"
    }
    // Add more sophisticated classification as needed
}
```

**Time:** 2-3 hours
**Difficulty:** Medium

### Task 2: Wire New Screens into Navigation
**File:** `app/src/main/java/com/cuegight/cuesight/MainActivity.kt`

**What to do:**
```kotlin
// Add routes for new screens (around line 90-100)

// New Teaching Screen
composable(
    route = "new_teaching/{studentId}",
    arguments = listOf(navArgument("studentId") { type = NavType.LongType })
) { backStackEntry ->
    val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
    val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
    NewTeachingScreen(
        studentId = studentId,
        studentName = studentName,
        onNavigateBack = { navController.popBackStack() }
    )
}

// New Practice Screen  
composable(
    route = "new_practice/{studentId}",
    arguments = listOf(navArgument("studentId") { type = NavType.LongType })
) { backStackEntry ->
    val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
    val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
    NewPracticeScreen(
        studentId = studentId,
        studentName = studentName,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

**Time:** 30 minutes
**Difficulty:** Easy

### Task 3: Test on Android Device
**Requirements:** Android device or emulator with Android 9+

**What to do:**
1. Build the app: `./gradlew assembleDebug`
2. Install on device: `adb install app/build/outputs/apk/debug/app-debug.apk`
3. Test Splash → Connection flow
4. Verify WiFi connection works
5. Test if screens navigate properly

**Time:** 1-2 hours
**Difficulty:** Easy

---

## 📂 Key Files Reference

### Core Services (Ready to Use)
```
core/
├── network/
│   ├── HttpMjpegStreamService.kt   ← HTTP streaming
│   ├── HttpCommandSender.kt        ← Send commands
│   └── ConnectionManager.kt        ← WiFi connection
└── util/
    └── EmotionMapper.kt             ← Emotion codes
```

### Feature Modules (New Screens)
```
feature/
├── splash/
│   └── SplashScreen.kt              ← Entry screen ✅
├── connection/
│   ├── ConnectionViewModel.kt       ← WiFi flow ✅
│   └── ConnectionScreen.kt          ← WiFi UI ✅
├── teaching/
│   ├── NewTeachingViewModel.kt      ← Streaming logic ✅
│   └── NewTeachingScreen.kt         ← Teaching UI ✅
└── practice/
    ├── NewPracticeViewModel.kt      ← Feedback logic ✅
    └── NewPracticeScreen.kt         ← Practice UI ✅
```

---

## 🔧 Common Development Tasks

### Add a New Feature Module
1. Create directory: `feature/myfeature/`
2. Create ViewModel: `MyFeatureViewModel.kt`
3. Create Screen: `MyFeatureScreen.kt`
4. Add to DI: `di/AppModule.kt`
5. Add route: `navigation/Screen.kt`
6. Wire in: `MainActivity.kt`

### Use HTTP Services
```kotlin
// Streaming
viewModelScope.launch {
    streamService.streamFrames("192.168.4.1")
        .collect { bitmap ->
            // Process frame
        }
}

// Commands
viewModelScope.launch {
    commandSender.sendEmotion("Happy")  // Sends v=1
}
```

### Connect to WiFi
```kotlin
val connectionManager = ConnectionManager(context)

when (val result = connectionManager.connectToESP32()) {
    is ConnectionResult.Success -> { /* Connected */ }
    is ConnectionResult.NeedsUserAction -> { /* Show settings */ }
    is ConnectionResult.Error -> { /* Show error */ }
}
```

---

## 🧪 Testing Workflow

### 1. Unit Tests
```bash
./gradlew test
```

### 2. Integration Tests  
```bash
./gradlew connectedAndroidTest
```

### 3. Manual Testing
1. Build debug APK
2. Install on device
3. Test each screen
4. Verify functionality
5. Check error handling

---

## 📚 Documentation Guide

| Document | When to Read | Purpose |
|----------|-------------|---------|
| **README_REVAMP.md** | First | Overview & status |
| **NEW_ARCHITECTURE.md** | Before coding | Architecture details |
| **IMPLEMENTATION_SUMMARY.md** | Planning work | Task list |
| **MIGRATION_GUIDE.md** | Migrating code | Migration patterns |
| **QUICKSTART.md** (this file) | Starting development | Quick reference |

---

## 💡 Quick Tips

### For First-Time Contributors
1. Read README_REVAMP.md completely
2. Understand the HTTP architecture
3. Look at existing feature modules
4. Copy patterns from NewTeachingViewModel or NewPracticeViewModel
5. Test incrementally

### For Experienced Developers
1. Review MIGRATION_GUIDE.md
2. Check IMPLEMENTATION_SUMMARY.md for tasks
3. Follow existing patterns
4. Write tests
5. Update documentation

### For Reviewers
1. Check architecture matches NEW_ARCHITECTURE.md
2. Verify patterns are consistent
3. Ensure no TCP code in new modules
4. Test on multiple Android versions
5. Verify documentation is updated

---

## 🐛 Troubleshooting

### Build Fails
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug

# Check Android SDK
# Verify gradle version
```

### WiFi Connection Fails
- Check ESP32_CAM is on
- Verify SSID is "ESP32_CAM"
- Check password is "12345678"
- Disable mobile data
- Try manual connection in settings

### HTTP Streaming Fails
- Check ESP32 is at 192.168.4.1
- Test with curl: `curl http://192.168.4.1/frame`
- Verify stream endpoint: `http://192.168.4.1/stream`
- Check firewall settings

### Commands Not Sending
- Verify ESP32 command port (81)
- Test with curl: `curl "http://192.168.4.1:81/cmd?v=1"`
- Check retry count in HttpCommandSender
- Look at logcat for errors

---

## 📞 Getting Help

### Resources
1. **Documentation** - Read all .md files in root
2. **Code Examples** - Look at feature/ modules
3. **Issue Tracker** - Check GitHub issues
4. **Logcat** - View Android logs: `adb logcat`

### Debug Commands
```bash
# View logs
adb logcat | grep CueSight

# Check connections
adb shell dumpsys wifi

# Monitor network
adb shell dumpsys connectivity
```

---

## ✅ Pre-Commit Checklist

Before committing:
- [ ] Code compiles without errors
- [ ] Follows existing patterns
- [ ] No hardcoded values
- [ ] Proper error handling
- [ ] Tested on device
- [ ] Documentation updated
- [ ] No TCP code in new modules
- [ ] Logcat output clean

---

## 🎯 Success Metrics

Your code is good when:
- ✅ Compiles without errors
- ✅ Follows HTTP architecture
- ✅ Has proper error handling
- ✅ Works on Android 9-14
- ✅ UI is teacher-friendly
- ✅ No technical jargon
- ✅ Tested on device
- ✅ Documentation updated

---

## 🚀 Next Steps

1. Pick a task from IMPLEMENTATION_SUMMARY.md
2. Read relevant documentation
3. Look at similar code in feature modules
4. Implement your changes
5. Test thoroughly
6. Update documentation
7. Submit PR

**Good luck! 🎉**

---

**Last Updated:** 2026-02-15
**Maintainer:** CueSight Team
**Version:** 2.0 (Revamp)
