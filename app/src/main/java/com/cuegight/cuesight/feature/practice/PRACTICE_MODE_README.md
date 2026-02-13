# CueSight Practice Mode - Implementation Documentation

## Overview
This document describes the complete implementation of the CueSight Practice Mode module, including all data layers, business logic, analytics engine, and UI components.

## Architecture

The Practice Mode follows clean architecture principles with clear separation of concerns:

```
feature/practice/
├── data/                    # Data layer
│   ├── entity/             # Room database entities
│   ├── dao/                # Data Access Objects
│   └── repository/         # Repository with retry logic
├── domain/                  # Business logic layer
│   ├── model/              # Domain models
│   └── engine/             # ERPF Analytics Engine
├── ui/                      # Presentation layer
│   ├── components/         # Reusable UI components
│   ├── PracticeModeScreen.kt
│   ├── AnalyticsDashboardScreen.kt
│   └── PracticeModeViewModel.kt
└── di/                      # Dependency injection
    └── PracticeModule.kt
```

## Key Features

### 1. Teacher-Student Practice Flow
- **NO camera involvement** - teacher manually selects emotions
- Teacher observes person's face and selects current emotion
- Student guesses the emotion they see
- System compares guess with teacher's selection
- Instant feedback on OLED glasses and phone
- Response time tracking
- 3-second cooldown between rounds

### 2. ERPF Analytics (Three Mathematical Metrics)

#### ERPI (Emotion Recognition Progress Index)
- Tracks learning trajectory over multiple sessions
- Uses least-squares linear regression on session accuracies
- Formula: `ERPI = slope / (σ + 0.001)`
- Interpretation:
  - > 0.3: Strong Improvement
  - > 0.0: Gradual Improvement
  - > -0.1: Stagnant
  - ≤ -0.1: Declining

#### Per-Emotion Mastery Score
- Tracks mastery of each individual emotion using exponential decay
- Formula: `p_e = exp(-λ_e * hours_since_last_correct / 24.0)`
- Lambda updates:
  - Correct: `λ = λ * 0.85` (easier to retain)
  - Incorrect: `λ = λ * 1.30` (harder to retain)
- Color-coded levels:
  - Green (>0.7): Strong
  - Yellow (0.4-0.7): Moderate
  - Red (<0.4): Needs Practice

#### Confusion-Weighted Accuracy (CWA)
- Builds 5x5 confusion matrix from all guesses
- Computes per-class recall for each emotion
- Weights recall by therapist-defined importance
- Formula: `CWA = Σ(w_i * recall_i)`
- Therapist can adjust weights (must sum to 1.0)

### 3. Database Schema

#### PracticeGuess
Stores each individual guess made during practice sessions.
```kotlin
- id: Int (auto-generated)
- sessionId: String (UUID)
- timestamp: Long
- teacherEmotion: String
- userGuess: String
- isCorrect: Boolean
- responseTimeMs: Long
```

#### PracticeSession
Stores session-level statistics.
```kotlin
- sessionId: String (UUID, primary key)
- startTime: Long
- endTime: Long?
- totalGuesses: Int
- correctGuesses: Int
- sessionAccuracy: Float
```

#### EmotionMastery
Tracks mastery level for each of the 5 emotions.
```kotlin
- emotion: String (primary key)
- lambda: Float (decay constant, starts at 1.0)
- lastCorrectTimestamp: Long
- totalAttempts: Int
- correctAttempts: Int
```

#### TherapistWeight
Stores therapist-defined importance weights for CWA calculation.
```kotlin
- emotion: String (primary key)
- weight: Float (default 0.20 for each emotion)
```

### 4. Retry Logic & Error Handling
All database operations use retry logic:
1. Try operation
2. If fails, wait 500ms
3. Retry once
4. If both fail, log error silently via Timber
5. **Never interrupt the session** - session must continue even if DB fails

Implementation in `PracticeRepository`:
```kotlin
private suspend fun insertWithRetry(operation: suspend () -> Long) {
    try {
        operation()
    } catch (e: Exception) {
        Log.w(TAG, "Database operation failed, retrying...")
        try {
            delay(500)
            operation()
        } catch (retryException: Exception) {
            Log.e(TAG, "Failed after retry. Session continues.")
            // Do not throw
        }
    }
}
```

### 5. OLED Communication Protocol
Format: `OLED:<duration_seconds>:<message_text>`

Examples:
- Correct: `OLED:5:CORRECT!`
- Wrong: `OLED:5:It was Happy`

Messages kept under 20 characters for OLED display (128x64 pixels).

### 6. UI Components

