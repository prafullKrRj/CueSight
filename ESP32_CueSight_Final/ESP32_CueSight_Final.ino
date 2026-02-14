/*
 * ============================================================================
 * CueSight ESP32-CAM - FIXED Streaming
 * ============================================================================
 */

#include "esp_camera.h"
#include "esp_timer.h"
#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// ============================================================================
// CAMERA MODEL
// ============================================================================
#define CAMERA_MODEL_AI_THINKER

#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27
#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22

// ============================================================================
// PIN DEFINITIONS
// ============================================================================
#define I2C_SDA 14
#define I2C_SCL 15
#define LED_PIN 4

// ============================================================================
// DISPLAY CONFIGURATION
// ============================================================================
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define OLED_ADDRESS 0x3C

// ============================================================================
// NETWORK CONFIGURATION
// ============================================================================
const char* ssid = "ESP32-CAM";
const char* password = "12345678";

#define FRAME_PORT 81
#define COMMAND_PORT 82
#define MAX_COMMAND_LENGTH 128
#define MAX_COMMANDS_PER_LOOP 3
#define SOCKET_TIMEOUT_SEC 3

// ============================================================================
// FPS CONTROL - Reduced to 4-5 FPS for memory efficiency
// ============================================================================
#define TARGET_FPS 5
#define FRAME_INTERVAL_MS (1000 / TARGET_FPS)  // 200ms

// ============================================================================
// EMOTION/FEEDBACK CODES - STORED IN PROGMEM (Flash, not RAM)
// ============================================================================
// Emotion codes sent from Android (uint8_t)
#define EMOTION_HAPPY       0x01
#define EMOTION_SAD         0x02
#define EMOTION_ANGRY       0x03
#define EMOTION_SURPRISED   0x04
#define EMOTION_NEUTRAL     0x05
#define EMOTION_NO_FACE     0x06
#define EMOTION_HIDDEN      0x07

// Emotion display strings (stored in Flash via PROGMEM)
const char EMOTION_STR_HAPPY[] PROGMEM = "Happy";
const char EMOTION_STR_SAD[] PROGMEM = "Sad";
const char EMOTION_STR_ANGRY[] PROGMEM = "Angry";
const char EMOTION_STR_SURPRISED[] PROGMEM = "Surprised";
const char EMOTION_STR_NEUTRAL[] PROGMEM = "Neutral";
const char EMOTION_STR_NO_FACE[] PROGMEM = "No face";
const char EMOTION_STR_HIDDEN[] PROGMEM = "?";
const char EMOTION_STR_WAITING[] PROGMEM = "Waiting...";

// Array of pointers to emotion strings (in PROGMEM)
const char* const EMOTION_STRINGS[] PROGMEM = {
  nullptr,                  // 0x00 unused
  EMOTION_STR_HAPPY,        // 0x01
  EMOTION_STR_SAD,          // 0x02
  EMOTION_STR_ANGRY,        // 0x03
  EMOTION_STR_SURPRISED,    // 0x04
  EMOTION_STR_NEUTRAL,      // 0x05
  EMOTION_STR_NO_FACE,      // 0x06
  EMOTION_STR_HIDDEN        // 0x07
};

// Helper function to get emotion string from PROGMEM
void getEmotionString(uint8_t code, char* buffer, size_t bufferSize) {
  if (bufferSize == 0) return;  // Safety check
  
  if (code == 0 || code > 7) {
    strncpy_P(buffer, EMOTION_STR_WAITING, bufferSize - 1);
  } else {
    const char* emotionPtr = (const char*)pgm_read_ptr(&EMOTION_STRINGS[code]);
    strncpy_P(buffer, emotionPtr, bufferSize - 1);
  }
  buffer[bufferSize - 1] = '\0';  // Always null-terminate at last position
}

// ============================================================================
// GLOBALS
// ============================================================================
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiServer frameServer(FRAME_PORT);
WiFiServer commandServer(COMMAND_PORT);
WiFiClient frameClient;
WiFiClient commandClient;

enum SessionMode {
  MODE_IDLE,
  MODE_TEACHING,
  MODE_PRACTICE
};

// Current emotion stored as code (uint8_t), not String - saves RAM
uint8_t currentEmotionCode = 0;
SessionMode currentMode = MODE_IDLE;
bool streamingActive = false;
bool lowMemoryLock = false;

