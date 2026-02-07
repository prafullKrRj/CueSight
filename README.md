# CueSight - Real-time Emotion Detection System

**AI-powered emotion detection using ESP32-CAM and Android**

## 🎯 Overview

CueSight is a complete emotion detection system that combines ESP32-CAM hardware with an Android application. The system detects facial emotions in real-time, displays them on an OLED screen, and triggers LED responses based on detected emotions.

## ✨ Key Features

- ✅ **Variable IP Address Configuration** - Enter your ESP32-CAM IP address directly in the app
- ✅ **Real-time Frame Streaming** - RAW grayscale frames over WebSocket
- ✅ **Emotion Detection** - Happy, Sad, Sleepy, and Neutral using ML Kit
- ✅ **OLED Display** - Shows detected emotions on ESP32's OLED screen
- ✅ **Smart LED Control** - Blinks LED once when happy emotion is detected
- ✅ **Manual Controls** - LED ON/OFF buttons for manual operation
- ✅ **Performance Optimized** - Processes every 3rd frame for efficiency

## 📋 Requirements

### Hardware
- ESP32-CAM module (with GC2145 camera)
- SSD1306 OLED Display (128x64, I2C)
- LED with resistor (220Ω-1kΩ)
- WiFi network
- Android device

### Software
- Arduino IDE or PlatformIO
- Android Studio (for Android app)
- Adafruit SSD1306 library
- Adafruit GFX library

## 🔧 Hardware Setup

### ESP32-CAM Connections

```
OLED Display:
  SDA → GPIO 14
  SCL → GPIO 15
  VCC → 3.3V
  GND → GND

LED:
  Anode → GPIO 4 (through resistor)
  Cathode → GND

Power:
  5V → VCC
  GND → GND
```

## 📱 Installation

### 1. ESP32-CAM Setup

1. Open `ESP32_Updated_Code.ino` in Arduino IDE
2. Install required libraries:
   - Adafruit SSD1306
   - Adafruit GFX Library
   - ESP32 Camera library
3. Update WiFi credentials:
   ```cpp
   const char* ssid = "YourWiFiName";
   const char* password = "YourPassword";
   ```
4. Select board: "AI Thinker ESP32-CAM"
5. Upload the code
6. Open Serial Monitor (115200 baud) to get IP address

### 2. Android App Setup

1. Open project in Android Studio
2. Sync Gradle files
3. Connect Android device or start emulator
4. Build and run the app

## 🚀 Usage

### Step-by-Step Guide

1. **Power on ESP32-CAM**
   - Wait for WiFi connection
   - Note IP address from OLED or Serial Monitor

2. **Open CueSight App**
   - Enter ESP32-CAM IP address (e.g., 192.168.1.100)
   - Press **START** button

3. **Detect Emotions**
   - Position your face in front of phone camera
   - App will detect and display emotions
   - OLED on ESP32 shows detected emotion
   - LED blinks once when you smile (Happy emotion)

4. **Manual LED Control**
   - Use **LED ON** button to turn LED on
   - Use **LED OFF** button to turn LED off

5. **Stop Streaming**
   - Press **STOP** button to end session

## 🧠 Emotion Detection

The system classifies emotions based on ML Kit face analysis:

| Emotion | Criteria | LED Action | Display |
|---------|----------|------------|---------|
| Happy 😊 | Smiling > 70% | Blink once | Shows on OLED |
| Sleepy 😴 | Both eyes closed | None | Shows on OLED |
| Sad 😢 | Smiling < 20% | None | Shows on OLED |
| Neutral 😐 | Default state | None | Shows on OLED |

## 🔌 WebSocket Protocol

### Frame Stream
```
ws://<ESP32_IP>:8888
Binary payload: RAW 160x120 grayscale frame (19,200 bytes)
```

