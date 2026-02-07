

# CueSight: Complete Production-Grade System Architecture & LLM Implementation Prompt

Below is the comprehensive, in-depth architectural specification and implementation prompt for CueSight. This is written as a self-contained instruction set that an LLM coding agent can consume to generate the complete Android (Kotlin) and ESP32-CAM (Arduino/C++) codebases.

---

## 1. SYSTEM OVERVIEW & ROLES

CueSight is a **teacher-operated** Android application paired with ESP32-CAM smart glasses. There is **one app** — used exclusively by the **teacher/therapist**. The student wears the glasses; the teacher controls everything from the phone.

### Key Clarifications
- **No student-facing app.** The teacher holds the phone, selects modes, logs guesses, and reviews analytics.
- **Teaching Mode:** The ESP32-CAM glasses display the detected emotion label directly on a small OLED/TFT screen mounted on the glasses frame, so the student sees the label in their peripheral vision. The teacher sees the live feed + label on the phone for monitoring.
- **Practice Mode:** The glasses display shows nothing (blank/neutral). The teacher verbally asks the student "What emotion is this person showing?" The student answers verbally. The teacher taps the student's guess on the phone. The app compares it against the ML model's detection and logs the result.
- **Session Model:** At app launch, the teacher enters/selects a Student ID. The entire session (30-40 min) is bound to that student. On logout/close, the session ends and is sealed.

---

## 2. COMMUNICATION ARCHITECTURE

### 2.1 Device Discovery: UDP Broadcast

The ESP32-CAM and the Android phone must be on the **same local Wi-Fi network** (the ESP32 creates a SoftAP hotspot, or both join an existing network).

**Protocol:** UDP broadcast for discovery.

```
ESP32-CAM behavior:
- On boot, ESP32 broadcasts a UDP packet every 2 seconds on port 4210
- Packet payload (plain text): "CUESIGHT|<ESP32_IP>|<STREAM_PORT>|<CMD_PORT>"
- Example: "CUESIGHT|192.168.4.1|81|82"

Android behavior:
- App listens on UDP port 4210 for broadcast packets
- Parses the payload to extract ESP32 IP, stream port, and command port
- Displays "Device Found: 192.168.4.1" on the connection screen
- Teacher taps "Connect" to initiate the TCP/WebSocket handshake
```

### 2.2 Video Streaming: MJPEG over HTTP (ESP32 → Android)

**Why MJPEG:** Lowest memory footprint on ESP32-CAM. No encoding library needed. The ESP32-CAM Arduino library has native MJPEG streaming. Each frame is a complete JPEG — no inter-frame dependency, so dropped frames don't cascade.

```
ESP32-CAM:
- Runs an HTTP server on port 81
- Endpoint: GET /stream
- Response: multipart/x-mixed-replace; boundary=frame
- Each part: Content-Type: image/jpeg followed by raw JPEG bytes
- Resolution: 320x240 (QVGA) for RAM efficiency — ESP32-CAM has ~520KB SRAM
- Frame rate target: 10-12 FPS (sufficient for facial expression analysis)
- JPEG quality: 12 (out of 63 scale, lower = better quality, ~15-20KB per frame)
```

### 2.3 Bidirectional Command Channel: WebSocket (Android ↔ ESP32)

**Why WebSocket:** Lowest latency bidirectional protocol. Single persistent TCP connection. Minimal overhead per message (2-6 bytes header). The ESP32 Arduino WebSocket library (`WebSocketsServer`) is lightweight.

```
ESP32-CAM:
- Runs a WebSocket server on port 82
- Accepts one client connection (the Android app)

Message format (JSON, kept small for ESP32 memory):

Android → ESP32 messages:
  {"cmd": "mode", "val": "teach"}       // Switch to teaching mode
  {"cmd": "mode", "val": "practice"}    // Switch to practice mode
  {"cmd": "emotion", "val": "Happy"}    // Send detected emotion label (teaching mode)
  {"cmd": "clear"}                       // Clear the glasses display
  {"cmd": "ping"}                        // Keep-alive

ESP32 → Android messages:
  {"status": "ok", "mode": "teach"}     // Acknowledgement
  {"status": "ok", "mode": "practice"}
  {"status": "pong"}                     // Keep-alive response
  {"status": "error", "msg": "..."}
```

### 2.4 Data Flow Summary

```
┌─────────────────┐         UDP Broadcast (4210)          ┌─────────────────┐
│                 │ ◄──────────────────────────────────── │                 │
│   Android App   │                                       │   ESP32-CAM     │
│   (Teacher)     │         MJPEG Stream (HTTP :81)       │   (Glasses)     │
│                 │ ◄──────────────────────────────────── │                 │
│                 │                                       │                 │
│                 │      WebSocket Bidirectional (:82)     │                 │
│                 │ ◄───────────────────────────────────► │                 │
└─────────────────┘                                       └─────────────────┘

Data direction:
  ESP32 → Android:  Video frames (MJPEG), status acks
  Android → ESP32:  Mode commands, emotion labels (for glasses display), clear/ping
```

---

## 3. ML PIPELINE (On-Device, Android)

### 3.1 Model: MobileNetV3-Small Fine-tuned for FER

**Do NOT use Google ML Kit's built-in face detection smiling probability.** It only gives "smiling probability" — not multi-class emotion classification.

**Model specification:**

