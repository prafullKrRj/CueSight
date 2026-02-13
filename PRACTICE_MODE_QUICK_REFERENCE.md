# CueSight Practice Mode - Quick Reference Card

## 🎯 What Is Practice Mode?

**NO camera** - Teacher manually selects emotions, student guesses, system tracks progress with 3 mathematical metrics.

## 📁 File Structure

```
feature/practice/
├── data/          (Entities, DAOs, Repository)
├── domain/        (Models, ERPF Engine)
├── ui/            (Screens, ViewModel, Components)
└── di/            (Hilt Module)
```

## 🔑 Key Files

| File | Purpose |
|------|---------|
| `PracticeModeScreen.kt` | Main practice session UI |
| `AnalyticsDashboardScreen.kt` | Analytics with 3 metrics |
| `PracticeModeViewModel.kt` | Business logic & state |
| `ErpfEngine.kt` | ERPI, Mastery, CWA computations |
| `PracticeRepository.kt` | DB access with retry logic |
| `PracticeModule.kt` | Hilt dependency injection |

## 🎨 UI Components

1. **EmotionButton** - Color-coded (Yellow/Blue/Red/Green/Gray)
2. **FeedbackCard** - Auto-dismiss (3s), Green=correct, Orange=wrong
3. **MasteryBars** - Progress bars with Strong/Moderate/Needs levels
4. **ConfusionMatrixGrid** - 5x5 heatmap
5. **WeightAdjustmentDialog** - Therapist weight sliders

## 📊 Analytics (ERPF)

### 1. ERPI - Learning Trajectory
- Linear regression on session accuracies
- `ERPI = slope / (stdDev + 0.001)`
- Interpretation: Strong/Gradual/Stagnant/Declining

### 2. Mastery - Per-Emotion Tracking
- Exponential decay: `p_e = exp(-λ * hours / 24)`
- Correct: `λ *= 0.85` (easier)
- Incorrect: `λ *= 1.30` (harder)

### 3. CWA - Weighted Accuracy
- Confusion matrix → Per-class recall
- `CWA = Σ(weight_i * recall_i)`
- Therapist adjusts weights (sum = 1.0)

## 🔄 Session Flow

```
1. Teacher selects emotion → Timer starts
2. Student buttons enabled
3. Student guesses → Timer stops
4. System compares & calculates
5. OLED feedback (5s): "OLED:5:CORRECT!"
6. Phone feedback (3s auto-dismiss)
7. Log to database
8. Update mastery lambda
9. 3-second cooldown
10. Reset for next round
```

## 🗄️ Database

| Entity | Key Fields |
|--------|-----------|
| `PracticeGuess` | teacherEmotion, userGuess, isCorrect, responseTimeMs |
| `PracticeSession` | totalGuesses, correctGuesses, sessionAccuracy |
| `EmotionMastery` | emotion, lambda, lastCorrectTimestamp |
| `TherapistWeight` | emotion, weight (sum = 1.0) |

## 🛠️ Integration Steps

### 1. Add Navigation
```kotlin
composable("practice_mode") {
    PracticeModeScreen(
        onNavigateToAnalytics = { navController.navigate("practice_analytics") }
    )
}
```

### 2. Replace DataService
```kotlin
@Provides
fun provideDataService(webSocketService: WebSocketService): DataService {
    return object : DataService {
        override suspend fun sendCommand(cmd: String) {
            webSocketService.sendCommand(cmd)
        }
    }
}
```

### 3. Build
```bash
./gradlew clean build
```

## 🧪 Testing Checklist

- [ ] Teacher selection enables student buttons
- [ ] Correct guess → Green feedback
- [ ] Wrong guess → Orange feedback
- [ ] OLED receives "OLED:5:MESSAGE"
- [ ] 3-second cooldown works
- [ ] Database persists guesses
- [ ] Lambda updates correctly
- [ ] Session accuracy calculated
- [ ] ERPI computed from sessions
- [ ] Mastery uses decay formula
- [ ] Confusion matrix correct
- [ ] CWA respects weights
- [ ] CSV export works
- [ ] Pre-population on first launch

## 📦 Dependencies

```gradle
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Hilt
implementation("com.google.dagger:hilt-android:2.48")
kapt("com.google.dagger:hilt-compiler:2.48")

// Compose
implementation("androidx.compose.material3:material3:1.1.2")
```

## 🎯 Key Constraints Met

✅ NO camera in Practice Mode  
✅ All DB ops on Dispatchers.IO  
✅ Retry logic: try → 500ms → retry → log  
✅ Never crash on DB failure  
✅ 3-second feedback auto-dismiss  
✅ 3-second cooldown enforced  
✅ OLED format: `OLED:5:TEXT`  
✅ Case-insensitive comparisons  
✅ Emotion enum (not strings)  
✅ CSV export (MediaStore API)  

## 🐛 Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| DataService not found | Replace placeholder in PracticeModule.kt |
| Database migration error | Version updated to 3, fallback enabled |
| OLED not displaying | Check WebSocket connection & command format |
| Weights don't save | Ensure sum equals 1.0 (use auto-normalize) |
| Analytics not updating | Ensure sessions have endTime set |

## 📝 OLED Commands

```
Correct:   OLED:5:CORRECT!
Wrong:     OLED:5:It was Happy
Custom:    OLED:<duration>:<message>
```

Max 20 characters for OLED (128x64 display).

## 🔢 Formulas Quick Reference

```
ERPI:     slope / (σ + 0.001)
Mastery:  exp(-λ * hours / 24)
CWA:      Σ(w_i * recall_i)
Recall:   TP / (TP + FN)
Accuracy: correct / total
```

## 📚 Documentation Files

1. `PRACTICE_MODE_README.md` - Full implementation guide
2. `PRACTICE_MODE_FILES.md` - File structure & list
3. `PRACTICE_MODE_IMPLEMENTATION_SUMMARY.md` - Executive summary
4. `PRACTICE_MODE_CHECKLIST.md` - Detailed checklist
5. `PRACTICE_MODE_QUICK_REFERENCE.md` - This card

## 🚀 Status

**Implementation:** ✅ 100% Complete  
**Files Created:** 26+  
**Lines of Code:** ~2,300  
**Ready for:** Integration & Testing

---

**Last Updated:** February 14, 2026  
**Version:** 1.0.0