uint32_t frameCount = 0;
uint32_t lastFPSCheck = 0;
float currentFPS = 0.0;
unsigned long lastFrameTime = 0;

uint32_t lastReconnectAttempt = 0;
bool wifiWasDisconnected = false;

// Static buffers to avoid dynamic allocation
char displayBuffer[64];
char commandBuffer[MAX_COMMAND_LENGTH + 1];  // Static buffer, no String
uint16_t commandBufferPos = 0;

// OLED update throttling - max 1Hz as per requirements
uint8_t pendingEmotionCode = 0;
unsigned long lastOLEDUpdate = 0;
#define OLED_UPDATE_INTERVAL_MS 1000  // Changed from 2000ms to 1000ms (1Hz max)

// ============================================================================
// FAST OLED UPDATE
// ============================================================================
void fastOLEDUpdate(const char* line1, const char* line2, uint8_t textSize) {
  display.clearDisplay();
  display.setTextSize(textSize);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  if (line1) display.println(line1);
  if (line2) {
    display.setCursor(0, textSize == 1 ? 10 : 20);
    display.println(line2);
  }
  display.display();
}

// Helper function to get mode label from PROGMEM
const char* modeLabel() {
  switch (currentMode) {
    case MODE_TEACHING:  return PSTR("TEACH");
    case MODE_PRACTICE:  return PSTR("PRACTICE");
    default:             return PSTR("IDLE");
  }
}

// Display emotion using code (fetches string from PROGMEM on-the-fly)
void showEmotionDisplay(uint8_t emotionCode) {
  char emotionStr[16];
  getEmotionString(emotionCode, emotionStr, sizeof(emotionStr));
  
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("Emotion:"));  // F() macro stores string in Flash
  display.setTextSize(2);
  display.setCursor(0, 16);
  display.println(emotionStr);
  display.setTextSize(1);
  display.setCursor(0, 40);
  display.printf("FPS: %.1f", currentFPS);
  display.setCursor(0, 50);
  display.printf_P(PSTR("Mode: %s"), modeLabel());
  display.display();
}

// Show feedback text (limited to 16 chars to fit display and prevent buffer overflow)
void showFeedbackDisplay(const char* feedbackText) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("Feedback:"));
  display.setTextSize(2);
  display.setCursor(0, 16);
  // Safely truncate feedback to fit display
  char truncated[17];
  size_t len = strlen(feedbackText);
  if (len > 16) len = 16;
  memcpy(truncated, feedbackText, len);
  truncated[len] = '\0';
  display.println(truncated);
  display.setTextSize(1);
  display.setCursor(0, 50);
  display.printf_P(PSTR("Mode: %s"), modeLabel());
  display.display();
}

