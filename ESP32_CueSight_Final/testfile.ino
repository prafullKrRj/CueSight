#include "esp_camera.h"
#include "esp_http_server.h"
#include <WiFi.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

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

// ─── Stream handler (runs in its own HTTPD thread) ───
static esp_err_t stream_handler(httpd_req_t *req) {
    esp_err_t res = ESP_OK;
    char part_buf[128];

    // Set chunked response with MJPEG content type
    res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
    if (res != ESP_OK) return res;

    // Disable any caching
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
        // At QVGA-quality-10 a frame takes ~15-20 ms to capture,
        // so the real limiter is the sensor, not this delay.
        // For ~8 FPS target: 125 ms per frame total.
        // The capture itself takes ~20 ms, so we sleep ~100 ms.
        // Adjust down for faster FPS (e.g., 80 → ~10 FPS).
        vTaskDelay(pdMS_TO_TICKS(100));
    }

    return res;
}

// ─── Single frame (for debugging / snapshots) ───
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

// ─── Command handler — fully non-blocking, runs on a SEPARATE server ───
static esp_err_t cmd_handler(httpd_req_t *req) {
    char query[32];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) == ESP_OK) {
        char val[8];
        if (httpd_query_key_value(query, "v", val, sizeof(val)) == ESP_OK) {
            receivedValue = atoi(val);
            Serial.printf("CMD: %d\n", receivedValue);
            httpd_resp_sendstr(req, "OK");
            return ESP_OK;
        }
    }
    httpd_resp_set_status(req, "400 Bad Request");
    httpd_resp_sendstr(req, "Missing v");
    return ESP_OK;
}

