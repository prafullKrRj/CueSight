#include "esp_camera.h"
#include "esp_http_server.h"
#include <WiFi.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ─── OLED Libraries (Optional but recommended for CueSight practice feedback) ───
// To use the OLED, install "Adafruit SSD1306" and "Adafruit GFX Library" in Arduino IDE.
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1 
// The ESP32-CAM has limited pins. I2C can be routed to un-used pins like 14 and 15
// (assuming you aren't using the microSD card).
#define I2C_SDA 14
#define I2C_SCL 15

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
bool oledConnected = false;

// ─── AI-Thinker ESP32-CAM OV2640 Pins ───
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// ─── Config ───
// Set to match CueSight's default expected ESP32 AP settings
const char* ssid     = "ESP32_CAM_P";
const char* password = "12345678"; 

// Shared command value — written by /cmd, read by your logic
static volatile int receivedValue = 0;

// ─── MJPEG boundary (fixed, no heap alloc) ───
#define PART_BOUNDARY "fb0d5a5b5e6b"
static const char* STREAM_CONTENT_TYPE =
    "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* STREAM_BOUNDARY =
    "\r\n--" PART_BOUNDARY "\r\n";
static const char* STREAM_PART =
    "Content-Type: image/jpeg\r\n"
    "Content-Length: %u\r\n"
    "X-Timestamp: %lu\r\n\r\n";

// ─── Forward Declarations ───
void displayMessageOnOLED(const char* msg, int textSize = 2);

// ─── Stream handler (runs in its own HTTPD thread) ───
static esp_err_t stream_handler(httpd_req_t *req) {
    esp_err_t res = ESP_OK;
    char part_buf[128];

    // Set chunked response with MJPEG content type
    res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
    if (res != ESP_OK) return res;

    // Disable any caching to keep latency low for CueSight app
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "X-Framerate", "8");

    while (true) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            Serial.println("Stream: capture failed, retrying...");
            vTaskDelay(pdMS_TO_TICKS(50));
            continue;
        }

        // Send boundary
        res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
        if (res != ESP_OK) {
            esp_camera_fb_return(fb);
            break;
        }

        // Send part header (Content-Type + Content-Length)
        size_t hlen = snprintf(part_buf, sizeof(part_buf),
                               STREAM_PART, fb->len, (unsigned long)esp_timer_get_time());
        res = httpd_resp_send_chunk(req, part_buf, hlen);
        if (res != ESP_OK) {
            esp_camera_fb_return(fb);
            break;
        }

        // Send JPEG data — single write, zero-copy from DMA/PSRAM buffer
        res = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
        esp_camera_fb_return(fb);

        if (res != ESP_OK) break;

        // Yield to let other tasks (including /cmd handler) run.
        vTaskDelay(pdMS_TO_TICKS(100)); // Target ~8-10 FPS
    }

    return res;
}

// ─── Single frame handler ───
static esp_err_t frame_handler(httpd_req_t *req) {
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
        httpd_resp_send_500(req);
        return ESP_FAIL;
    }
    httpd_resp_set_type(req, "image/jpeg");
    httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=snap.jpg");
    esp_err_t res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    return res;
}

// ─── Command handler (handles HttpCommandSender.kt requests) ───
static esp_err_t cmd_handler(httpd_req_t *req) {
    char query[32];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) == ESP_OK) {
        char val[8];
        if (httpd_query_key_value(query, "v", val, sizeof(val)) == ESP_OK) {
            receivedValue = atoi(val);
            Serial.printf("Received CMD from CueSight app: %d\n", receivedValue);
            httpd_resp_sendstr(req, "OK");
            return ESP_OK;
        }
    }
    httpd_resp_set_status(req, "400 Bad Request");
    httpd_resp_sendstr(req, "Missing v parameter");
    return ESP_OK;
}