// ============================================================================
// TCP COMMAND HANDLER - Optimized for memory efficiency
// ============================================================================
// Supports both:
// 1. NEW: Binary emotion codes (single byte: 0x01-0x07)
// 2. LEGACY: Text commands (for backward compatibility)
void handleCommand(const char* message, uint16_t len) {
  if (len == 0) return;

  // Check if it's a binary emotion code (single byte, 0x01-0x07)
  if (len == 1) {
    uint8_t code = (uint8_t)message[0];
    if (code >= 0x01 && code <= 0x07) {
      pendingEmotionCode = code;
      Serial.printf("[TCP] 📥 Received emotion code: 0x%02X\n", code);
      return;
    }
  }

  // Parse text commands (legacy support)
  Serial.printf("[TCP] 📥 Received command: %s\n", message);

  if (strncmp(message, "MODE:", 5) == 0) {
    const char* mode = message + 5;
    if (strcasecmp(mode, "TEACHING") == 0) {
      currentMode = MODE_TEACHING;
      Serial.println(F("[MODE] ✅ Switched to TEACHING mode"));
      fastOLEDUpdate("Mode", "Teaching", 1);
    } else if (strcasecmp(mode, "PRACTICE") == 0) {
      currentMode = MODE_PRACTICE;
      Serial.println(F("[MODE] ✅ Switched to PRACTICE mode"));
      fastOLEDUpdate("Mode", "Practice", 1);
    } else {
      currentMode = MODE_IDLE;
      streamingActive = false;
      Serial.println(F("[MODE] ✅ Switched to IDLE mode"));
      fastOLEDUpdate("Mode", "Idle", 1);
    }
  } else if (strcmp(message, "STREAM:START") == 0) {
    if (!lowMemoryLock) {
      streamingActive = true;
      frameCount = 0;
      lastFPSCheck = millis();
      lastFrameTime = millis() + 300;  // 300ms warmup before first frame
      Serial.println(F("[TCP] ✅ Streaming started (300ms warmup)"));
    } else {
      Serial.println(F("[TCP] ❌ Cannot start streaming - LOW MEMORY LOCK"));
    }
  } else if (strcmp(message, "STREAM:STOP") == 0) {
    streamingActive = false;
    Serial.println(F("[TCP] ⏹️ Streaming stopped"));
  } else if (strncmp(message, "EMOTION:", 8) == 0) {
    const char* emotion = message + 8;
    
    // Map string emotion to code for efficient storage
    uint8_t code = 0;
    if (strcmp(emotion, "HIDDEN") == 0 || strcmp(emotion, "?") == 0) {
      code = EMOTION_HIDDEN;
    } else if (strncmp(emotion, "Happy", 5) == 0) {
      code = EMOTION_HAPPY;
    } else if (strncmp(emotion, "Sad", 3) == 0) {
      code = EMOTION_SAD;
    } else if (strncmp(emotion, "Angry", 5) == 0) {
      code = EMOTION_ANGRY;
    } else if (strncmp(emotion, "Surprised", 9) == 0) {
      code = EMOTION_SURPRISED;
    } else if (strncmp(emotion, "Neutral", 7) == 0) {
      code = EMOTION_NEUTRAL;
    } else if (strncmp(emotion, "No face", 7) == 0) {
      code = EMOTION_NO_FACE;
    }
    
    if (code != 0) {
      pendingEmotionCode = code;
    }
    // Note: OLED update deferred to main loop to avoid I2C blocking during frame sends
  } else if (strncmp(message, "FEEDBACK:", 9) == 0) {
    const char* feedback = message + 9;
    showFeedbackDisplay(feedback);
  } else if (strncmp(message, "LED:", 4) == 0) {
    const char* command = message + 4;
    if (strcasecmp(command, "ON") == 0) {
      digitalWrite(LED_PIN, HIGH);
    } else if (strcasecmp(command, "OFF") == 0) {
      digitalWrite(LED_PIN, LOW);
    } else if (strcasecmp(command, "BLINK") == 0) {
      digitalWrite(LED_PIN, HIGH);
      delay(150);
      digitalWrite(LED_PIN, LOW);
    }
  }
  
  yield();  // Yield to prevent watchdog reset during command processing
}

// ============================================================================
// >>> SAFE SEND — Chunked write with retry logic <<<
// Sends data in 512-byte chunks with retries and 3-second timeout
// ============================================================================
bool safeSend(WiFiClient& client, const uint8_t* data, size_t len) {
  if (!client.connected()) return false;
  
  size_t sent = 0;
  unsigned long startMs = millis();
  
  while (sent < len) {
    if (!client.connected()) {
      Serial.printf("[TCP] Client disconnected during send (sent %u/%u)\n", sent, len);
      return false;
    }
    
    if (millis() - startMs > 3000) {
      Serial.printf("[TCP] Send timeout after %u/%u bytes\n", sent, len);
      return false;
    }

    size_t chunk = min(len - sent, (size_t)512);  // send in 512-byte chunks
    size_t written = client.write(data + sent, chunk);
    
    if (written > 0) {
      sent += written;
    } else {
      // Write buffer full, brief yield and retry
      delay(1);
    }
  }
  return true;
}