```
Base model: MobileNetV3-Small (pretrained on ImageNet)
Fine-tuned on: FER-2013 dataset (35,887 images, 7 classes)
   Classes: [Angry, Disgust, Fear, Happy, Sad, Surprise, Neutral]
   Note: For v1, collapse to 5 classes: [Happy, Sad, Angry, Surprise, Neutral]
         (merge Disgust→Angry, Fear→Surprise for simplicity with ASD learners)

Input: 224x224x3 RGB image (face crop, normalized to [-1, 1])
Output: Softmax probability vector of length 5

Model format: TensorFlow Lite (.tflite), quantized to float16
Expected size: ~2-3 MB
Inference time: <30ms on mid-range Android device

Confidence threshold: 0.60 (only display/log emotions above this confidence)
```

### 3.2 Face Detection

Use **Google ML Kit Face Detection** only for face bounding box extraction (not emotion classification):

```
ML Kit FaceDetector configuration:
  - Performance mode: FAST
  - Landmark mode: NONE (not needed)
  - Classification mode: NONE (we use our own classifier)
  - Min face size: 0.15 (15% of image dimension)
  - Face tracking: ENABLED (to track same face across frames)

Pipeline per frame:
  1. Receive JPEG frame from MJPEG stream
  2. Decode to Bitmap
  3. Pass to ML Kit FaceDetector → get List<Face> with bounding boxes
  4. For each Face:
     a. Crop the bounding box region from the Bitmap
     b. Resize crop to 224x224
     c. Normalize pixel values to [-1, 1]
     d. Run MobileNetV3 TFLite interpreter → get [p_happy, p_sad, p_angry, p_surprise, p_neutral]
     e. Take argmax → predicted emotion
     f. If max probability ≥ 0.60, accept the prediction
  5. Stabilization: maintain a rolling buffer of last 5 predictions per tracked face ID
     - Only update displayed emotion if ≥ 3 of last 5 agree (majority vote)
     - This prevents flickering labels
```

---

## 4. DATA STORAGE: Room Database (SQLite)

### 4.1 Complete Schema

```sql
-- Table: students
-- Stores student profiles. Teacher creates these once.
CREATE TABLE students (
    student_id       TEXT PRIMARY KEY,          -- e.g., "STU-001" or teacher-assigned ID
    student_name     TEXT NOT NULL,
    date_of_birth    TEXT,                       -- ISO 8601: "2015-03-20"
    notes            TEXT,                       -- Free-form therapist notes
    created_at       INTEGER NOT NULL            -- Unix timestamp millis
);

-- Table: sessions
-- One row per session. A session starts when teacher selects a student and taps "Start Session".
-- A session ends when teacher taps "End Session" or closes the app.
CREATE TABLE sessions (
    session_id       TEXT PRIMARY KEY,           -- UUID
    student_id       TEXT NOT NULL,
    start_time       INTEGER NOT NULL,           -- Unix timestamp millis
    end_time         INTEGER,                    -- NULL while session is active
    teaching_duration_ms  INTEGER DEFAULT 0,     -- Total time spent in Teaching Mode
    practice_duration_ms  INTEGER DEFAULT 0,     -- Total time spent in Practice Mode
    total_guesses    INTEGER DEFAULT 0,
    correct_guesses  INTEGER DEFAULT 0,
    session_notes    TEXT,                       -- Teacher can add notes after session
    FOREIGN KEY (student_id) REFERENCES students(student_id)
);

-- Table: emotion_logs
-- One row per guess attempt in Practice Mode.
CREATE TABLE emotion_logs (
    log_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id       TEXT NOT NULL,
    student_id       TEXT NOT NULL,
    timestamp        INTEGER NOT NULL,           -- Unix timestamp millis
    ai_detected      TEXT NOT NULL,              -- "Happy", "Sad", "Angry", "Surprise", "Neutral"
    ai_confidence    REAL NOT NULL,              -- 0.0 to 1.0
    student_guess    TEXT NOT NULL,              -- What teacher logged as student's verbal answer
    is_correct       INTEGER NOT NULL,           -- 1 = correct, 0 = incorrect
    response_time_ms INTEGER,                    -- Time between "Guess" button tap and answer selection
    FOREIGN KEY (session_id) REFERENCES sessions(session_id),
    FOREIGN KEY (student_id) REFERENCES students(student_id)
);

-- Table: teaching_snapshots
-- Periodic snapshots during Teaching Mode for session timeline.
-- Logged every 30 seconds OR on emotion change, whichever comes first.
CREATE TABLE teaching_snapshots (
    snapshot_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id       TEXT NOT NULL,
    timestamp        INTEGER NOT NULL,
    detected_emotion TEXT NOT NULL,
    confidence       REAL NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
);

-- Table: mode_switches
-- Logs every time the teacher switches between Teaching and Practice mode within a session.
CREATE TABLE mode_switches (
    switch_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id       TEXT NOT NULL,
    timestamp        INTEGER NOT NULL,
    from_mode        TEXT NOT NULL,               -- "TEACHING" or "PRACTICE"
    to_mode          TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
);
```

### 4.2 Room Entity/DAO Structure (Kotlin)

