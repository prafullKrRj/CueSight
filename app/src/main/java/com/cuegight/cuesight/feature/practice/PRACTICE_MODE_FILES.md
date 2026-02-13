# CueSight Practice Mode - File Structure

## Complete Module Implementation

This document lists all files created for the Practice Mode module.

### Data Layer (13 files)

#### Entities (4 files)
1. `data/entity/PracticeGuess.kt` - Individual guess records
2. `data/entity/PracticeSession.kt` - Session metadata
3. `data/entity/EmotionMastery.kt` - Per-emotion mastery tracking
4. `data/entity/TherapistWeight.kt` - Emotion importance weights

#### DAOs (4 files)
5. `data/dao/PracticeGuessDao.kt` - Guess database operations
6. `data/dao/PracticeSessionDao.kt` - Session database operations
7. `data/dao/EmotionMasteryDao.kt` - Mastery database operations
8. `data/dao/TherapistWeightDao.kt` - Weight database operations

#### Repository (1 file)
9. `data/repository/PracticeRepository.kt` - Repository with retry logic

### Domain Layer (6 files)

#### Models (5 files)
10. `domain/model/Emotion.kt` - Emotion enum (5 emotions)
11. `domain/model/ErpiResult.kt` - ERPI computation result
12. `domain/model/MasteryResult.kt` - Mastery computation result
13. `domain/model/CwaResult.kt` - CWA computation result
14. `domain/model/FeedbackState.kt` - UI feedback state

#### Engine (1 file)
15. `domain/engine/ErpfEngine.kt` - ERPF Analytics Engine

### UI Layer (9 files)

#### Screens (2 files)
16. `ui/PracticeModeScreen.kt` - Main practice session screen
17. `ui/AnalyticsDashboardScreen.kt` - Analytics dashboard

#### ViewModel (1 file)
18. `ui/PracticeModeViewModel.kt` - Practice mode business logic

#### Components (5 files)
19. `ui/components/EmotionButton.kt` - Color-coded emotion button
20. `ui/components/FeedbackCard.kt` - Auto-dismissing feedback
21. `ui/components/MasteryBars.kt` - Mastery progress bars
22. `ui/components/ConfusionMatrixGrid.kt` - Confusion matrix heatmap
23. `ui/components/WeightAdjustmentDialog.kt` - Weight adjustment UI

#### DataService Interface (included in ViewModel)
- `DataService` interface - WebSocket communication (placeholder)

### Dependency Injection (1 file)
24. `di/PracticeModule.kt` - Hilt DI configuration

### Documentation (2 files)
25. `PRACTICE_MODE_README.md` - Comprehensive implementation docs
26. `PRACTICE_MODE_FILES.md` - This file

### Database Updates (1 file modified)
27. Modified: `data/database/CueSightDatabase.kt` - Added 4 new entities and DAOs

---

## Total: 27 files (26 created + 1 modified)

## File Tree

```
feature/practice/
├── PRACTICE_MODE_README.md
├── PRACTICE_MODE_FILES.md
│
├── data/
│   ├── entity/
│   │   ├── PracticeGuess.kt
│   │   ├── PracticeSession.kt
│   │   ├── EmotionMastery.kt
│   │   └── TherapistWeight.kt
│   │
│   ├── dao/
│   │   ├── PracticeGuessDao.kt
│   │   ├── PracticeSessionDao.kt
│   │   ├── EmotionMasteryDao.kt
│   │   └── TherapistWeightDao.kt
│   │
│   └── repository/
│       └── PracticeRepository.kt
│
├── domain/
│   ├── model/
│   │   ├── Emotion.kt
│   │   ├── ErpiResult.kt
│   │   ├── MasteryResult.kt
│   │   ├── CwaResult.kt
│   │   └── FeedbackState.kt
│   │
│   └── engine/
│       └── ErpfEngine.kt
│
├── ui/
│   ├── PracticeModeScreen.kt
│   ├── AnalyticsDashboardScreen.kt
│   ├── PracticeModeViewModel.kt
│   │
│   └── components/
│       ├── EmotionButton.kt
│       ├── FeedbackCard.kt
│       ├── MasteryBars.kt
│       ├── ConfusionMatrixGrid.kt
│       └── WeightAdjustmentDialog.kt
│
└── di/
    └── PracticeModule.kt
```

## Lines of Code Summary

- **Data Layer:** ~450 lines
- **Domain Layer:** ~350 lines
- **UI Layer:** ~950 lines
- **DI Layer:** ~75 lines
- **Documentation:** ~500 lines

**Total:** ~2,325 lines of code + documentation

## Key Technologies Used

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Room Database** - Local persistence
- **Hilt** - Dependency injection
- **Kotlin Coroutines** - Asynchronous operations
- **StateFlow** - Reactive state management
- **Material Design 3** - UI components

## Integration Points

### 1. Database Integration
The module integrates with the existing `CueSightDatabase`:
- Added 4 new entities
- Added 4 new DAOs
- Database version incremented from 2 to 3

### 2. Navigation Integration
Add to navigation graph:
```kotlin
composable("practice_mode") {
    PracticeModeScreen(
        onNavigateToAnalytics = { navController.navigate("practice_analytics") }
    )
}

composable("practice_analytics") {
    AnalyticsDashboardScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### 3. DataService Integration
Replace placeholder `DataService` implementation with actual WebSocket service:
```kotlin
@Provides
@Singleton
fun provideDataService(webSocketService: WebSocketService): DataService {
    return object : DataService {
        override suspend fun sendCommand(command: String) {
            webSocketService.sendCommand(command)
        }
    }
}
```

## Build Configuration

Required in `build.gradle.kts`:
```kotlin
android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Compose
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
}
```

## Testing Strategy

### Unit Tests Needed
1. `ErpfEngineTest` - Test ERPI, Mastery, CWA calculations
2. `PracticeRepositoryTest` - Test retry logic
3. `PracticeModeViewModelTest` - Test session flow

### UI Tests Needed
1. Practice session flow
2. Feedback display
3. Analytics dashboard rendering
4. Weight adjustment dialog

### Integration Tests Needed
1. Database operations
2. End-to-end practice session
3. CSV export functionality

---

**Status:** ✅ Implementation Complete  
**Ready for:** Integration & Testing  
**Next Steps:** Replace DataService placeholder with actual WebSocket implementation