// ============================================================================
// CAMERA INITIALIZATION - Memory optimized configuration
// ============================================================================
bool initCamera() {
  camera_config_t config;

  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;

  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  // MEMORY OPTIMIZATION: Smaller frame size (160x120 vs 320x240)
  // Reduces memory usage by ~75% while maintaining face detection capability
  config.frame_size = FRAMESIZE_QQVGA;    // 160x120 (was QVGA 320x240)
  
  // MEMORY OPTIMIZATION: Lower JPEG quality (25 vs 35)
  // Quality 25 produces ~1-2KB frames vs ~3-4KB at quality 35
  // Still sufficient for emotion detection, dramatically reduces bandwidth
  config.jpeg_quality = 25;               // Lower quality = smaller frames
  
  config.grab_mode = CAMERA_GRAB_LATEST;  // Always get latest frame

  // CRITICAL MEMORY OPTIMIZATION: Use minimal frame buffers
  // - With PSRAM: Use 2 buffers for smoother capture (one capturing, one being read)
  // - Without PSRAM: Use ONLY 1 buffer to conserve precious DRAM
  if (psramFound()) {
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.fb_count = 2;  // 2 buffers with PSRAM for double-buffering
    Serial.println(F("[CAM] Using PSRAM - 2 frame buffers"));
  } else {
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.fb_count = 1;  // ONLY 1 buffer without PSRAM - saves ~20-30KB RAM
    Serial.println(F("[CAM] No PSRAM - 1 frame buffer (memory critical mode)"));
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[CAM] Init failed: 0x%x\n", err);
    fastOLEDUpdate("Camera FAIL", nullptr, 1);
    return false;
  }

  // Camera sensor configuration for optimal low-light performance
  sensor_t *s = esp_camera_sensor_get();
  s->set_framesize(s, FRAMESIZE_QQVGA);   // Confirm frame size
  s->set_brightness(s, 1);      // Slightly increase brightness
  s->set_contrast(s, 0);        // Normal contrast
  s->set_exposure_ctrl(s, 1);   // Enable auto-exposure
  s->set_aec2(s, 1);            // Enable AEC algorithm
  s->set_whitebal(s, 1);        // Enable auto white balance
  s->set_awb_gain(s, 1);        // Enable AWB gain
  s->set_gain_ctrl(s, 1);       // Enable auto gain
  s->set_special_effect(s, 0);  // No special effects
  s->set_lenc(s, 1);            // Enable lens correction

  Serial.println(F("[CAM] Initialized: JPEG at QQVGA (160x120), Quality 25"));
  Serial.printf("[CAM] Frame buffers: %d, Location: %s\n", 
                config.fb_count, 
                config.fb_location == CAMERA_FB_IN_PSRAM ? "PSRAM" : "DRAM");
  return true;
}

// ============================================================================
// WIFI CONNECTION
// ============================================================================
bool connectWiFi() {
  WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) {
    if (event == WIFI_EVENT_AP_STADISCONNECTED) {
      Serial.println("[WiFi] Client disconnected from AP");
    } else if (event == WIFI_EVENT_AP_STACONNECTED) {
      Serial.println("[WiFi] Client connected to AP");
    }
  });

  WiFi.mode(WIFI_AP);
  WiFi.setSleep(false);
  fastOLEDUpdate("Creating AP...", ssid, 1);

  bool success = WiFi.softAP(ssid, password);
  if (!success) {
    Serial.println("\n[WiFi] AP creation FAILED!");
    fastOLEDUpdate("WiFi FAIL", "AP Error", 1);
    return false;
  }

  IPAddress IP = WiFi.softAPIP();
  Serial.printf("\n[WiFi] AP created: %s\n", ssid);
  Serial.printf("[WiFi] IP: %s\n", IP.toString().c_str());

  snprintf(displayBuffer, sizeof(displayBuffer), "IP:%s", IP.toString().c_str());
  fastOLEDUpdate(displayBuffer, "AP Ready", 1);
  delay(2000);
  return true;
}

