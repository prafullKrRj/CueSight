/*
 * CueSight ESP32-CAM (Updated) - WebSocket RAW Streaming
 * Removes HTTP MJPEG streaming and sends raw grayscale frames over WebSocket.
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <WebSocketsServer.h>
#include <WiFiUdp.h>

#define CAMERA_MODEL_GC2145
#include "camera_pins.h"

// --- Pin Definitions ---
#define I2C_SDA 14
#define I2C_SCL 15
#define LED_PIN 4

// --- Display Configuration ---
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define OLED_ADDRESS 0x3C

// --- Network Configuration ---
// TODO: Update these credentials before uploading to your ESP32-CAM.
const char* ssid = "YourWiFiSSID";
const char* password = "YourWiFiPassword";

#define UDP_PORT 37020
#define WEBSOCKET_PORT 8888

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WebSocketsServer webSocket(WEBSOCKET_PORT);
WiFiUDP udp;

enum SessionMode {
  MODE_IDLE,
  MODE_TEACHING,
  MODE_PRACTICE
};

String currentEmotion = "Waiting...";
SessionMode currentMode = MODE_IDLE;
bool streamingActive = false;
bool lowMemoryLock = false;
uint8_t connectedClient = 255;

uint32_t frameCount = 0;
uint32_t lastFPSCheck = 0;
float currentFPS = 0.0f;

uint32_t lastReconnectAttempt = 0;
uint32_t lastPingTime = 0;
bool wifiWasDisconnected = false;

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

void onWebSocketEvent(uint8_t client_num, WStype_t type, uint8_t* payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[WS] Client #%u disconnected\n", client_num);
      if (connectedClient == client_num) {
        streamingActive = false;
        connectedClient = 255;
      }
      break;

    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(client_num);
        Serial.printf("[WS] Client #%u connected from %s\n", client_num, ip.toString().c_str());
        connectedClient = client_num;
      }
      break;

    case WStype_TEXT:
      {
        String message = String((const char*)payload, length);
        message.trim();
        Serial.printf("[WS] Received text: %s\n", message.c_str());

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
            Serial.println("[WS] Streaming started");
          }
        } else if (message == "STREAM:STOP") {
          streamingActive = false;
          Serial.println("[WS] Streaming stopped");
        } else if (message.startsWith("EMOTION:")) {
          String emotion = message.substring(8);
          emotion.trim();
          if (emotion == "HIDDEN") {
            currentEmotion = "Hidden";
            showEmotionDisplay("Hidden");
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
      break;

    default:
      break;
  }
}

void handleDiscovery() {
  int packetSize = udp.parsePacket();
  if (packetSize) {
    char incomingPacket[255];
    int len = udp.read(incomingPacket, 255);
    if (len > 0) {
      incomingPacket[len] = 0;
    }

    if (strcmp(incomingPacket, "DISCOVER_CUESIGHT") == 0) {
      String response = "CUESIGHT_ESP32:" + WiFi.localIP().toString();
      udp.beginPacket(udp.remoteIP(), udp.remotePort());
      udp.write((uint8_t*)response.c_str(), response.length());
      udp.endPacket();
      Serial.println("[UDP] Discovery response sent");
    }
  }
}

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
  config.pixel_format = PIXFORMAT_GRAYSCALE;
  config.frame_size = FRAMESIZE_QQVGA;
  config.jpeg_quality = 10;
  config.fb_count = 2;
  config.grab_mode = CAMERA_GRAB_LATEST;

  if (psramFound()) {
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.fb_count = 2;
  } else {
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", err);
    fastOLEDUpdate("Camera FAIL", "Restart", 1);
    return false;
  }

  sensor_t *s = esp_camera_sensor_get();
  s->set_pixformat(s, PIXFORMAT_GRAYSCALE);
  s->set_framesize(s, FRAMESIZE_QQVGA);
  return true;
}

bool connectWiFi() {
  WiFi.setAutoReconnect(true);
  WiFi.persistent(true);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  fastOLEDUpdate("WiFi...", ssid, 1);
  WiFi.begin(ssid, password);

  uint8_t attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    attempts++;
  }

  if (WiFi.status() != WL_CONNECTED) {
    fastOLEDUpdate("WiFi FAIL", "Check AP", 1);
    return false;
  }

  return true;
}

void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Wire.begin(I2C_SDA, I2C_SCL);
  display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS);
  display.clearDisplay();
  display.setTextSize(2);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("CueSight");
  display.setTextSize(1);
  display.setCursor(0, 25);
  display.println("Starting...");
  display.display();

  if (!initCamera()) {
    return;
  }

  if (!connectWiFi()) {
    return;
  }

  udp.begin(UDP_PORT);
  webSocket.begin();
  webSocket.onEvent(onWebSocketEvent);

  lastFPSCheck = millis();
  lastReconnectAttempt = millis();
  lastPingTime = millis();

  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("Ready!");
  display.println(WiFi.localIP().toString());
  display.println();
  display.println("Waiting for");
  display.println("Android app...");
  display.display();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    wifiWasDisconnected = true;
    unsigned long now = millis();
    if (now - lastReconnectAttempt > 10000) {
      WiFi.disconnect();
      WiFi.reconnect();
      lastReconnectAttempt = now;
    }
    delay(100);
    return;
  }

  if (wifiWasDisconnected) {
    wifiWasDisconnected = false;
    fastOLEDUpdate("WiFi OK", "Waiting...", 1);
  }

  webSocket.loop();
  handleDiscovery();

  unsigned long now = millis();
  if (connectedClient != 255 && (now - lastPingTime > 30000)) {
    webSocket.sendPing(connectedClient);
    lastPingTime = now;
  }

  if (streamingActive && connectedClient != 255) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      delay(50);
      return;
    }

    webSocket.sendBIN(connectedClient, fb->buf, fb->len);
    esp_camera_fb_return(fb);

    frameCount++;
    if (frameCount % 50 == 0) {
      unsigned long elapsed = millis() - lastFPSCheck;
      currentFPS = (50.0 * 1000.0) / elapsed;
      lastFPSCheck = millis();
    }

    if (frameCount % 200 == 0) {
      uint32_t freeHeap = esp_get_free_heap_size();
      if (freeHeap < 30000 && !lowMemoryLock) {
        lowMemoryLock = true;
        streamingActive = false;
        if (connectedClient != 255) {
          webSocket.sendTXT(connectedClient, "ERROR:LOW_MEMORY");
        }
        fastOLEDUpdate("Low Memory", "Restart ESP32", 1);
      }
    }

    yield();
  } else {
    delay(20);
  }
}
