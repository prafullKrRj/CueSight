/*
 * ============================================================================
 * CueSight ESP32-CAM - FIXED Streaming (NON-BLOCKING)
 * ============================================================================
 */

#include "esp_camera.h"
#include "esp_timer.h"
#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

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

// ============================================================================
// FPS CONTROL
// ============================================================================
#define TARGET_FPS 6
#define FRAME_INTERVAL_MS (1000 / TARGET_FPS)

// ============================================================================
// EMOTION CODES
// ============================================================================
#define EMOTION_HAPPY       0x01
#define EMOTION_SAD         0x02
#define EMOTION_ANGRY       0x03
#define EMOTION_SURPRISED   0x04
#define EMOTION_NEUTRAL     0x05
#define EMOTION_NO_FACE     0x06
#define EMOTION_HIDDEN      0x07

const char EMOTION_STR_HAPPY[] PROGMEM = "Happy";
const char EMOTION_STR_SAD[] PROGMEM = "Sad";
const char EMOTION_STR_ANGRY[] PROGMEM = "Angry";
const char EMOTION_STR_SURPRISED[] PROGMEM = "Surprised";
const char EMOTION_STR_NEUTRAL[] PROGMEM = "Neutral";
const char EMOTION_STR_NO_FACE[] PROGMEM = "No face";
const char EMOTION_STR_HIDDEN[] PROGMEM = "?";
const char EMOTION_STR_WAITING[] PROGMEM = "Waiting...";

const char* const EMOTION_STRINGS[] PROGMEM = {
  nullptr,
  EMOTION_STR_HAPPY,
  EMOTION_STR_SAD,
  EMOTION_STR_ANGRY,
  EMOTION_STR_SURPRISED,
  EMOTION_STR_NEUTRAL,
  EMOTION_STR_NO_FACE,
  EMOTION_STR_HIDDEN
};

void getEmotionString(uint8_t code, char* buffer, size_t bufferSize) {
  if (bufferSize < 2) {
    if (bufferSize == 1) buffer[0] = '\0';
    return;
  }
  if (code == 0 || code > 7) {
    strncpy_P(buffer, EMOTION_STR_WAITING, bufferSize - 1);
  } else {
    const char* emotionPtr = (const char*)pgm_read_ptr(&EMOTION_STRINGS[code]);
    strncpy_P(buffer, emotionPtr, bufferSize - 1);
  }
  buffer[bufferSize - 1] = '\0';
}

// ============================================================================
// GLOBALS
// ============================================================================
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiServer frameServer(FRAME_PORT);
WiFiServer commandServer(COMMAND_PORT);
WiFiClient frameClient;
WiFiClient commandClient;

enum SessionMode { MODE_IDLE, MODE_TEACHING, MODE_PRACTICE };

uint8_t currentEmotionCode = 0;
SessionMode currentMode = MODE_IDLE;
bool streamingActive = false;
bool lowMemoryLock = false;

uint32_t frameCount = 0;
uint32_t lastFPSCheck = 0;
float currentFPS = 0.0;
unsigned long lastFrameTime = 0;

char commandBuffer[MAX_COMMAND_LENGTH + 1];
uint16_t commandBufferPos = 0;

uint8_t pendingEmotionCode = 0;
unsigned long lastOLEDUpdate = 0;
#define OLED_UPDATE_INTERVAL_MS 1000

// ============================================================================
// OLED HELPERS
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

const char* modeLabel() {
  switch (currentMode) {
    case MODE_TEACHING:  return PSTR("TEACH");
    case MODE_PRACTICE:  return PSTR("PRACTICE");
    default:             return PSTR("IDLE");
  }
}

void showEmotionDisplay(uint8_t emotionCode) {
  char emotionStr[16];
  getEmotionString(emotionCode, emotionStr, sizeof(emotionStr));

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("Emotion:"));
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

void showFeedbackDisplay(const char* feedbackText) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("Feedback:"));
  display.setTextSize(2);
  display.setCursor(0, 16);
  char truncated[17];
  strncpy(truncated, feedbackText, 16);
  truncated[16] = '\0';
  display.println(truncated);
  display.setTextSize(1);
  display.setCursor(0, 50);
  display.printf_P(PSTR("Mode: %s"), modeLabel());
  display.display();
}

// ============================================================================
// SAFE SEND (chunked write with timeout)
// ============================================================================
bool safeSend(WiFiClient& client, const uint8_t* data, size_t len) {
  if (!client.connected()) return false;
  
  size_t sent = 0;
  unsigned long startMs = millis();
  
  while (sent < len) {
    if (!client.connected()) return false;
    if (millis() - startMs > 2000) return false;

    size_t chunk = min(len - sent, (size_t)1024);
    size_t written = client.write(data + sent, chunk);
    
    if (written > 0) {
      sent += written;
    } else {
      delay(1);
    }
  }
  return true;
}