// ============================================================================
// SETUP
// ============================================================================
void setup() {
  Serial.begin(115200);
  Serial.println("\n\n========================================");
  Serial.println("CueSight ESP32-CAM (FIXED)");
  Serial.println("========================================\n");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Wire.begin(I2C_SDA, I2C_SCL);
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS)) {
    Serial.println("[OLED] Init failed");
  } else {
    Serial.println("[OLED] Init OK");
    display.clearDisplay();
    display.setTextSize(2);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("CueSight");
    display.setTextSize(1);
    display.setCursor(0, 25);
    display.println("Starting...");
    display.display();
  }

  Serial.println("[CAM] Initializing...");
  if (!initCamera()) {
    Serial.println("[CAM] FATAL ERROR");
    while (1) { digitalWrite(LED_PIN, HIGH); delay(100); digitalWrite(LED_PIN, LOW); delay(100); }
  }

  Serial.println("[WiFi] Connecting...");
  if (!connectWiFi()) {
    Serial.println("[WiFi] FATAL ERROR");
    while (1) { digitalWrite(LED_PIN, HIGH); delay(500); digitalWrite(LED_PIN, LOW); delay(500); }
  }

  frameServer.begin();
  frameServer.setNoDelay(true);
  commandServer.begin();
  commandServer.setNoDelay(true);
  Serial.printf("[TCP] Frame server started on port %d\n", FRAME_PORT);
  Serial.printf("[TCP] Command server started on port %d\n", COMMAND_PORT);

  lastFPSCheck = millis();
  lastReconnectAttempt = millis();
  lastOLEDUpdate = millis();

  Serial.println("\n========================================");
  Serial.println("System Ready");
  Serial.printf("Frame streaming: %s:%d\n", WiFi.softAPIP().toString().c_str(), FRAME_PORT);
  Serial.printf("Commands: %s:%d\n", WiFi.softAPIP().toString().c_str(), COMMAND_PORT);
  Serial.printf("Target FPS: %d\n", TARGET_FPS);
  Serial.println("========================================\n");
  Serial.printf("[MEM] Free heap: %u bytes\n", esp_get_free_heap_size());

  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("Ready!");
  display.println(WiFi.softAPIP().toString());
  display.println();
  display.println("Waiting for");
  display.println("Android app...");
  display.display();
}