```
Package: com.cuesight.data

Entities:
  - StudentEntity (maps to students table)
  - SessionEntity (maps to sessions table)
  - EmotionLogEntity (maps to emotion_logs table)
  - TeachingSnapshotEntity (maps to teaching_snapshots table)
  - ModeSwitchEntity (maps to mode_switches table)

DAOs:
  - StudentDao
      insertStudent(student)
      getStudentById(id): StudentEntity?
      getAllStudents(): Flow<List<StudentEntity>>
      updateStudent(student)

  - SessionDao
      insertSession(session)
      endSession(sessionId, endTime, teachingDuration, practiceDuration, totalGuesses, correctGuesses)
      getSessionById(id): SessionEntity?
      getSessionsForStudent(studentId): Flow<List<SessionEntity>>
      getActiveSession(): SessionEntity?
      updateSessionNotes(sessionId, notes)

  - EmotionLogDao
      insertLog(log)
      getLogsForSession(sessionId): Flow<List<EmotionLogEntity>>
      getLogsForStudent(studentId): Flow<List<EmotionLogEntity>>
      getConfusionMatrixData(studentId): List<ConfusionPair>
        -- ConfusionPair is a data class: { aiDetected: String, studentGuess: String, count: Int }
        -- Query: SELECT ai_detected, student_guess, COUNT(*) as count FROM emotion_logs WHERE student_id = :studentId GROUP BY ai_detected, student_guess
      getAccuracyOverTime(studentId): List<AccuracyPoint>
        -- AccuracyPoint: { sessionId: String, date: Long, accuracy: Float }
        -- Query: SELECT session_id, MIN(timestamp) as date, (SUM(is_correct) * 1.0 / COUNT(*)) as accuracy FROM emotion_logs WHERE student_id = :studentId GROUP BY session_id ORDER BY date

  - TeachingSnapshotDao
      insertSnapshot(snapshot)
      getSnapshotsForSession(sessionId): List<TeachingSnapshotEntity>

  - ModeSwitchDao
      insertSwitch(switch)
      getSwitchesForSession(sessionId): List<ModeSwitchEntity>

Database:
  - CueSightDatabase (Room database class, version 1)
```

---

## 5. ANDROID APP: COMPLETE SCREEN SPECIFICATION

### Architecture: MVVM + Clean Architecture

```
Tech stack:
  - Language: Kotlin
  - UI: Jetpack Compose
  - Navigation: Compose Navigation (NavHost)
  - DI: Hilt
  - Async: Kotlin Coroutines + Flow
  - DB: Room
  - ML: TensorFlow Lite (MobileNetV3), ML Kit Face Detection
  - Network: OkHttp (MJPEG stream parsing), OkHttp WebSocket client
  - Image: Coil (for any static images in analytics)

Package structure:
  com.cuesight/
  ├── di/                      -- Hilt modules
  ├── data/
  │   ├── db/                  -- Room entities, DAOs, database
  │   ├── network/             -- UDP discovery, MJPEG client, WebSocket client
  │   └── repository/          -- Repository implementations
  ├── domain/
  │   ├── model/               -- Domain models
  │   ├── repository/          -- Repository interfaces
  │   └── usecase/             -- Use cases
  ├── ml/
  │   ├── FaceDetectorHelper    -- ML Kit face detection wrapper
  │   ├── EmotionClassifier     -- TFLite MobileNetV3 wrapper
  │   └── EmotionStabilizer     -- Rolling buffer + majority vote logic
  ├── ui/
  │   ├── navigation/          -- NavHost, routes
  │   ├── splash/              -- Splash screen
  │   ├── studentselect/       -- Student selection/creation screen
  │   ├── connection/          -- Device discovery & connection screen
  │   ├── session/             -- Active session screen (Teaching + Practice modes)
  │   ├── analytics/           -- Post-session analytics & history
  │   └── components/          -- Shared composables
  └── util/                    -- Extensions, constants
```

### Screen-by-Screen Specification

---

#### SCREEN 1: Splash Screen
**Route:** `splash`

```
Behavior:
  - Show CueSight logo + tagline "Emotion Recognition Training" for 2 seconds
  - Auto-navigate to Student Select screen
  - No user interaction

UI Elements:
  ┌──────────────────────────────┐
  │                              │
  │        [CueSight Logo]       │
  │                              │
  │   "Emotion Recognition       │
  │        Training System"      │
  │                              │
  │     ● ● ● (loading dots)    │
  │                              │
  └──────────────────────────────┘
```

---

#### SCREEN 2: Student Select Screen
**Route:** `student_select`

This is the **entry point** of every session. The teacher must identify which student they're working with.

```
UI Elements:
  ┌──────────────────────────────────┐
  │  CueSight                  [+]   │  ← "+" button to add new student
  │──────────────────────────────────│
  │  Select Student                  │
  │                                  │
  │  ┌────────────────────────────┐  │
  │  │ 🔍 Search students...     │  │  ← Search/filter field
  │  └────────────────────────────┘  │
  │                                  │
  │  ┌────────────────────────────┐  │
  │  │ 👤 Arjun Mehta             │  │  ← Tap to select → go to Connection screen
  │  │    ID: STU-001             │  │
  │  │    Last session: 3 days ago│  │
  │  └────────────────────────────┘  │
  │  ┌────────────────────────────┐  │
  │  │ 👤 Priya Sharma            │  │
  │  │    ID: STU-002             │  │
  │  │    Last session: 1 week ago│  │
  │  └────────────────────────────┘  │
  │  ┌────────────────────────────┐  │
  │  │ 👤 Rohan Das               │  │
  │  │    ID: STU-003             │  │
  │  │    No sessions yet         │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  [View Analytics]                │  ← Navigate to analytics (no active session)
  └──────────────────────────────────┘

On tap of student card:
  - Store selected studentId in ViewModel/SharedState
  - Navigate to Connection Screen

On tap of "+":
  - Show bottom sheet / dialog:
    ┌────────────────────────────┐
    │ Add New Student            │
    │                            │
    │ Student ID: [__________]   │  ← Required, teacher assigns
    │ Name:       [__________]   │  ← Required
    │ DOB:        [__________]   │  ← Optional, date picker
    │ Notes:      [__________]   │  ← Optional, multiline
    │                            │
    │      [Cancel]  [Save]      │
    └────────────────────────────┘
```

---

#### SCREEN 3: Device Connection Screen
**Route:** `connection/{studentId}`

