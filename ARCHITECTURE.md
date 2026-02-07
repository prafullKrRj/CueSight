# CueSight System Architecture

## Component Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     ANDROID APP (CueSight)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              User Interface (Compose)                     │   │
│  │  • IP Address Input Field                                │   │
│  │  • Frame Stream Display (Card)                           │   │
│  │  • Emotion Display Card                                  │   │
│  │  • START/STOP Buttons                                    │   │
│  │  • LED ON/OFF Buttons                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↕                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              MainActivity Logic                           │   │
│  │  • Stream Management (WebSocket)                         │   │
│  │  • RAW Frame Parsing                                     │   │
│  │  • ML Kit Face Detection                                 │   │
│  │  • Emotion Classification                                │   │
│  │  • WebSocket Command Messages                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↕
                     WiFi Network (WebSocket)
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                   ESP32-CAM HARDWARE MODULE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────┐      ┌──────────────────┐                │
│  │   GC2145 Camera  │      │  WebSocket Server│                │
│  │   160x120        │  →   │  Port 8888       │                │
│  │   Grayscale      │      │  RAW Frames      │                │
│  └──────────────────┘      └──────────────────┘                │
│                                     ↕                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Request Handler                              │  │
│  │  • STREAM:START/STOP → Frame Streaming                   │  │
│  │  • EMOTION:*         → OLED Display                      │  │
│  │  • FEEDBACK:*        → Practice Feedback                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↕                                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │  LED (GPIO 4)  │  │ OLED Display    │  │  WiFi Module   │  │
│  │  • Blink Happy │  │ SSD1306 128x64  │  │  Station Mode  │  │
│  │  • Manual Ctrl │  │ I2C: 14,15      │  │                │  │
│  │  • Heartbeat   │  │ Shows Emotions  │  │                │  │
│  └────────────────┘  └─────────────────┘  └────────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

### 1. Frame Streaming Flow
```
ESP32-CAM Camera
      ↓
Capture Frame (160x120 Grayscale)
      ↓
Send RAW bytes over WebSocket
      ↓
Android App (OkHttp WebSocket)
      ↓
Convert RAW bytes to Bitmap
      ↓
Display in UI
```

### 2. Emotion Detection Flow
```
Video Frame (every 3rd frame)
      ↓
ML Kit InputImage
      ↓
Face Detection
      ↓
Extract Probabilities:
  • smiling
  • leftEyeOpen
  • rightEyeOpen
      ↓
Classify Emotion:
  • Happy (smiling > 0.7)
  • Sleepy (eyes < 0.3)
  • Sad (smiling < 0.2)
  • Neutral (default)
      ↓
Display in App UI
```

### 3. Emotion Communication Flow
```
Emotion Detected
      ↓
WebSocket: EMOTION:Happy
      ↓
ESP32 Receives
      ↓
Update OLED Display
```
```

### 4. Manual LED Control Flow
```
User Presses Button
      ↓
"LED ON" or "LED OFF"
      ↓
WebSocket: LED:ON / LED:OFF
      ↓
ESP32 Receives
      ↓
digitalWrite(LED_PIN, HIGH/LOW)
      ↓
LED State Changes
```

## Communication Protocol

### Command Messages

| Type | Payload | Purpose |
|------|---------|---------|
| Text | MODE:TEACHING | Set teaching mode |
| Text | MODE:PRACTICE | Set practice mode |
| Text | MODE:IDLE | Set idle mode |
| Text | STREAM:START | Begin frame streaming |
| Text | STREAM:STOP | Stop frame streaming |
| Text | EMOTION:Happy | Show detected emotion |
| Text | EMOTION:HIDDEN | Hide emotion in practice |
| Text | FEEDBACK:CORRECT | Show correct feedback |
| Text | FEEDBACK:WRONG | Show wrong feedback |
| Text | FEEDBACK:Sad | Show correct answer |
| Text | LED:ON | Turn LED on |
| Text | LED:OFF | Turn LED off |
| Binary | RAW bytes | 160x120 grayscale frame |

### Network Requirements
- Both devices on same WiFi network
- Port 8888 open on ESP32
- WebSocket (not encrypted)
- Low latency (<50ms local network)

## State Machine

### Android App States
```
[IDLE] ──START──> [STREAMING] ──STOP──> [IDLE]
                      │
                      ├──> Frame Processing
                      ├──> Emotion Detection
                      └──> Send Commands
```

### ESP32 States
```
[BOOT] ──Init──> [WIFI_CONNECT] ──Success──> [READY]
                                                 │
                     ┌───────────────────────────┤
                     │                           │
                [STREAMING]                 [PROCESSING]
                     │                           │
                     └──> Capture Frame          │
                                                 │
                                    ┌────────────┴────────────┐
                                    │                         │
                              [SHOW_EMOTION]            [BLINK_LED]
```

## Emotion Classification Logic

```
Input: Face object from ML Kit

if smiling > 0.7:
    emotion = "Happy 😊"
    action = "BLINK LED"
    
elif leftEyeOpen < 0.3 AND rightEyeOpen < 0.3:
    emotion = "Sleepy 😴"
    action = "Display only"
    
elif smiling < 0.2:
    emotion = "Sad 😢"
    action = "Display only"
    
else:
    emotion = "Neutral 😐"
    action = "Display only"

Output: emotion string + conditional LED blink
```

## Hardware Connections

```
ESP32-CAM Module
├── Camera Module (GC2145) → CSI Interface
├── OLED Display
│   ├── SDA → GPIO 14
│   ├── SCL → GPIO 15
│   ├── VCC → 3.3V
│   └── GND → GND
├── LED
│   ├── Anode → GPIO 4 (with resistor)
│   └── Cathode → GND
└── Power
    ├── 5V → VCC
    └── GND → GND
```

## Performance Characteristics

| Metric | Value | Notes |
|--------|-------|-------|
| Frame Rate | ~15-20 FPS | Grayscale 160x120 |
| Detection Rate | ~3-5 FPS | Every 3rd frame |
| Network Latency | 20-50ms | Local WiFi |
| Detection Latency | 100-300ms | ML Kit processing |
| OLED Update | <100ms | I2C communication |
| LED Blink | 200ms | Single pulse |
| Heartbeat | 10s | System alive check |

## Technology Stack

### Android (Kotlin)
- **UI Framework**: Jetpack Compose + Material3
- **Networking**: OkHttp3
- **ML Framework**: ML Kit Vision API
- **Concurrency**: Kotlin Coroutines
- **State Management**: Compose State

### ESP32-CAM (C++)
- **Framework**: Arduino/ESP-IDF
- **WebSocket Server**: WebSocketsServer
- **Display**: Adafruit SSD1306
- **Camera**: ESP32 Camera Driver
- **Network**: ESP32 WiFi

## Security Considerations

⚠️ **Note**: This is a local network prototype
- No authentication implemented
- WebSocket only (unencrypted)
- IP addresses hardcoded or user-entered
- Suitable for trusted local networks only

For production:
- Implement WSS/TLS
- Add authentication tokens
- Use mDNS for discovery
- Implement rate limiting
- Add input validation