// ─── Root HTML page (Fallback for manual testing) ───
static const char ROOT_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CueSight ESP32</title>
<style>
  body{margin:0;background:#111;color:#eee;font-family:sans-serif;text-align:center}
  img{width:100%;max-width:640px;display:block;margin:10px auto}
  .btn{display:inline-block;padding:12px 24px;margin:6px;background:#333;
       color:#0f0;border:1px solid #0f0;text-decoration:none;border-radius:6px;
       font-size:16px}
  .btn:active{background:#0f0;color:#111}
</style></head><body>
<h2>CueSight Camera Feed</h2>
<img id="stream" src="/stream">
<p id="status">Ready</p>
<script>
function sendCmd(v){
  fetch(':81/cmd?v='+v).then(r=>r.text()).then(t=>{
    document.getElementById('status').textContent='Cmd '+v+': '+t;
  }).catch(e=>{
    document.getElementById('status').textContent='Error: '+e;
  });
}
</script></body></html>
)rawliteral";

static esp_err_t root_handler(httpd_req_t *req) {
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_send(req, ROOT_HTML, strlen(ROOT_HTML));
}

// ─── Server initialization ───
static httpd_handle_t stream_httpd = NULL;
static httpd_handle_t ctrl_httpd   = NULL;

void startServers() {
    // --- Stream server on port 80 ---
    httpd_config_t stream_config = HTTPD_DEFAULT_CONFIG();
    stream_config.server_port    = 80;
    stream_config.ctrl_port      = 32768; // Unique internal ctrl port
    stream_config.max_uri_handlers = 4;
    stream_config.stack_size     = 8192;  // stream needs more stack
    stream_config.core_id        = 1;     // Camera DMA runs best on core 1

    if (httpd_start(&stream_httpd, &stream_config) == ESP_OK) {
        httpd_uri_t root_uri    = { .uri = "/",       .method = HTTP_GET, .handler = root_handler,   .user_ctx = NULL };
        httpd_uri_t stream_uri  = { .uri = "/stream", .method = HTTP_GET, .handler = stream_handler, .user_ctx = NULL };
        httpd_uri_t frame_uri   = { .uri = "/frame",  .method = HTTP_GET, .handler = frame_handler,  .user_ctx = NULL };
        httpd_register_uri_handler(stream_httpd, &root_uri);
        httpd_register_uri_handler(stream_httpd, &stream_uri);
        httpd_register_uri_handler(stream_httpd, &frame_uri);
        Serial.println("✓ Stream server started on port 80");
    }

    // --- Control server on port 81 (Matches Android app HttpCommandSender.kt) ---
    httpd_config_t ctrl_config = HTTPD_DEFAULT_CONFIG();
    ctrl_config.server_port    = 81;
    ctrl_config.ctrl_port      = 32769; // Unique internal ctrl port
    ctrl_config.max_uri_handlers = 2;
    ctrl_config.stack_size     = 4096;
    ctrl_config.core_id        = 0;     // Run on the other core

    if (httpd_start(&ctrl_httpd, &ctrl_config) == ESP_OK) {
        httpd_uri_t cmd_uri = { .uri = "/cmd", .method = HTTP_GET, .handler = cmd_handler, .user_ctx = NULL };
        httpd_register_uri_handler(ctrl_httpd, &cmd_uri);
        Serial.println("✓ Control server started on port 81");
    }
}

// ─── Setup ───
void setup() {
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);   // disable brownout detector

    Serial.begin(115200);
    delay(1000);
    Serial.println("\n===== CueSight ESP32-CAM Base Starting =====");

    // Initialize OLED (Optional)
    Wire.begin(I2C_SDA, I2C_SCL);
    if(display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
      oledConnected = true;
      Serial.println("✓ OLED Initialized");
      display.clearDisplay();
      display.setTextColor(SSD1306_WHITE);
      displayMessageOnOLED("Ready", 3); // Larger text size
    } else {
      Serial.println("OLED allocation failed or not connected.");
    }

    // ── Camera init ──
    camera_config_t config;
    config.ledc_channel  = LEDC_CHANNEL_0;
    config.ledc_timer    = LEDC_TIMER_0;
    config.pin_d0        = Y2_GPIO_NUM;
    config.pin_d1        = Y3_GPIO_NUM;
    config.pin_d2        = Y4_GPIO_NUM;
    config.pin_d3        = Y5_GPIO_NUM;
    config.pin_d4        = Y6_GPIO_NUM;
    config.pin_d5        = Y7_GPIO_NUM;
    config.pin_d6        = Y8_GPIO_NUM;
    config.pin_d7        = Y9_GPIO_NUM;
    config.pin_xclk      = XCLK_GPIO_NUM;
    config.pin_pclk      = PCLK_GPIO_NUM;
    config.pin_vsync     = VSYNC_GPIO_NUM;
    config.pin_href      = HREF_GPIO_NUM;
    config.pin_sscb_sda  = SIOD_GPIO_NUM;
    config.pin_sscb_scl  = SIOC_GPIO_NUM;
    config.pin_pwdn      = PWDN_GPIO_NUM;
    config.pin_reset     = RESET_GPIO_NUM;
    config.xclk_freq_hz  = 20000000;
    config.pixel_format  = PIXFORMAT_JPEG;
    config.grab_mode     = CAMERA_GRAB_LATEST;

    if (psramFound()) {
        Serial.println("PSRAM: Found — using QVGA + double buffer for speed");
        config.frame_size   = FRAMESIZE_QVGA;     // Low resolution for high-speed streaming
        config.jpeg_quality = 12;                 // Lower quality = smaller size = faster (0-63)
        config.fb_count     = 2;
        config.fb_location  = CAMERA_FB_IN_PSRAM;
    } else {
        Serial.println("PSRAM: Not found — using HQVGA single buffer");
        config.frame_size   = FRAMESIZE_HQVGA;    // Very low resolution to prevent hangs on small memory
        config.jpeg_quality = 15;
        config.fb_count     = 1;
        config.fb_location  = CAMERA_FB_IN_DRAM;
    }

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("Camera init FAILED: 0x%x — restarting\n", err);
        delay(1000);
        ESP.restart();
    }
    Serial.println("✓ Camera Initialized");

    // Sensor optimizations for consistent brightness
    sensor_t *s = esp_camera_sensor_get();
    s->set_brightness(s, 1);
    s->set_saturation(s, 1);
    s->set_whitebal(s, 1);
    s->set_awb_gain(s, 1);
    s->set_aec2(s, 1);
    s->set_ae_level(s, 0);
    s->set_gainceiling(s, GAINCEILING_4X);

    // ── WiFi AP (Matches CueSight Default ESP32 AP logic) ──
    WiFi.mode(WIFI_AP);
    // You can hardcode an IP address here if needed, but 192.168.4.1 is the default SoftAP IP
    WiFi.softAP(ssid, password);
    Serial.printf("✓ WiFi AP Started: %s @ %s\n", ssid, WiFi.softAPIP().toString().c_str());

    // ── Start Servers ──
    startServers();
    Serial.println("===== CUESIGHT BACKEND READY =====");
}

