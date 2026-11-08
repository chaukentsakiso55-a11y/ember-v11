package com.cyberpulse.ember

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private lateinit var tts: TextToSpeech
    private val worker = Executors.newSingleThreadExecutor()
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var ttsReady = false
    private val prefs by lazy {
        val master = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            this, "ember_secure", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 18)
        tts = TextToSpeech(this, this)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            addJavascriptInterface(EmberBridge(this@MainActivity, this), "EmberAndroid")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" || uri.scheme == "mailto" || uri.scheme == "tel") {
                        return try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) { true }
                    }
                    return uri.scheme != "file"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val allowed = request.resources.filter { resource ->
                            when (resource) {
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                else -> false
                            }
                        }.toTypedArray()
                        if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                    }
                }

                override fun onShowFileChooser(
                    view: WebView?, callback: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        startActivityForResult(params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*"), 4102)
                        true
                    } catch (_: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }
        }
        setContentView(web)
        if (savedInstanceState == null) web.loadUrl("file:///android_asset/index.html")
        else web.restoreState(savedInstanceState)
        requestRuntimePermissions()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale.getDefault()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4102) {
            val result = if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 4101)
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        worker.shutdownNow()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    inner class EmberBridge(private val activity: Activity, private val view: WebView) {
        private fun reply(id: String, value: JSONObject) {
            val js = "window.__emberCb && window.__emberCb(${JSONObject.quote(id)},${JSONObject.quote(value.toString())});"
            activity.runOnUiThread { view.evaluateJavascript(js, null) }
        }

        @JavascriptInterface
        fun gemini(id: String, body: String, key: String, model: String) {
            worker.execute {
                var status = 0
                var response = ""
                var connection: HttpURLConnection? = null
                try {
                    val safeModel = model.replace(Regex("[^A-Za-z0-9._-]"), "")
                    connection = URL("https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent").openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 90000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-goog-api-key", key)
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    response = JSONObject().put("error", JSONObject().put("message", e.message ?: "Network request failed")).toString()
                } finally { connection?.disconnect() }
                reply(id, JSONObject().put("status", status).put("body", response))
            }
        }

        @JavascriptInterface
        fun speak(id: String, text: String, voice: String) {
            activity.runOnUiThread {
                if (!ttsReady) { reply(id, JSONObject().put("status", 0).put("body", "Text to speech is not ready")); return@runOnUiThread }
                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ember-${SystemClock.uptimeMillis()}")
                reply(id, JSONObject().put("status", if (result == TextToSpeech.SUCCESS) 1 else 0).put("body", ""))
            }
        }

        @JavascriptInterface fun stopSpeak() { activity.runOnUiThread { if (::tts.isInitialized) tts.stop() } }
        @JavascriptInterface fun listen() { activity.runOnUiThread { view.evaluateJavascript("window.__listenRec && window.__listenRec()", null) } }
        @JavascriptInterface fun onConfig(json: String) { }
        @JavascriptInterface fun requestPermission(kind: String) { activity.runOnUiThread { requestRuntimePermissions() } }

        @JavascriptInterface
        fun permissionStatus(): String = JSONObject()
            .put("microphone", ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            .put("camera", ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            .put("notifications", if (android.os.Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true)
            .toString()

        @JavascriptInterface
        fun metrics(): String {
            val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            val used = 100.0 * (memory.totalMem - memory.availMem).toDouble() / memory.totalMem.coerceAtLeast(1L)
            val battery = activity.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = activity.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val charging = status?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) in listOf(
                android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_STATUS_FULL
            )
            return JSONObject().put("cpu", -1).put("mem", used.toInt()).put("net", 0)
                .put("netKb", 0).put("tmp", 0).put("up", SystemClock.elapsedRealtime())
                .put("bat", level).put("charging", charging).toString()
        }

        @JavascriptInterface
        fun act(id: String, raw: String) {
            worker.execute {
                val action = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                val type = action.optString("type")
                val result = try {
                    when (type) {
                        "open_url" -> {
                            val uri = Uri.parse(action.optString("url"))
                            if (uri.scheme != "https") "Only HTTPS links can be opened by Ember."
                            else { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)); "Opened link." }
                        }
                        "set_timer" -> {
                            val seconds = action.optInt("sec", 60).coerceIn(1, 86400)
                            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                                .putExtra(AlarmClock.EXTRA_MESSAGE, action.optString("label", "Ember timer"))
                                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            activity.startActivity(intent); "Timer opened in the Clock app."
                        }
                        "set_alarm" -> {
                            val parts = action.optString("time").split(":")
                            if (parts.size != 2) "Invalid alarm time."
                            else {
                                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                                    .putExtra(AlarmClock.EXTRA_HOUR, parts[0].toInt())
                                    .putExtra(AlarmClock.EXTRA_MINUTES, parts[1].toInt())
                                    .putExtra(AlarmClock.EXTRA_MESSAGE, action.optString("label", "Ember"))
                                activity.startActivity(intent); "Alarm opened in the Clock app."
                            }
                        }
                        "game_stop" -> "No game-control session is active."
                        "list_reminders" -> "Android reminder integration is not configured in this build."
                        "cancel_reminder" -> "Android reminder integration is not configured in this build."
                        "set_reminder" -> "Reminder scheduling is not configured in this build."
                        else -> "This action is not implemented in the Android bridge yet: $type"
                    }
                } catch (e: Exception) { "Action failed: ${e.message ?: "unknown error"}" }
                reply(id, JSONObject().put("status", 1).put("body", result))
            }
        }

        @JavascriptInterface fun typeSecret(id: String, user: String, password: String, submit: Boolean) {
            reply(id, JSONObject().put("status", 0).put("body", "Cross-app typing is disabled in this build."))
        }

        @JavascriptInterface fun saveFile(name: String, content: String): String {
            return try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Ember")
                }
                val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "Could not create download."
                activity.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                "Saved to Downloads/Ember."
            } catch (e: Exception) { "Save failed: ${e.message}" }
        }

        @JavascriptInterface fun setFloating(enabled: Boolean) { }
        @JavascriptInterface fun setVoice(enabled: Boolean) { }
        @JavascriptInterface fun setPaused(paused: Boolean) { }
        @JavascriptInterface fun floatState(): String = JSONObject().put("available", false).toString()
        @JavascriptInterface fun syncProfile(json: String) { }
        @JavascriptInterface fun takePending(): String = "{}"
        @JavascriptInterface fun saveSecret(value: String) { try { prefs.edit().putString("gemini_key", value).apply() } catch (_: Exception) { } }
        @JavascriptInterface fun loadSecret(): String = try { prefs.getString("gemini_key", "") ?: "" } catch (_: Exception) { "" }

        @JavascriptInterface fun espScan(): String = "[]"
        @JavascriptInterface fun espConnect(id: String, device: Int) { reply(id, JSONObject().put("status", 0).put("body", "USB serial support is not enabled in this build.")) }
        @JavascriptInterface fun espRun(id: String, code: String) { reply(id, JSONObject().put("status", 0).put("body", "USB serial support is not enabled in this build.")) }
        @JavascriptInterface fun espUpload(id: String, name: String, code: String, restart: Boolean) { reply(id, JSONObject().put("status", 0).put("body", "USB serial support is not enabled in this build.")) }
        @JavascriptInterface fun espStop() { }
        @JavascriptInterface fun espReset() { }
        @JavascriptInterface fun espRead(): String = ""
        @JavascriptInterface fun espDisconnect() { }
    }
}
