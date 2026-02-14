/*
 * ============================================================================
 * CueSight ESP32-CAM - Optimized JPEG Streaming via OV2640
 * ============================================================================
 *
 * Features:
 * - JPEG 320x240 @ 15-20 FPS via OV2640 HW encoder (optimized for speed)
 * - Raw TCP binary streaming (JPEG frames with 4-byte length prefix)
 * - OLED display for status
 * - LED control with emotion feedback
 * - Session mode support (Teaching/Practice/Idle)
 * - Auto WiFi reconnection
 * - Fixed IP: 192.168.4.1 (AP mode)
 *
 * Camera Config: Optimized OV2640 settings (HW JPEG encoder)
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

// AI-Thinker ESP32-CAM pin mapping (hardcoded)
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
const char* ssid = "ESP32-CAM";       // ESP32 AP SSID
const char* password = "12345678";   // TODO: Change this

#define TCP_PORT 81
#define MAX_COMMAND_LENGTH 128

// ============================================================================
// GLOBALS
// ============================================================================
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiServer tcpServer(TCP_PORT);
WiFiClient connectedClient;

// State variables
enum SessionMode {
  MODE_IDLE,
  MODE_TEACHING,
  MODE_PRACTICE
};

String currentEmotion = "Waiting...";
SessionMode currentMode = MODE_IDLE;
bool streamingActive = false;
bool lowMemoryLock = false;

// Performance metrics
uint32_t frameCount = 0;
uint32_t lastFPSCheck = 0;
float currentFPS = 0.0;

// Network monitoring
uint32_t lastReconnectAttempt = 0;
bool wifiWasDisconnected = false;

// Display buffer
char displayBuffer[64];
String commandBuffer = "";

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

const char* modeLabel() {
  switch (currentMode) {
    case MODE_TEACHING:
      return "TEACH";
    case MODE_PRACTICE:
      return "PRACTICE";
    default:
      return "IDLE";
  }
}

void showEmotionDisplay(const String& emotionText) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Emotion:");
  display.setTextSize(2);
  display.setCursor(0, 16);
  display.println(emotionText);
  display.setTextSize(1);
  display.setCursor(0, 40);
  display.printf("FPS: %.1f", currentFPS);
  display.setCursor(0, 50);
  display.printf("Mode: %s", modeLabel());
  display.display();
}

void showFeedbackDisplay(const String& feedbackText) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Feedback:");
  display.setTextSize(2);
  display.setCursor(0, 16);
  display.println(feedbackText);
  display.setTextSize(1);
  display.setCursor(0, 50);
  display.printf("Mode: %s", modeLabel());
  display.display();
}

// ============================================================================
// TCP COMMAND HANDLER
// ============================================================================
void handleCommand(String message) {
  message.trim();
  if (message.length() == 0) {
    return;
  }

  Serial.printf("[TCP] Received command: %s\n", message.c_str());

  if (message.startsWith("MODE:")) {
    String mode = message.substring(5);
    mode.toUpperCase();
    if (mode == "TEACHING") {
      currentMode = MODE_TEACHING;
      fastOLEDUpdate("Mode", "Teaching", 1);
    } else if (mode == "PRACTICE") {
      currentMode = MODE_PRACTICE;
      fastOLEDUpdate("Mode", "Practice", 1);
    } else {
      currentMode = MODE_IDLE;
      streamingActive = false;
      fastOLEDUpdate("Mode", "Idle", 1);
    }
  } else if (message == "STREAM:START") {
    if (!lowMemoryLock) {
      streamingActive = true;
      Serial.println("[TCP] Streaming started");
    }
  } else if (message == "STREAM:STOP") {
    streamingActive = false;
    Serial.println("[TCP] Streaming stopped");
  } else if (message.startsWith("EMOTION:")) {
    String emotion = message.substring(8);
    emotion.trim();
    if (emotion == "HIDDEN" || emotion == "?") {
      currentEmotion = "?";
      showEmotionDisplay("?");
    } else {
      currentEmotion = emotion;
      currentEmotion.replace("😊", "");
      currentEmotion.replace("😢", "");
      currentEmotion.replace("😴", "");
      currentEmotion.replace("😐", "");
      currentEmotion.trim();
      showEmotionDisplay(currentEmotion);
    }
  } else if (message.startsWith("FEEDBACK:")) {
    String feedback = message.substring(9);
    feedback.trim();
    showFeedbackDisplay(feedback);
  } else if (message.startsWith("LED:")) {
    String command = message.substring(4);
    command.toUpperCase();
    if (command == "ON") {
      digitalWrite(LED_PIN, HIGH);
    } else if (command == "OFF") {
      digitalWrite(LED_PIN, LOW);
    } else if (command == "BLINK") {
      digitalWrite(LED_PIN, HIGH);
      delay(150);
      digitalWrite(LED_PIN, LOW);
    }
  }
}


// ============================================================================
// CAMERA INITIALIZATION - JPEG OPTIMIZED for OV2640
// ============================================================================
bool initCamera() {
  camera_config_t config;

  // Pin configuration
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

  // ======== JPEG OPTIMIZED for OV2640 HW encoder ========
  config.xclk_freq_hz = 20000000;              // 20MHz XCLK
  config.pixel_format = PIXFORMAT_JPEG;        // OV2640 HW JPEG (zero CPU cost)
  config.frame_size = FRAMESIZE_QVGA;          // 320x240 (better for ML face detection)
  config.jpeg_quality = 12;                    // Quality 12 (~10-15KB/frame)
  config.fb_count = 2;                         // Double buffer
  config.grab_mode = CAMERA_GRAB_LATEST;       // Skip old frames
  // =========================================

  if (psramFound()) {
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.fb_count = 2;
    Serial.println("[CAM] Using PSRAM - 2 frame buffers");
  } else {
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.fb_count = 1;
    Serial.println("[CAM] No PSRAM - 1 frame buffer");
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[CAM] Init failed: 0x%x\n", err);
    fastOLEDUpdate("Camera FAIL", nullptr, 1);
    return false;
  }

  sensor_t *s = esp_camera_sensor_get();
  s->set_framesize(s, FRAMESIZE_QVGA);

  // Optimize OV2640 sensor for speed
  s->set_brightness(s, 1);
  s->set_contrast(s, 0);
  s->set_exposure_ctrl(s, 1);
  s->set_aec2(s, 1);
  s->set_whitebal(s, 1);
  s->set_awb_gain(s, 1);
  s->set_gain_ctrl(s, 1);
  s->set_special_effect(s, 0);
  s->set_lenc(s, 1);

  Serial.println("[CAM] Initialized: JPEG at QVGA (320x240) via OV2640 HW encoder");
  Serial.println("[CAM] Mode: Hardware JPEG - zero CPU encoding overhead!");
  Serial.println("[CAM] Expected FPS: 15-20 (limited by WiFi bandwidth)");
  Serial.println("[CAM] Frame size: ~10-15 KB (JPEG compressed)");

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

  // Create Access Point
  bool success = WiFi.softAP(ssid, password);

  if (!success) {
    Serial.println("\n[WiFi] AP creation FAILED!");
    fastOLEDUpdate("WiFi FAIL", "AP Error", 1);
    return false;
  }

  IPAddress IP = WiFi.softAPIP();
  Serial.printf("\n[WiFi] Access Point created: %s\n", ssid);
  Serial.printf("[WiFi] AP IP address: %s\n", IP.toString().c_str());
  Serial.printf("[WiFi] Connect to this AP with password: %s\n", password);

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
  Serial.println("🚀 CueSight ESP32-CAM");
  Serial.println("   JPEG via OV2640 HW Encoder");
  Serial.println("========================================\n");

  // LED setup
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // OLED setup
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

  // Camera init
  Serial.println("[CAM] Initializing...");
  if (!initCamera()) {
    Serial.println("[CAM] FATAL ERROR");
    while (1) {
      digitalWrite(LED_PIN, HIGH);
      delay(100);
      digitalWrite(LED_PIN, LOW);
      delay(100);
    }
  }

  // WiFi connection
  Serial.println("[WiFi] Connecting...");
  if (!connectWiFi()) {
    Serial.println("[WiFi] FATAL ERROR");
    while (1) {
      digitalWrite(LED_PIN, HIGH);
      delay(500);
      digitalWrite(LED_PIN, LOW);
      delay(500);
    }
  }


  // TCP server
  tcpServer.begin();
  tcpServer.setNoDelay(true);
  Serial.printf("[TCP] Server started on port %d\n", TCP_PORT);

  // Init timing
  lastFPSCheck = millis();
  lastReconnectAttempt = millis();

  Serial.println("\n========================================");
  Serial.println("✅ System Ready");
  Serial.printf("🔌 TCP: %s:%d\n", WiFi.softAPIP().toString().c_str(), TCP_PORT);
  Serial.println("📷 Camera: JPEG 320x240 via OV2640 HW encoder");
  Serial.println("========================================\n");

  Serial.printf("[MEM] Free heap: %u bytes\n", esp_get_free_heap_size());

  // Final display update
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
  // AP mode doesn't need reconnection logic - remove WiFi reconnection check
  // Keep the rest of the loop as-is

  if (wifiWasDisconnected) {
    wifiWasDisconnected = false;
    fastOLEDUpdate("WiFi OK", "Waiting...", 1);
  }

  // Accept or replace active TCP client
  WiFiClient incomingClient = tcpServer.available();
  if (incomingClient) {
    if (connectedClient && connectedClient.connected()) {
      connectedClient.stop();
    }
    commandBuffer = "";
    streamingActive = false;
    connectedClient = incomingClient;
    connectedClient.setNoDelay(true);
    Serial.printf("[TCP] Client connected from %s\n", connectedClient.remoteIP().toString().c_str());
  }

  if (connectedClient && !connectedClient.connected()) {
    Serial.println("[TCP] Client disconnected");
    connectedClient.stop();
    commandBuffer = "";
    streamingActive = false;
  }

  // Process line-delimited commands from Android
  while (connectedClient && connectedClient.connected() && connectedClient.available()) {
    char c = (char)connectedClient.read();
    if (c == '\n') {
      handleCommand(commandBuffer);
      commandBuffer = "";
    } else if (c != '\r') {
      commandBuffer += c;
      if (commandBuffer.length() > MAX_COMMAND_LENGTH) {
        Serial.println("[TCP] Command too long, dropping buffer");
        commandBuffer = "";
      }
    }
  }

  // TCP RAW streaming (if active)
  if (streamingActive && connectedClient && connectedClient.connected()) {
    camera_fb_t* fb = esp_camera_fb_get();

    if (!fb) {
      Serial.println("[CAM] Capture failed");
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
    size_t headerWritten = connectedClient.write(header, sizeof(header));
    size_t frameWritten = connectedClient.write(fb->buf, fb->len);
    if (headerWritten != sizeof(header) || frameWritten != (size_t)fb->len) {
      Serial.println("[TCP] Write failed, disconnecting client");
      connectedClient.stop();
      streamingActive = false;
      esp_camera_fb_return(fb);
      return;
    }

    // Release frame buffer
    esp_camera_fb_return(fb);

    // FPS calculation
    frameCount++;
    if (frameCount % 50 == 0) {
      unsigned long elapsed = millis() - lastFPSCheck;
      currentFPS = (50.0 * 1000.0) / elapsed;
      Serial.printf("[TCP] FPS: %.1f | Frames: %lu | Heap: %u\n",
                    currentFPS, frameCount, esp_get_free_heap_size());
      lastFPSCheck = millis();
    }

    // Memory monitoring
    if (frameCount % 200 == 0) {
      uint32_t freeHeap = esp_get_free_heap_size();
      if (freeHeap < 30000 && !lowMemoryLock) {
        lowMemoryLock = true;
        streamingActive = false;
        Serial.printf("⚠️ LOW MEMORY: %u bytes\n", freeHeap);
        if (connectedClient && connectedClient.connected()) {
          connectedClient.stop();
        }
        fastOLEDUpdate("Low Memory", "Restart ESP32", 1);
      }
    }

    yield();

  } else {
    // Heartbeat LED when not streaming
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 10000) {
      digitalWrite(LED_PIN, HIGH);
      delay(30);
      digitalWrite(LED_PIN, LOW);
      lastHeartbeat = millis();
    }
    delay(20);
  }
}