// ─── Loop — Process incoming application commands ───
void loop() {
    static int lastProcessed = -1;
    int cmd = receivedValue;
    
    if (cmd != lastProcessed) {
        lastProcessed = cmd;
        const char* statusStr = nullptr;

        // Emotion Commands (1-7) & Practice Commands (10-12) based on HttpCommandSender.kt
        switch(cmd) {
            case 1: statusStr = "Happy"; break;
            case 2: statusStr = "Sad"; break;
            case 3: statusStr = "Angry"; break;
            case 4: statusStr = "Surprise"; break; // Very long, will wrap automatically
            case 5: statusStr = "Neutral"; break;
            case 6: statusStr = "Disgust"; break;
            case 7: statusStr = "Fear"; break;
            
            case 10: statusStr = "?\nGuess"; break; // Practice: Question mark
            case 11: statusStr = "YES"; break;      // Practice: Correct ✓
            case 12: statusStr = "NO"; break;       // Practice: Wrong ✗ ("X" is small, "NO" avoids symbol rendering issues)
            
            default:
                if (cmd != 0) {
                   Serial.printf("Unknown Cmd: %d\n", cmd); 
                }
                break;
        }

        if (statusStr != nullptr) {
             Serial.printf("App says: %s\n", statusStr);
             displayMessageOnOLED(statusStr, 3); // Increased Text size from 2 to 3
             
             // Reset back to 0 so we don't process it infinitely, 
             // but if the app explicitly sends '0' nothing will display.
             receivedValue = 0; 
             lastProcessed = 0;
        }
    }

    vTaskDelay(pdMS_TO_TICKS(50));
}

// Helper to draw to OLED if attached
void displayMessageOnOLED(const char* msg, int textSize) {
    if (!oledConnected) return;
    display.clearDisplay();
    display.setTextSize(textSize);
    display.setCursor(0, 0);
    display.println(msg);
    display.display();
}