```
UI Elements:
  ┌──────────────────────────────────┐
  │  ← Back         Connection       │
  │──────────────────────────────────│
  │                                  │
  │  Student: Arjun Mehta (STU-001)  │
  │                                  │
  │  ┌────────────────────────────┐  │
  │  │                            │  │
  │  │   🔍 Searching for         │  │  ← Animated radar/pulse icon
  │  │      CueSight Glasses...   │  │
  │  │                            │  │
  │  │   Listening on UDP :4210   │  │  ← Debug info (small, gray text)
  │  │                            │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Found Devices:                  │
  │  ┌────────────────────────────┐  │
  │  │ 📡 CueSight-Glasses        │  │  ← Appears when UDP broadcast received
  │  │    IP: 192.168.4.1         │  │
  │  │    Stream: :81  Cmd: :82   │  │
  │  │              [Connect]     │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  [Enter IP Manually]             │  ← Fallback: manual IP entry
  │                                  │
  └──────────────────────────────────┘

On "Connect" tap:
  1. Attempt to open WebSocket connection to ws://<IP>:82
  2. Attempt to open MJPEG stream from http://<IP>:81/stream
  3. Show progress indicator: "Connecting..."
  4. On success:
     - Show green checkmark: "Connected ✓"
     - Wait 1 second
     - Create new Session in Room DB (session_id = UUID, student_id, start_time = now)
     - Navigate to Session Screen
  5. On failure:
     - Show error: "Connection failed. Ensure glasses are powered on and on the same network."
     - [Retry] button
```

---

#### SCREEN 4: Active Session Screen (THE CORE SCREEN)
**Route:** `session/{sessionId}`

This is where the teacher spends 30-40 minutes. It has **two sub-modes** toggled by a switch.

##### 4A: Teaching Mode View

```
  ┌──────────────────────────────────┐
  │  Arjun (STU-001)    ⏱ 00:14:32  │  ← Student name + session timer
  │──────────────────────────────────│
  │                                  │
  │  ┌────────────────────────────┐  │
  │  │                            │  │
  │  │    [LIVE VIDEO FEED]       │  │  ← MJPEG stream rendered here
  │  │                            │  │
  │  │    ┌──────────┐            │  │  ← Bounding box around detected face
  │  │    │          │            │  │
  │  │    │  😊 Happy │            │  │  ← Emotion label overlay (semi-transparent)
  │  │    │  (87%)   │            │  │  ← Confidence percentage
  │  │    └──────────┘            │  │
  │  │                            │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Current Detection:              │
  │  ┌────────────────────────────┐  │
  │  │  😊  HAPPY     87%         │  │  ← Large, clear display for teacher
  │  └────────────────────────────┘  │
  │                                  │
  │  ┌──────────┐  ┌──────────────┐  │
  │  │ TEACHING │  │  PRACTICE    │  │  ← Mode toggle (segmented control)
  │  │ ██████   │  │              │  │     TEACHING is currently active (highlighted)
  │  └──────────┘  └──────────────┘  │
  │                                  │
  │  [End Session]                   │  ← Red button, bottom
  └──────────────────────────────────┘

Teaching Mode behavior:
  - Video feed is displayed with face bounding boxes and emotion overlays
  - The detected emotion label is ALSO sent to ESP32 via WebSocket:
      {"cmd": "emotion", "val": "Happy"}
  - ESP32 renders "Happy 😊" on the small OLED/TFT on the glasses frame
  - Student sees the label on glasses; teacher monitors on phone
  - Teaching snapshots logged to Room every 30 seconds or on emotion change
  - No guess buttons, no interaction logging — pure passive learning
```

##### 4B: Practice Mode View

```
  ┌──────────────────────────────────┐
  │  Arjun (STU-001)    ⏱ 00:22:15  │
  │──────────────────────────────────│
  │                                  │
  │  ┌────────────────────────────┐  │
  │  │                            │  │
  │  │    [LIVE VIDEO FEED]       │  │  ← Same MJPEG stream, but NO overlay
  │  │                            │  │     No bounding box, no labels visible
  │  │                            │  │
  │  │                            │  │
  │  │                            │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  AI Detection (hidden from       │  ← Small text: "Detection running in background"
  │  student): ██████████            │     The actual emotion is HIDDEN on this screen
  │                                  │     (teacher doesn't see it either until after guess)
  │                                  │
  │  ┌──────────────────────────┐    │
  │  │  🎯 LOG STUDENT'S GUESS  │    │  ← Large primary button
  │  └──────────────────────────┘    │
  │                                  │
  │  ┌──────────┐  ┌──────────────┐  │
  │  │ TEACHING │  │  PRACTICE    │  │
  │  │          │  │  ██████████  │  │  ← PRACTICE is active
  │  └──────────┘  └──────────────┘  │
  │                                  │
  │  Session Stats:  12/15 correct   │  ← Running accuracy for this session
  │  [End Session]                   │
  └──────────────────────────────────┘

On "LOG STUDENT'S GUESS" tap:
  1. The video feed PAUSES (freeze frame)
  2. The app internally snapshots the current AI-detected emotion + confidence
  3. A bottom sheet slides up:

  ┌──────────────────────────────────┐
  │  What emotion did the student    │
  │  say they see?                   │
  │                                  │
  │  ┌──────┐ ┌──────┐ ┌──────┐     │
  │  │ 😊   │ │ 😢   │ │ 😠   │     │
  │  │Happy │ │ Sad  │ │Angry │     │
  │  └──────┘ └──────┘ └──────┘     │
  │  ┌──────┐ ┌──────┐              │
  │  │ 😲   │ │ 😐   │              │
  │  │Surpr.│ │Neutr.│              │
  │  └──────┘ └──────┘              │
  │                                  │
  │  [Cancel]                        │  ← Dismiss without logging
  └──────────────────────────────────┘

  4. Teacher taps the emotion the student verbally stated
  5. The app compares: student_guess vs ai_detected
  6. Result overlay appears for 3 seconds:

  If CORRECT:
  ┌──────────────────────────────────┐
  │         ✅ Correct!              │
  │                                  │
  │    The emotion was: Happy 😊     │
  │    Confidence: 87%               │
  └──────────────────────────────────┘

  If INCORRECT:
  ┌──────────────────────────────────┐
  │         ❌ Not quite             │
  │                                  │
  │  Student said:  Angry 😠         │
  │  Detected:      Neutral 😐      │
  │  Confidence: 73%                 │
  └──────────────────────────────────┘

  7. Log to Room DB:
     INSERT INTO emotion_logs (session_id, student_id, timestamp, ai_detected,
       ai_confidence, student_guess, is_correct, response_time_ms)
     VALUES (current_session_id, current_student_id, System.currentTimeMillis(),
       "Neutral", 0.73, "Angry", 0, elapsed_ms_since_button_tap)

  8. Resume video feed

Practice Mode behavior on ESP32 side:
  - When mode switches to Practice, Android sends: {"cmd": "mode", "val": "practice"}
  - ESP32 clears the OLED display — student sees NO labels
  - The teacher has the student look at a person's face (could be the teacher making expressions)
  - The teacher asks "What emotion do you see?"
  - Student answers verbally
  - Teacher logs it

Mode Switch behavior:
  - On toggle between Teaching ↔ Practice:
    - Log to mode_switches table
    - Send WebSocket command to ESP32
    - Update UI immediately
    - Track cumulative time in each mode for the session record
```