// ============================================================================
// COMMAND HANDLER
// ============================================================================
void handleCommand(const char* message, uint16_t len) {
  if (len == 0) return;

  // Binary emotion code (single byte 0x01-0x07)
  if (len == 1) {
    uint8_t code = (uint8_t)message[0];
    if (code >= 0x01 && code <= 0x07) {
      pendingEmotionCode = code;
      Serial.printf("[TCP] Emotion code: 0x%02X\n", code);
      return;
    }
  }

  Serial.printf("[TCP] Command: %s\n", message);

  if (strncmp(message, "MODE:", 5) == 0) {
    const char* mode = message + 5;
    if (strcasecmp(mode, "TEACHING") == 0) {
      currentMode = MODE_TEACHING;
      Serial.println(F("[MODE] Switched to TEACHING"));
      fastOLEDUpdate("Mode", "Teaching", 1);
    } else if (strcasecmp(mode, "PRACTICE") == 0) {
      currentMode = MODE_PRACTICE;
      Serial.println(F("[MODE] Switched to PRACTICE"));
      fastOLEDUpdate("Mode", "Practice", 1);
    } else {
      currentMode = MODE_IDLE;
      streamingActive = false;
      Serial.println(F("[MODE] Switched to IDLE"));
      fastOLEDUpdate("Mode", "Idle", 1);
    }
  } else if (strcmp(message, "STREAM:START") == 0) {
    if (!lowMemoryLock) {
      streamingActive = true;
      frameCount = 0;
      lastFPSCheck = millis();
      lastFrameTime = millis();
      Serial.println(F("[TCP] Streaming started"));
    } else {
      Serial.println(F("[TCP] Cannot start - LOW MEMORY LOCK"));
    }
  } else if (strcmp(message, "STREAM:STOP") == 0) {
    streamingActive = false;
    Serial.println(F("[TCP] Streaming stopped"));
  } else if (strncmp(message, "EMOTION:", 8) == 0) {
    const char* emotion = message + 8;
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
    if (code != 0) pendingEmotionCode = code;
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
      delay(120);
      digitalWrite(LED_PIN, LOW);
    }
  }
  
  yield();
}

// ============================================================================
// CAMERA INIT
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
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_QQVGA;
  config.jpeg_quality = 20;
  config.fb_count = 1;
  config.grab_mode = CAMERA_GRAB_LATEST;
  
  if(psramFound()){
    Serial.println("PSRAM: Found");
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
  } else {
    Serial.println("PSRAM: Not found");
    config.fb_location = CAMERA_FB_IN_DRAM;
  }
  
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init FAILED: 0x%x\n", err);
    return false;
  }
  
  Serial.println("Camera: Initialized");
  
  sensor_t * s = esp_camera_sensor_get();
  s->set_brightness(s, 1);
  s->set_contrast(s, 0);
  s->set_whitebal(s, 1);
  s->set_awb_gain(s, 1);
  s->set_gain_ctrl(s, 1);
  s->set_aec2(s, 1);
  s->set_lenc(s, 1);
  
  Serial.println("Camera: Configured");
  return true;
}

// ============================================================================
// WIFI
// ============================================================================
bool connectWiFi() {
  WiFi.mode(WIFI_AP);
  WiFi.setSleep(false);
  fastOLEDUpdate("Creating AP...", ssid, 1);

  bool success = WiFi.softAP(ssid, password);
  if (!success) {
    Serial.println("[WiFi] AP creation FAILED");
    fastOLEDUpdate("WiFi FAIL", "AP Error", 1);
    return false;
  }

  IPAddress IP = WiFi.softAPIP();
  Serial.printf("[WiFi] AP created: %s\n", ssid);
  Serial.printf("[WiFi] IP: %s\n", IP.toString().c_str());

  char buffer[32];
  snprintf(buffer, sizeof(buffer), "IP:%s", IP.toString().c_str());
  fastOLEDUpdate(buffer, "AP Ready", 1);
  delay(2000);
  return true;
}