// ─── Root page ───
static const char ROOT_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  body{margin:0;background:#111;color:#eee;font-family:sans-serif;text-align:center}
  img{width:100%;max-width:640px;display:block;margin:10px auto}
  .btn{display:inline-block;padding:12px 24px;margin:6px;background:#333;
       color:#0f0;border:1px solid #0f0;text-decoration:none;border-radius:6px;
       font-size:16px}
  .btn:active{background:#0f0;color:#111}
</style></head><body>
<h2>ESP32-CAM OV2640</h2>
<img id="stream" src="/stream">
<div>
  <a class="btn" href="#" onclick="sendCmd(1)">Cmd 1</a>
  <a class="btn" href="#" onclick="sendCmd(2)">Cmd 2</a>
  <a class="btn" href="#" onclick="sendCmd(3)">Cmd 3</a>
</div>
<p id="status">Ready</p>
<script>
function sendCmd(v){
  fetch('/cmd?v='+v).then(r=>r.text()).then(t=>{
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

// ─── Start TWO servers: stream on core 1, commands on core 0 ───
static httpd_handle_t stream_httpd = NULL;
static httpd_handle_t ctrl_httpd   = NULL;

void startServers() {
    // --- Stream server on port 80 ---
    httpd_config_t stream_config = HTTPD_DEFAULT_CONFIG();
    stream_config.server_port    = 80;
    stream_config.ctrl_port      = 32768;
    stream_config.max_uri_handlers = 4;
    stream_config.stack_size     = 8192;   // stream needs more stack
    // core_id = 1 → camera DMA runs best on core 1 (protocol core)
    stream_config.core_id        = 1;

    if (httpd_start(&stream_httpd, &stream_config) == ESP_OK) {
        httpd_uri_t root_uri    = { .uri = "/",       .method = HTTP_GET,
                .handler = root_handler,   .user_ctx = NULL };
        httpd_uri_t stream_uri  = { .uri = "/stream",  .method = HTTP_GET,
                .handler = stream_handler, .user_ctx = NULL };
        httpd_uri_t frame_uri   = { .uri = "/frame",   .method = HTTP_GET,
                .handler = frame_handler,  .user_ctx = NULL };
        httpd_register_uri_handler(stream_httpd, &root_uri);
        httpd_register_uri_handler(stream_httpd, &stream_uri);
        httpd_register_uri_handler(stream_httpd, &frame_uri);
        Serial.println("Stream server started on port 80");
    }

    // --- Control server on port 81 (separate socket, never blocked by stream) ---
    httpd_config_t ctrl_config = HTTPD_DEFAULT_CONFIG();
    ctrl_config.server_port    = 81;
    ctrl_config.ctrl_port      = 32769;
    ctrl_config.max_uri_handlers = 2;
    ctrl_config.stack_size     = 4096;
    ctrl_config.core_id        = 0;   // runs on the other core

    if (httpd_start(&ctrl_httpd, &ctrl_config) == ESP_OK) {
        httpd_uri_t cmd_uri = { .uri = "/cmd", .method = HTTP_GET,
                .handler = cmd_handler, .user_ctx = NULL };
        httpd_register_uri_handler(ctrl_httpd, &cmd_uri);
        Serial.println("Control server started on port 81");
    }
}

// ─── Setup ───
void setup() {
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);   // disable brownout detector

    Serial.begin(115200);
    delay(1000);
    Serial.println("\n===== ESP32-CAM OV2640 Starting =====");

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
    config.grab_mode     = CAMERA_GRAB_LATEST;   // always get freshest frame

    if (psramFound()) {
        Serial.println("PSRAM: Found — using VGA + double buffer");
        config.frame_size   = FRAMESIZE_VGA;      // 640x480 — much better quality
        config.jpeg_quality = 10;                  // lower = better (range 0-63)
        config.fb_count     = 2;                   // double-buffer: one capturing while one sending
        config.fb_location  = CAMERA_FB_IN_PSRAM;
    } else {
        Serial.println("PSRAM: Not found — using QVGA single buffer");
        config.frame_size   = FRAMESIZE_QVGA;
        config.jpeg_quality = 12;
        config.fb_count     = 1;
        config.fb_location  = CAMERA_FB_IN_DRAM;
    }

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("Camera init FAILED: 0x%x — restarting\n", err);
        delay(1000);
        ESP.restart();
    }
    Serial.println("Camera: Initialized");

    // Fine-tune sensor
    sensor_t *s = esp_camera_sensor_get();
    s->set_brightness(s, 1);      // slight brightness boost
    s->set_saturation(s, 1);      // slight saturation boost
    s->set_whitebal(s, 1);        // auto white balance ON
    s->set_awb_gain(s, 1);        // AWB gain ON
    s->set_aec2(s, 1);            // auto exposure (DSP) ON
    s->set_ae_level(s, 0);        // AE level (centered)
    s->set_gainceiling(s, GAINCEILING_4X);  // limit gain to reduce noise

    // ── WiFi AP ──
    WiFi.mode(WIFI_AP);
    WiFi.softAP(ssid, password);
    Serial.printf("WiFi AP: %s @ %s\n", ssid, WiFi.softAPIP().toString().c_str());

    // ── HTTP servers ──
    startServers();

    Serial.println("===== READY =====");
    Serial.println("Connect to WiFi: ESP32_CAM");
    Serial.println("Stream : http://192.168.4.1/stream");
    Serial.println("Snapshot: http://192.168.4.1/frame");
    Serial.println("Command : http://192.168.4.1:81/cmd?v=123");
}

// ─── Loop — free for your own logic ───
void loop() {
    // The HTTP servers run in their own FreeRTOS tasks.
    // Use this loop for anything else (motor control, sensor reads, etc.)

    // Example: read the latest command value without blocking
    static int lastPrinted = -1;
    int v = receivedValue;
    if (v != lastPrinted) {
        Serial.printf("Loop sees CMD: %d\n", v);
        lastPrinted = v;
        // TODO: act on the command here
    }

    vTaskDelay(pdMS_TO_TICKS(10));   // 100 Hz loop, very low overhead
}