---

#### SCREEN 5: End Session Confirmation
**Route:** Dialog/overlay on Session Screen

```
Triggered by: "End Session" button tap

  ┌──────────────────────────────────┐
  │  End Session?                    │
  │                                  │
  │  Duration: 34 min 12 sec         │
  │  Teaching: 18 min                │
  │  Practice: 16 min                │
  │  Guesses: 23                     │
  │  Correct: 17 (73.9%)             │
  │                                  │
  │  Session Notes (optional):       │
  │  ┌────────────────────────────┐  │
  │  │ Student showed improvement │  │  ← Free-form text input
  │  │ with Happy but still       │  │
  │  │ struggles with Neutral     │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  [Cancel]     [End & Save]       │
  └──────────────────────────────────┘

On "End & Save":
  1. Update session record:
     UPDATE sessions SET end_time = now, teaching_duration_ms = X,
       practice_duration_ms = Y, total_guesses = 23, correct_guesses = 17,
       session_notes = "..." WHERE session_id = current_id
  2. Send to ESP32: {"cmd": "clear"} and close WebSocket
  3. Close MJPEG stream
  4. Navigate to Session Summary screen
```

---

#### SCREEN 6: Session Summary Screen
**Route:** `session_summary/{sessionId}`

```
  ┌──────────────────────────────────┐
  │  ← Back        Session Summary   │
  │──────────────────────────────────│
  │  Arjun (STU-001)                 │
  │  Feb 7, 2026 • 34 min           │
  │                                  │
  │  Overall Accuracy                │
  │  ┌────────────────────────────┐  │
  │  │        73.9%               │  │  ← Large circular progress indicator
  │  │      17 / 23               │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Per-Emotion Breakdown           │
  │  ┌────────────────────────────┐  │
  │  │ Happy     ████████░░  80%  │  │  ← Horizontal bar chart
  │  │ Sad       ██████░░░░  60%  │  │
  │  │ Angry     ████░░░░░░  40%  │  │
  │  │ Surprise  █████████░  90%  │  │
  │  │ Neutral   ███████░░░  70%  │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Common Confusions               │
  │  ┌────────────────────────────┐  │
  │  │ Angry confused with Sad    │  │  ← Extracted from confusion matrix
  │  │ (3 times)                  │  │
  │  │ Neutral confused with Happy│  │
  │  │ (2 times)                  │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Guess Log (chronological)       │
  │  ┌────────────────────────────┐  │
  │  │ 14:32  Happy → Happy  ✅   │  │  ← Scrollable list of all guesses
  │  │ 14:33  Sad → Sad      ✅   │  │
  │  │ 14:35  Angry → Sad    ❌   │  │
  │  │ 14:36  Neutral → Happy❌   │  │
  │  │ ...                        │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Teacher Notes:                  │
  │  "Student showed improvement..." │
  │                                  │
  │  [Back to Home]                  │
  └──────────────────────────────────┘
```

---

#### SCREEN 7: Analytics Dashboard
**Route:** `analytics/{studentId}`

Accessible from Student Select screen (long-press student → "View Analytics") or after a session.