// ============================================================================
// SETUP
// ============================================================================
void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  
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
// MAIN LOOP
// ============================================================================
void loop() {
  // Accept frame client (port 81)
  WiFiClient incomingFrameClient = frameServer.available();
  if (incomingFrameClient) {
    if (frameClient && frameClient.connected()) {
      Serial.println("[TCP] Replacing frame client");
      frameClient.stop();
    }
    frameClient = incomingFrameClient;
    frameClient.setNoDelay(true);
    Serial.printf("[TCP] Frame client connected from %s\n", frameClient.remoteIP().toString().c_str());
  }

  if (frameClient && !frameClient.connected()) {
    Serial.println("[TCP] Frame client disconnected");
    frameClient.stop();
    streamingActive = false;
  }

  // Accept command client (port 82)
  WiFiClient incomingCmdClient = commandServer.available();
  if (incomingCmdClient) {
    if (commandClient && commandClient.connected()) {
      Serial.println("[TCP] Replacing command client");
      commandClient.stop();
    }
    commandBufferPos = 0;
    memset(commandBuffer, 0, sizeof(commandBuffer));
    commandClient = incomingCmdClient;
    commandClient.setNoDelay(true);
    Serial.printf("[TCP] Command client connected from %s\n", commandClient.remoteIP().toString().c_str());
  }

  if (commandClient && !commandClient.connected()) {
    Serial.println("[TCP] Command client disconnected");
    commandClient.stop();
    commandBufferPos = 0;
    memset(commandBuffer, 0, sizeof(commandBuffer));
  }

  // Process commands (non-blocking, max 3 per loop)
  int commandsProcessed = 0;
  while (commandClient && commandClient.connected() && commandClient.available() && commandsProcessed < MAX_COMMANDS_PER_LOOP) {
    char c = (char)commandClient.read();
    if (c == '\n') {
      commandBuffer[commandBufferPos] = '\0';
      handleCommand(commandBuffer, commandBufferPos);
      commandBufferPos = 0;
      commandsProcessed++;
    } else if (c != '\r') {
      if (commandBufferPos < MAX_COMMAND_LENGTH) {
        commandBuffer[commandBufferPos++] = c;
      } else {
        commandBufferPos = 0;
      }
    }
  }

  // Deferred OLED update (max 1Hz)
  if (pendingEmotionCode != 0 && (millis() - lastOLEDUpdate) > OLED_UPDATE_INTERVAL_MS) {
    currentEmotionCode = pendingEmotionCode;
    showEmotionDisplay(currentEmotionCode);
    pendingEmotionCode = 0;
    lastOLEDUpdate = millis();
  }

  // Frame streaming (non-blocking FPS control)
  if (streamingActive && frameClient && frameClient.connected()) {
    unsigned long now = millis();
    if (now - lastFrameTime >= FRAME_INTERVAL_MS) {
      lastFrameTime = now;

      camera_fb_t* fb = esp_camera_fb_get();
      if (!fb) {
        Serial.println("[CAM] Capture failed");
        delay(30);
        return;
      }

      uint32_t frameLen = fb->len;
      uint8_t header[4] = {
        (uint8_t)((frameLen >> 24) & 0xFF),
        (uint8_t)((frameLen >> 16) & 0xFF),
        (uint8_t)((frameLen >> 8) & 0xFF),
        (uint8_t)(frameLen & 0xFF)
      };

      bool ok = safeSend(frameClient, header, 4);
      if (ok) ok = safeSend(frameClient, fb->buf, fb->len);

      esp_camera_fb_return(fb);

      if (!ok) {
        Serial.println("[TCP] Write failed, disconnecting");
        frameClient.stop();
        streamingActive = false;
        return;
      }

      frameCount++;
      if (frameCount % 30 == 0) {
        unsigned long elapsed = millis() - lastFPSCheck;
        currentFPS = (30.0 * 1000.0) / elapsed;
        uint32_t freeHeap = esp_get_free_heap_size();
        Serial.printf("[TCP] FPS: %.1f | Frames: %lu | Heap: %u | Frame: %u bytes\n",
                      currentFPS, frameCount, freeHeap, frameLen);
        lastFPSCheck = millis();
      }

      // Memory check every 20 frames
      if (frameCount % 20 == 0) {
        uint32_t freeHeap = esp_get_free_heap_size();
        if (freeHeap < 25000) {
          if (!lowMemoryLock) {
            lowMemoryLock = true;
            streamingActive = false;
            Serial.printf("LOW MEMORY: %u bytes\n", freeHeap);
            if (frameClient && frameClient.connected()) frameClient.stop();
            fastOLEDUpdate("Low Memory!", "< 25KB free", 2);
          }
        } else if (freeHeap < 30000) {
          Serial.printf("Memory warning: %u bytes\n", freeHeap);
        }
      }

      yield();
    } else {
      delay(1);
    }
  } else {
    // Heartbeat when idle
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 10000) {
      digitalWrite(LED_PIN, HIGH);
      delay(30);
      digitalWrite(LED_PIN, LOW);
      lastHeartbeat = millis();
    }
    delay(10);
  }
}