### Command Messages (Text)
```
MODE:TEACHING
MODE:PRACTICE
MODE:IDLE
STREAM:START
STREAM:STOP
EMOTION:Happy
EMOTION:HIDDEN
FEEDBACK:CORRECT
FEEDBACK:WRONG
FEEDBACK:Sad
LED:ON
LED:OFF
```

## 📊 System Flow

```
Android Frame → ML Kit Face Detection → Emotion Classification
                                              ↓
                                 Send to ESP32 via WebSocket
                                              ↓
                        ┌─────────────────────┴─────────────────────┐
                        ↓                                             ↓
                 Update OLED Display                      If Happy: Blink LED
```

## 🐛 Troubleshooting

### No Frames
- ✓ Check IP address is correct
- ✓ Ensure both devices on same WiFi network
- ✓ Verify ESP32 shows "WiFi Connected" on OLED
- ✓ Verify WebSocket port 8888 is reachable

### Emotions Not Displayed on OLED
- ✓ Check I2C connections (SDA=14, SCL=15)
- ✓ Verify OLED address is 0x3C
- ✓ Check Serial Monitor for "Emotion received:" messages
- ✓ Ensure POST requests are reaching ESP32

### LED Not Blinking
- ✓ Check LED connection to GPIO 4
- ✓ Verify LED polarity (anode to GPIO)
- ✓ Test with manual LED ON/OFF buttons
- ✓ Check Serial Monitor for "LED BLINK" messages

### App Crashes
- ✓ Grant camera permissions
- ✓ Check internet permission in manifest
- ✓ Verify valid IP address format
- ✓ Check Logcat for error messages

## 📁 Project Structure

```
CueSight/
├── app/
│   └── src/main/java/com/cuegight/cuesight/
│       ├── MainActivity.kt          # Main Android app logic
│       └── CueSight.kt             # Application setup
├── ESP32_Updated_Code.ino          # ESP32-CAM firmware
├── IMPLEMENTATION_GUIDE.md         # Detailed implementation guide
├── TESTING_GUIDE.md                # Testing procedures
├── ARCHITECTURE.md                 # System architecture docs
└── README.md                       # This file
```

## 🔐 Security Notes

⚠️ **Important**: This is a local network prototype
- WebSocket only (not encrypted)
- No authentication
- Suitable for trusted networks only

For production use:
- Implement HTTPS/TLS
- Add authentication tokens
- Validate all inputs
- Use mDNS for discovery

## 📈 Performance

- **Frame Rate**: 15-20 FPS (grayscale 160x120)
- **Detection Rate**: 3-5 FPS (every 3rd frame)
- **Network Latency**: 20-50ms (local WiFi)
- **Detection Latency**: 100-300ms (ML Kit processing)
- **OLED Update**: <100ms
- **LED Blink**: 200ms pulse

## 🛠️ Technology Stack

### Android
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Networking**: OkHttp3
- **ML**: Google ML Kit Vision API
- **Async**: Kotlin Coroutines

### ESP32-CAM
- **Language**: C++
- **Framework**: Arduino/ESP-IDF
- **Display**: Adafruit SSD1306
- **Web Server**: esp_http_server
- **Camera**: ESP32 Camera Driver

## 📖 Documentation

- [Implementation Guide](IMPLEMENTATION_GUIDE.md) - Detailed setup and implementation
- [Testing Guide](TESTING_GUIDE.md) - Step-by-step testing procedures
- [Architecture](ARCHITECTURE.md) - System design and data flow

## 🎓 Project Details

**Project Name**: CueSight  
**Type**: BE Final Year Project  
**Domain**: IoT + AI + Mobile Development  
**Purpose**: Real-time emotion detection and feedback system

## 📝 License

This project is for educational purposes.

## 🤝 Contributing

This is an academic project. For improvements or questions, please create an issue.

## 📧 Support

For issues or questions:
1. Check the [Testing Guide](TESTING_GUIDE.md)
2. Review the [Troubleshooting](#-troubleshooting) section
3. Check Serial Monitor and Logcat for error messages

---

**Built with ❤️ for emotion recognition and IoT integration**