#### EmotionButton
Color-coded emotion button with selection state:
- Happy: Yellow (#FFEB3B)
- Sad: Blue (#2196F3)
- Angry: Red (#F44336)
- Surprised: Green (#4CAF50)
- Neutral: Gray (#9E9E9E)

#### FeedbackCard
Auto-dismissing feedback card (3 seconds):
- Green for correct answers
- Orange (supportive, not red) for incorrect

#### MasteryBars
Horizontal progress bars showing mastery level for each emotion with color-coding.

#### ConfusionMatrixGrid
5x5 heatmap showing confusion matrix:
- Diagonal (correct): Green tint
- Off-diagonal errors: Red tint
- Intensity based on count percentage

#### WeightAdjustmentDialog
Dialog for therapist to adjust emotion importance weights:
- Sliders for each emotion (0-1 range)
- Auto-normalize button
- Real-time sum validation
- Must equal 1.0 to save

### 7. Session Flow

```
1. Session Start
   ├── Generate UUID for sessionId
   ├── Create PracticeSession record
   ├── Pre-populate EmotionMastery (if first time)
   ├── Pre-populate TherapistWeight (if first time)
   └── Start session timer

2. Each Round
   ├── Teacher selects emotion → Timer starts
   ├── Student buttons enabled
   ├── Student selects guess → Timer stops
   ├── Calculate response time & correctness
   ├── Send OLED feedback (5 seconds)
   ├── Show phone feedback (3 seconds auto-dismiss)
   ├── Log PracticeGuess to database
   ├── Update EmotionMastery lambda
   ├── 3-second cooldown
   └── Reset for next round

3. Session End
   ├── Calculate session accuracy
   ├── Update PracticeSession with end stats
   ├── Stop timer
   └── Navigate to Analytics Dashboard
```

### 8. CSV Export Feature
Exports all practice guesses to Downloads folder:
- Uses MediaStore API for Android 10+
- Filename: `cueSight_practice_export_<timestamp>.csv`
- Columns: `timestamp,session_id,teacher_emotion,user_guess,is_correct,response_time_ms`

### 9. Pre-Population on First Launch
On first Practice Mode launch:
- Checks if EmotionMastery table is empty
- If empty, inserts defaults for all 5 emotions (λ=1.0)
- Checks if TherapistWeight table is empty
- If empty, inserts equal weights (0.20 each)

### 10. Key Constraints Implemented

✅ All DB operations on `Dispatchers.IO`  
✅ OLED commands sent on `Dispatchers.IO` via DataService  
✅ Feedback card auto-dismisses after 3 seconds  
✅ 3-second cooldown enforced between rounds  
✅ Insert retry logic (try → wait 500ms → retry → log)  
✅ Never crash on DB failure  
✅ Case-insensitive emotion comparisons  
✅ Emotions defined as enum (not raw strings)  
✅ CSV export uses MediaStore for Android 10+  
✅ Response time measured in milliseconds  
✅ Session timer displays MM:SS format  

## Usage

### Starting a Practice Session
```kotlin
// Navigate to practice mode
navController.navigate("practice_mode")
```

### Ending a Session & Viewing Analytics
```kotlin
// Automatically navigates to analytics on session end
viewModel.endSession()
// Analytics screen shows ERPI, Mastery, Confusion Matrix, CWA
```

### Adjusting Therapist Weights
```kotlin
// From Analytics Dashboard, tap "Adjust Weights"
// Dialog opens with sliders for each emotion
// Auto-normalize button ensures sum = 1.0
// Save validates weights before persisting
```

### Exporting Data
```kotlin
// From Analytics Dashboard toolbar
// Tap Export icon
// CSV saved to Downloads folder
// Toast confirms success/failure
```

## Dependencies Required

Add to `app/build.gradle`:
```gradle
dependencies {
    // Room Database
    implementation "androidx.room:room-runtime:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    
    // Hilt Dependency Injection
    implementation "com.google.dagger:hilt-android:2.48"
    kapt "com.google.dagger:hilt-compiler:2.48"
    implementation "androidx.hilt:hilt-navigation-compose:1.1.0"
    
    // Compose
    implementation "androidx.compose.ui:ui:1.5.4"
    implementation "androidx.compose.material3:material3:1.1.2"
    implementation "androidx.compose.material:material-icons-extended:1.5.4"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    
    // Lifecycle
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.6.2"
}
```

## Testing Checklist

- [ ] Teacher emotion selection enables student buttons
- [ ] Student buttons disabled until teacher selects
- [ ] Correct guess shows green feedback
- [ ] Wrong guess shows orange feedback
- [ ] OLED receives correct command format
- [ ] Response time calculated accurately
- [ ] Database persists guesses correctly
- [ ] Lambda updates after each guess
- [ ] 3-second cooldown prevents rapid clicking
- [ ] Session timer displays correctly
- [ ] Session accuracy calculated correctly
- [ ] ERPI computed from session accuracies
- [ ] Mastery scores use exponential decay
- [ ] Confusion matrix built correctly
- [ ] CWA uses therapist weights
- [ ] Weight adjustment enforces sum = 1.0
- [ ] CSV export includes all guesses
- [ ] Pre-population occurs on first launch
- [ ] Retry logic handles DB failures silently

## Future Enhancements

1. **Visual Analytics**
   - Line chart for ERPI (session accuracy over time)
   - Radar chart for emotion mastery
   - More detailed confusion matrix visualizations

2. **Advanced Features**
   - Session history with detailed logs
   - Progress reports over time periods
   - Emotion difficulty ranking
   - Personalized practice recommendations

3. **Performance**
   - Database indices for faster queries
   - Pagination for large session lists
   - Background processing for analytics

## Notes

- The `DataService` interface is a placeholder - replace with actual WebSocket implementation
- Database version incremented to 3 with new entities
- All string comparisons are case-insensitive for robustness
- Emotions enum prevents typos and ensures consistency
- Repository uses `withContext(Dispatchers.IO)` for all blocking operations
- ViewModel uses `viewModelScope` for automatic cancellation

---

**Implementation Status:** ✅ Complete  
**Last Updated:** February 14, 2026  
**Module Version:** 1.0.0