```
  ┌──────────────────────────────────┐
  │  ← Back      Analytics          │
  │──────────────────────────────────│
  │  Arjun Mehta (STU-001)           │
  │  12 sessions • Since Jan 2026    │
  │                                  │
  │  ── Accuracy Over Time ──        │
  │  ┌────────────────────────────┐  │
  │  │  100%|                   ╱ │  │  ← Line chart: X = session date, Y = accuracy %
  │  │   80%|        ╱╲     ╱╱   │  │
  │  │   60%|    ╱╱╱    ╲╱╱      │  │
  │  │   40%|╱╱╱                  │  │
  │  │   20%|                     │  │
  │  │      └──────────────────── │  │
  │  │       Jan    Feb           │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  ── Cumulative Confusion ──      │
  │  ── Matrix (All Sessions) ──     │
  │  ┌────────────────────────────┐  │
  │  │      Hap  Sad  Ang  Sur  Neu│ │  ← Color-coded heatmap grid
  │  │ Hap  42    1    0    2    3  │ │     Rows = AI detected
  │  │ Sad   2   28    8    1    5  │ │     Cols = Student guessed
  │  │ Ang   0    6   18    1    3  │ │     Diagonal = correct (green)
  │  │ Sur   1    0    1   22    2  │ │     Off-diagonal = errors (orange/red)
  │  │ Neu   3    2    2    0   30  │ │
  │  └────────────────────────────┘  │
  │                                  │
  │  ── Key Insights ──              │
  │  ┌────────────────────────────┐  │
  │  │ ⚠ Sad↔Angry confusion     │  │  ← Auto-generated from confusion matrix
  │  │   persists (14 total)      │  │     Highlights cells > threshold
  │  │ ✅ Happy recognition is     │  │
  │  │   strong (87.5%)           │  │
  │  │ 📈 Overall trend: +2.3%    │  │  ← Slope of accuracy line
  │  │   per session              │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  ── Session History ──           │
  │  ┌────────────────────────────┐  │
  │  │ Feb 7  • 34 min • 73.9%   │→ │  ← Tap to go to Session Summary for that session
  │  │ Feb 4  • 28 min • 71.2%   │→ │
  │  │ Jan 30 • 40 min • 65.0%   │→ │
  │  │ Jan 27 • 35 min • 58.3%   │→ │
  │  │ ...                        │  │
  │  └────────────────────────────┘  │
  └──────────────────────────────────┘
```

---

## 6. ESP32-CAM FIRMWARE SPECIFICATION

### 6.1 Complete Behavior

```cpp
// Firmware: CueSight ESP32-CAM
// Language: Arduino C++ (PlatformIO or Arduino IDE)
// Libraries required:
//   - esp32-camera (built into ESP32 Arduino core)
//   - WebSocketsServer by Markus Sattler
//   - ArduinoJson (v6+)
//   - U8g2 or Adafruit_SSD1306 (for OLED display)
//   - WiFi (built-in)

/*
HARDWARE CONNECTIONS:
  ESP32-CAM:
    - OV2640 camera (onboard)
    - GPIO 12 → SDA (OLED)
    - GPIO 13 → SCL (OLED)
    - OLED: SSD1306 128x64 (I2C, address 0x3C)

BOOT SEQUENCE:
  1. Initialize Serial (115200 baud) for debug
  2. Initialize camera with QVGA (320x240), JPEG quality 12, 1 frame buffer
     (single buffer to save RAM)
  3. Initialize OLED display, show "CueSight" splash for 2 sec
  4. Initialize WiFi in AP+STA mode:
     - Create SoftAP: SSID="CueSight-Glasses", Password="cuesight123"
     - IP: 192.168.4.1
  5. Start UDP broadcast task (Core 0):
     - Every 2 seconds, broadcast "CUESIGHT|192.168.4.1|81|82" to 192.168.4.255:4210
  6. Start MJPEG HTTP server on port 81:
     - Endpoint: GET /stream
     - Response: multipart/x-mixed-replace
     - Capture frame → send as JPEG part → repeat
     - Target: 10-12 FPS
  7. Start WebSocket server on port 82:
     - Accept 1 client max
     - Handle incoming messages (see below)
  8. Show "Ready" on OLED, then clear

WEBSOCKET MESSAGE HANDLING:
  On receive text message:
    Parse JSON using ArduinoJson (StaticJsonDocument<200>)

    if cmd == "mode":
      if val == "teach":
        currentMode = TEACHING
        displayEnabled = true
        Send: {"status":"ok","mode":"teach"}
      if val == "practice":
        currentMode = PRACTICE
        displayEnabled = false
        clearDisplay()
        Send: {"status":"ok","mode":"practice"}

    if cmd == "emotion":
      if currentMode == TEACHING and displayEnabled:
        emotionLabel = val  // e.g., "Happy"
        displayEmotion(emotionLabel)  // Render on OLED

    if cmd == "clear":
      clearDisplay()
      Send: {"status":"ok"}

    if cmd == "ping":
      Send: {"status":"pong"}

OLED DISPLAY RENDERING:
  displayEmotion(String emotion):
    Clear OLED buffer
    Set font: large, bold (U8g2: u8g2_font_helvB18_tr or similar)
    Map emotion to emoji-like text symbol:
      "Happy"    → ":)" + "Happy"
      "Sad"      → ":(" + "Sad"
      "Angry"    → ">:(" + "Angry"
      "Surprise" → ":O" + "Surprise"
      "Neutral"  → ":|" + "Neutral"
    Center text on 128x64 display
    Send buffer to display

  clearDisplay():
    Clear OLED buffer
    Send buffer (blank screen)

MEMORY MANAGEMENT:
  - Use ONLY 1 frame buffer (fb_count = 1) for camera
  - JPEG quality: 12 (balance quality vs. size)
  - Resolution: QVGA 320x240 (DO NOT use higher — insufficient PSRAM for streaming)
  - WebSocket: max payload 256 bytes (commands are small JSON)
  - Free camera frame buffer immediately after sending via HTTP
  - ArduinoJson: StaticJsonDocument on stack, never dynamic allocation

STREAMING IMPLEMENTATION (port 81):
  HTTP handler for /stream:
    Set response header: "multipart/x-mixed-replace; boundary=frame"
    Loop:
      camera_fb_t *fb = esp_camera_fb_get()
      if fb == NULL: continue
      Send HTTP chunk:
        "--frame\r\n"
        "Content-Type: image/jpeg\r\n"
        "Content-Length: " + fb->len + "\r\n\r\n"
        [fb->buf, fb->len bytes]
        "\r\n"
      esp_camera_fb_return(fb)
      delay(80)  // ~12 FPS throttle

DUAL-CORE TASK ASSIGNMENT:
  Core 0: UDP broadcast task (low priority, runs every 2s)
  Core 1: Main loop — camera capture, HTTP streaming, WebSocket handling
*/
```

