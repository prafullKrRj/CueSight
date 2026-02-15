package com.cuegight.cuesight

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainActivityTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Esp32CamScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32CamScreen() {
    val scope = rememberCoroutineScope()

    // Change these if your IP/ports differ
    val streamUrl = "http://192.168.4.1/stream"
    val cmdBase = "http://192.168.4.1:81/cmd?v="

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ESP32-CAM Stream") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // MJPEG stream inside WebView (non-blocking)
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        loadUrl(streamUrl)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { sendCmd(scope, cmdBase, 1) }) { Text("Cmd 1") }
                Button(onClick = { sendCmd(scope, cmdBase, 2) }) { Text("Cmd 2") }
                Button(onClick = { sendCmd(scope, cmdBase, 3) }) { Text("Cmd 3") }
            }
        }
    }
}

private fun sendCmd(scope: kotlinx.coroutines.CoroutineScope, base: String, v: Int) {
    scope.launch(Dispatchers.IO) {
        val url = URL(base + v)
        (url.openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 1000
            readTimeout = 1000
            try {
                inputStream.close()
            } finally {
                disconnect()
            }
        }
    }
}