// ============================================================================
// MAIN LOOP — TWO-PORT ARCHITECTURE
// ============================================================================
void loop() {
  if (wifiWasDisconnected) {
    wifiWasDisconnected = false;
    fastOLEDUpdate("WiFi OK", "Waiting...", 1);
  }

  // ===== ACCEPT FRAME CLIENT (port 81) =====
  WiFiClient incomingFrameClient = frameServer.available();
  if (incomingFrameClient) {
    if (frameClient && frameClient.connected()) {
      Serial.println("[TCP] ⚠️ Replacing existing frame client");
      frameClient.stop();
    }
    frameClient = incomingFrameClient;
    frameClient.setNoDelay(true);
    frameClient.setTimeout(SOCKET_TIMEOUT_SEC);
    Serial.printf("[TCP] ✅ Frame client connected from %s\n",
                  frameClient.remoteIP().toString().c_str());
  }

  if (frameClient && !frameClient.connected()) {
    Serial.println("[TCP] ❌ Frame client disconnected");
    frameClient.stop();
    streamingActive = false;
  }

  // ===== ACCEPT COMMAND CLIENT (port 82) =====
  WiFiClient incomingCmdClient = commandServer.available();
  if (incomingCmdClient) {
    if (commandClient && commandClient.connected()) {
      Serial.println(F("[TCP] ⚠️ Replacing existing command client"));
      commandClient.stop();
    }
    commandBufferPos = 0;  // Reset static buffer
    memset(commandBuffer, 0, sizeof(commandBuffer));  // Clear buffer
    commandClient = incomingCmdClient;
    commandClient.setNoDelay(true);
    commandClient.setTimeout(SOCKET_TIMEOUT_SEC);
    Serial.printf("[TCP] ✅ Command client connected from %s\n",
                  commandClient.remoteIP().toString().c_str());
  }

  if (commandClient && !commandClient.connected()) {
    Serial.println(F("[TCP] ❌ Command client disconnected"));
    commandClient.stop();
    commandBufferPos = 0;  // Reset static buffer position
    memset(commandBuffer, 0, sizeof(commandBuffer));  // Clear buffer
  }

  // ===== PROCESS LINE-DELIMITED COMMANDS (port 82) =====
  // Limit to MAX_COMMANDS_PER_LOOP commands per loop iteration to prevent command flood
  // Uses static buffer to avoid String allocations
  int commandsProcessed = 0;
  while (commandClient && commandClient.connected() && commandClient.available() && commandsProcessed < MAX_COMMANDS_PER_LOOP) {
    char c = (char)commandClient.read();
    if (c == '\n') {
      // Null-terminate and process command
      commandBuffer[commandBufferPos] = '\0';
      handleCommand(commandBuffer, commandBufferPos);
      commandBufferPos = 0;
      commandsProcessed++;
    } else if (c != '\r') {
      // Add to buffer if space available
      if (commandBufferPos < MAX_COMMAND_LENGTH) {
        commandBuffer[commandBufferPos++] = c;
      } else {
        // Buffer overflow protection
        Serial.println(F("[TCP] Command too long, dropping buffer"));
        commandBufferPos = 0;
      }
    }
    yield();  // Yield during command processing to prevent watchdog
  }

  // ===== DEFERRED OLED UPDATE (max once every 1 second = 1Hz) =====
  // This prevents I2C bus blocking during frame transmission
  if (pendingEmotionCode != 0 && (millis() - lastOLEDUpdate) > OLED_UPDATE_INTERVAL_MS) {
    currentEmotionCode = pendingEmotionCode;
    showEmotionDisplay(currentEmotionCode);
    pendingEmotionCode = 0;
    lastOLEDUpdate = millis();
  }

  // ===== TCP RAW FRAME STREAMING (port 81) =====
  if (streamingActive && frameClient && frameClient.connected()) {

    // FPS throttle: send one frame every 200ms (5 FPS) for memory efficiency
    unsigned long now = millis();
    if (now - lastFrameTime < FRAME_INTERVAL_MS) {
      delay(1);  // yield CPU instead of busy-looping
      return;
    }
    lastFrameTime = now;

    // Capture frame from camera
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println(F("[CAM] ❌ Capture failed"));
      delay(50);
      return;
    }

    // Send [4-byte big-endian length][JPEG bytes]
    uint32_t frameLen = fb->len;
    uint8_t header[4] = {
      (uint8_t)((frameLen >> 24) & 0xFF),
      (uint8_t)((frameLen >> 16) & 0xFF),
      (uint8_t)((frameLen >> 8) & 0xFF),
      (uint8_t)(frameLen & 0xFF)
    };

    // Use safeSend() for chunked reliable write with timeout protection
    bool ok = safeSend(frameClient, header, 4);
    if (ok) {
      ok = safeSend(frameClient, fb->buf, fb->len);
    }

    // CRITICAL: Return frame buffer IMMEDIATELY after send to free memory
    esp_camera_fb_return(fb);

    if (!ok) {
      Serial.println(F("[TCP] ❌ Write failed, disconnecting frame client"));
      frameClient.stop();
      streamingActive = false;
      return;
    }

    // FPS calculation and logging
    frameCount++;
    if (frameCount % 50 == 0) {
      unsigned long elapsed = millis() - lastFPSCheck;
      currentFPS = (50.0 * 1000.0) / elapsed;
      uint32_t freeHeap = esp_get_free_heap_size();
      Serial.printf("[TCP] 📺 FPS: %.1f | Frames: %lu | Heap: %u | Frame: %u bytes\n",
                    currentFPS, frameCount, freeHeap, frameLen);
      lastFPSCheck = millis();
    }

    // ENHANCED MEMORY MONITORING - Check every 20 frames (every ~4 seconds at 5 FPS)
    // This is more frequent than before (was every 200 frames) to catch memory issues faster
    if (frameCount % 20 == 0) {
      uint32_t freeHeap = esp_get_free_heap_size();
      
      // CRITICAL: Stop streaming if free heap drops below 25KB (per requirements)
      if (freeHeap < 25000) {
        if (!lowMemoryLock) {
          lowMemoryLock = true;
          streamingActive = false;
          Serial.printf("❌ LOW MEMORY DETECTED: %u bytes (threshold: 25000)\n", freeHeap);
          Serial.println(F("[MEM] Stopping stream to prevent crash"));
          
          if (frameClient && frameClient.connected()) {
            frameClient.stop();
          }
          
          // Show error without long blocking delay
          fastOLEDUpdate("Low Memory!", "< 25KB free", 2);
          delay(1000);  // Reduced from 3000ms to 1000ms to stay responsive
          fastOLEDUpdate("Restart ESP32", "to recover", 1);
        }
      } else if (freeHeap < 30000) {
        // WARNING: Heap getting low but still above critical threshold
        Serial.printf("⚠️ MEMORY WARNING: %u bytes free\n", freeHeap);
      }
    }

    yield();  // Yield after each frame to prevent watchdog reset

  } else {
    // Heartbeat LED when not streaming (reduced frequency to save power)
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 10000) {
      digitalWrite(LED_PIN, HIGH);
      delay(30);
      digitalWrite(LED_PIN, LOW);
      lastHeartbeat = millis();
    }
    delay(20);  // Yield CPU when idle
  }
}