---

## 7. ANDROID NETWORK LAYER IMPLEMENTATION DETAILS

### 7.1 UDP Discovery Service

```
Class: UdpDiscoveryService

Behavior:
  - Open DatagramSocket on port 4210
  - Set socket timeout: 5000ms
  - In a coroutine loop:
    - Receive DatagramPacket (max 256 bytes)
    - Decode to String
    - Parse: split by "|" → ["CUESIGHT", ip, streamPort, cmdPort]
    - Validate: first token must be "CUESIGHT"
    - Emit discovered device via SharedFlow/callback
  - Stop listening when connection established or screen left

Data class: DiscoveredDevice(ip: String, streamPort: Int, cmdPort: Int)
```

### 7.2 MJPEG Stream Client

```
Class: MjpegStreamClient

Behavior:
  - Use OkHttp to make GET request to http://<ip>:<streamPort>/stream
  - Parse multipart response manually:
    - Read boundary markers
    - Extract Content-Length header for each part
    - Read exactly Content-Length bytes → raw JPEG data
    - Decode JPEG bytes to android.graphics.Bitmap using BitmapFactory.decodeByteArray()
    - Emit Bitmap via SharedFlow (buffer = 2, DROP_OLDEST)
  - Run in IO dispatcher coroutine
  - On disconnect: emit null, attempt reconnect after 2 seconds, max 3 retries

Interface:
  fun connect(ip: String, port: Int)
  fun disconnect()
  val frameFlow: SharedFlow<Bitmap?>
```

### 7.3 WebSocket Command Client

```
Class: WebSocketCommandClient

Behavior:
  - Use OkHttp WebSocket client
  - Connect to ws://<ip>:<cmdPort>
  - Implement WebSocketListener:
    - onOpen: emit ConnectionState.CONNECTED
    - onMessage: parse JSON response, emit via responseFlow
    - onClosing/onClosed: emit ConnectionState.DISCONNECTED
    - onFailure: emit ConnectionState.ERROR, attempt reconnect
  - Expose suspend functions:
    - sendModeCommand(mode: "teach" | "practice")
    - sendEmotionLabel(emotion: String)
    - sendClear()
    - sendPing()
  - Start ping/pong heartbeat every 10 seconds to detect disconnection

Interface:
  fun connect(ip: String, port: Int)
  fun disconnect()
  suspend fun sendCommand(json: String)
  val connectionState: StateFlow<ConnectionState>
  val responseFlow: SharedFlow<WebSocketResponse>
```

---

## 8. ML PROCESSING PIPELINE (ANDROID)

```
Class: EmotionPipeline

Dependencies:
  - FaceDetectorHelper (wraps ML Kit)
  - EmotionClassifier (wraps TFLite MobileNetV3)
  - EmotionStabilizer (rolling buffer logic)

Method: suspend fun processFrame(bitmap: Bitmap): EmotionResult?

  Step 1: Face Detection
    val faces: List<Face> = faceDetectorHelper.detect(bitmap)
    if (faces.isEmpty()) return null
    val face = faces[0]  // Process primary (largest) face only

  Step 2: Face Crop
    val boundingBox: Rect = face.boundingBox
    // Expand bounding box by 20% on each side for context
    val expandedBox = expandRect(boundingBox, factor = 0.2f, within = bitmap.dimensions)
    val faceCrop: Bitmap = Bitmap.createBitmap(bitmap, expandedBox)

  Step 3: Preprocessing
    val resized: Bitmap = Bitmap.createScaledBitmap(faceCrop, 224, 224, true)
    val inputBuffer: ByteBuffer = bitmapToByteBuffer(resized)
    // Normalize: pixel / 127.5 - 1.0 → range [-1, 1]

  Step 4: Inference
    val probabilities: FloatArray = emotionClassifier.classify(inputBuffer)
    // probabilities = [happy, sad, angry, surprise, neutral]
    val maxIndex = probabilities.indexOfMax()
    val maxConfidence = probabilities[maxIndex]
    val emotionLabel = EMOTION_LABELS[maxIndex]

  Step 5: Confidence Gate
    if (maxConfidence < 0.60f) return null  // Not confident enough

  Step 6: Stabilization
    val stableEmotion = emotionStabilizer.update(face.trackingId, emotionLabel)
    // Returns emotion only if ≥ 3 of last 5 predictions agree
    if (stableEmotion == null) return null

  Step 7: Return
    return EmotionResult(
      emotion = stableEmotion,
      confidence = maxConfidence,
      boundingBox = face.boundingBox,
      faceTrackingId = face.trackingId
    )

Class: EmotionStabilizer
  - Map<Int?, RingBuffer<String>> — keyed by face tracking ID
  - RingBuffer size: 5
  - update(trackingId, emotion): adds to buffer, returns majority if ≥ 3 agree, else null
  - Evict entries not updated for > 3 seconds (face left frame)

Processing rate: Process every 3rd frame from MJPEG stream (at 12 FPS → ~4 inferences/sec)
  This balances responsiveness with CPU/battery usage.
```

---

## 9. SESSION LIFECYCLE STATE MACHINE

```
States:
  IDLE → no active session
  CONNECTING → UDP discovery + WebSocket/MJPEG setup
  TEACHING → Teaching Mode active
  PRACTICING → Practice Mode active
  GUESSING → Practice Mode, guess dialog open (video paused)
  ENDING → End session dialog shown
  SUMMARY → Session ended, viewing summary

Transitions:
  IDLE → CONNECTING:         Teacher selects student, taps Connect
  CONNECTING → TEACHING:     Connection successful, session starts (default mode = Teaching)
  TEACHING → PRACTICING:     Teacher taps Practice toggle
  PRACTICING → TEACHING:     Teacher taps Teaching toggle
  PRACTICING → GUESSING:     Teacher taps "Log Student's Guess"
  GUESSING → PRACTICING:     Teacher selects emotion or cancels
  TEACHING → ENDING:         Teacher taps "End Session"
  PRACTICING → ENDING:       Teacher taps "End Session"
  ENDING → SUMMARY:          Teacher confirms end
  ENDING → TEACHING/PRACTICING: Teacher cancels end
  SUMMARY → IDLE:            Teacher taps "Back to Home"

On unexpected disconnection (WebSocket drops):
  - Show persistent banner: "⚠ Connection lost. Reconnecting..."
  - Attempt auto-reconnect every 3 seconds, up to 5 times
  - If reconnection fails, prompt: "Connection lost. End session or retry?"
  - Session timer continues during disconnection (it's wall-clock time)
  - Emotion pipeline pauses (no frames to process)
```

---

## 10. COMPLETE NAVIGATION GRAPH

```
NavHost(startDestination = "splash") {
    composable("splash") → SplashScreen
    composable("student_select") → StudentSelectScreen
    composable("connection/{studentId}") → ConnectionScreen
    composable("session/{sessionId}") → SessionScreen (Teaching/Practice handled internally)
    composable("session_summary/{sessionId}") → SessionSummaryScreen
    composable("analytics/{studentId}") → AnalyticsDashboardScreen
}

Back stack behavior:
  - From Session: back press shows "End Session?" dialog (prevent accidental exit)
  - From Summary: back goes to student_select (clear session from back stack)
  - From Analytics: back goes to student_select
```

---

## 11. KEY IMPLEMENTATION CONSTRAINTS

```
1. SINGLE APP: There is no separate student app. The teacher holds the phone.
2. PRIVACY: Zero cloud communication. All ML on-device. All data in local Room DB.
   No internet permission needed (only WIFI/LAN).
3. OFFLINE-FIRST: App works entirely on local WiFi between phone and ESP32.
4. SESSION BINDING: From login to logout, all actions are bound to one student_id.
5. PERMISSIONS (Android Manifest):
   - android.permission.INTERNET (for local network sockets only)
   - android.permission.ACCESS_WIFI_STATE
   - android.permission.ACCESS_NETWORK_STATE
   - android.permission.CHANGE_WIFI_STATE (for connecting to ESP32 AP)
6. MIN SDK: 26 (Android 8.0) — covers 95%+ of devices
7. TARGET SDK: 34
8. TFLite model file: assets/mobilenetv3_fer.tflite
   - Must be bundled in APK at build time
   - Load with Interpreter(loadModelFile(context, "mobilenetv3_fer.tflite"))
9. ESP32-CAM module: AI-Thinker ESP32-CAM (most common variant)
   - PSRAM: 4MB (required for camera)
   - Flash: 4MB
   - Camera: OV2640
10. OLED on glasses: SSD1306 128x64 I2C — small, low power, fits on glasses arm
```

---

## 12. ERROR HANDLING SPECIFICATIONS

```
Network Errors:
  - UDP no response after 15 seconds → "No CueSight glasses found. Ensure they are powered on."
  - WebSocket connection refused → "Cannot connect. Check Wi-Fi network."
  - MJPEG stream timeout (no frame for 5 seconds) → Show last frame with "⚠ Stream paused" overlay
  - WebSocket disconnect mid-session → Auto-reconnect with banner notification

ML Errors:
  - No face detected for > 10 seconds in Teaching Mode → Show "No face detected" on phone + glasses OLED
  - TFLite model load failure → Fatal error screen: "Model not found. Please reinstall the app."
  - Low confidence for > 30 seconds → Show "Expression unclear" on OLED (Teaching Mode)

Database Errors:
  - Room insert failure → Retry once, then log error silently (don't interrupt session)
  - Database migration error → Destructive migration with user warning on first launch after update

ESP32 Errors:
  - Camera init failure → Blink onboard LED rapidly, show "CAM ERR" on OLED
  - WiFi AP failure → Restart ESP32 (watchdog)
  - WebSocket client disconnect → Clear OLED, restart broadcast, await new connection
  - Watchdog timer: 30 seconds — if main loop hangs, ESP32 auto-restarts
```

---

## 13. BUILD & DEPENDENCY SPECIFICATION

### Android (build.gradle.kts)

```
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // OkHttp (MJPEG + WebSocket)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Charts (for analytics)
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### ESP32-CAM (platformio.ini)

```ini
[env:esp32cam]
platform = espressif32
board = esp32cam
framework = arduino
monitor_speed = 115200
lib_deps =
    links2004/WebSockets@^2.4.1
    bblanchon/ArduinoJson@^6.21.5
    olikraus/U8g2@^2.35.9
build_flags =
    -DBOARD_HAS_PSRAM
    -mfix-esp32-psram-cache-issue
```

---

This specification covers every screen, every data flow, every message format, every database table, every error case, and every implementation detail needed for an LLM coding agent to generate the complete production-grade codebase for both the Android app and the ESP32-CAM firmware. The architecture prioritizes low latency (WebSocket commands), memory efficiency (single frame buffer, QVGA, small JSON), privacy (all local), and pedagogical effectiveness (strict Teaching/Practice separation with therapeutic data logging).
