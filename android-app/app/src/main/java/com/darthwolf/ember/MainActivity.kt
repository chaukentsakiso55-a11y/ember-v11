package com.darthwolf.ember

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val bg = Color.rgb(3, 9, 19)
    private val panelColor = Color.rgb(5, 19, 32)
    private val cyan = Color.rgb(0, 220, 255)
    private val pink = Color.rgb(237, 32, 250)
    private val white = Color.rgb(209, 234, 255)
    private val muted = Color.rgb(108, 163, 198)
    private val prefs by lazy { getSharedPreferences("ember_v11", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var composer: EditText
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private val history = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(txt("[ EMBER ]", 25f, pink, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(txt("● ONLINE", 11f, cyan, true))
        root.addView(header)
        val tagline = txt("N E U R A L   C O M M A N D   N O D E", 10f, muted, true)
        tagline.gravity = Gravity.CENTER
        root.addView(tagline)
        root.addView(View(this).apply { setBackgroundColor(cyan) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(10), 0, dp(10)) })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("HOME", "AI CHAT", "SETTINGS").forEach { page ->
            nav.addView(btn(page, page == "HOME") { navigate(page) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), dp(5), dp(3), 0) })
        }
        root.addView(nav)
        setContentView(root)
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun txt(s: String, size: Float = 14f, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = if (bold) .06f else .015f
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panelColor); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(19, 79, 108))
        }
        elevation = dp(3).toFloat()
    }

    private fun btn(title: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 12f; setTextColor(white)
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(Color.rgb(65, 8, 78), Color.rgb(7, 43, 65)) else intArrayOf(Color.rgb(4, 25, 40), Color.rgb(2, 14, 27))
        ).apply { cornerRadius = dp(8).toFloat(); setStroke(dp(1), if (primary) pink else cyan) }
        setOnClickListener { action() }
    }

    private fun heading(title: String, sub: String) {
        content.addView(txt(title, 17f, cyan, true))
        content.addView(txt(sub, 10f, muted))
    }
    private fun addCard(v: View, top: Int = 12) {
        content.addView(v, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })
    }
    private fun navigate(page: String) {
        when (page) { "HOME" -> showHome(); "AI CHAT" -> showChat(); else -> showSettings() }
    }

    private fun showHome() {
        content.removeAllViews()
        heading("INTELLIGENCE. CONTROL. AMPLIFIED.", "EMBER V11  //  CYBER PULSE")
        val hero = card().apply {
            gravity = Gravity.CENTER
            addView(EmberFace(this@MainActivity), LinearLayout.LayoutParams(dp(190), dp(190)))
            addView(txt("EMBER NEURAL CORE", 17f, white, true).apply { gravity = Gravity.CENTER })
            addView(txt("AI SYSTEM  •  READY FOR INPUT", 10f, cyan).apply { gravity = Gravity.CENTER })
        }
        addCard(hero, 14)
        val vitals = card()
        vitals.addView(txt("SYSTEM VITALS", 15f, cyan, true))
        vitals.addView(txt("DEVICE STATUS  //  LIVE SESSION", 10f, muted))
        listOf("CPU" to "ANDROID PROCESS", "RAM" to "MEMORY", "NET" to "NETWORK", "CORE" to "EMBER ENGINE").forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(txt(pair.first, 12f, white, true), LinearLayout.LayoutParams(dp(58), dp(34)))
            row.addView(txt(pair.second, 10f, muted), LinearLayout.LayoutParams(0, dp(34), 1f))
            row.addView(txt("● ACTIVE", 10f, cyan, true))
            vitals.addView(row)
            vitals.addView(View(this).apply { setBackgroundColor(Color.rgb(19, 79, 108)) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        addCard(vitals)
        val quick = card()
        quick.addView(txt("QUICK ACCESS", 15f, cyan, true))
        quick.addView(btn("OPEN AI CHAT", true) { showChat() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        quick.addView(btn("CONFIGURE PROVIDERS") { showSettings() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        addCard(quick)
        content.addView(txt("CYBER PULSE  /  BUILD BEYOND THE IDEA", 9f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) })
    }

    private fun showChat() {
        content.removeAllViews()
        heading("AI CHAT", "SECURE NEURAL LINK  //  MULTI-PROVIDER ENGINE")
        val providerCard = card()
        status = txt(if (configured()) "● PROVIDER CONFIGURED" else "● PROVIDER NOT CONFIGURED  •  OPEN SETTINGS", 10f, if (configured()) cyan else pink, true)
        providerCard.addView(status)
        addCard(providerCard)
        history.forEach { (role, message) ->
            val bubble = card()
            bubble.addView(txt(if (role == "user") "YOU" else "EMBER", 10f, if (role == "user") pink else cyan, true))
            bubble.addView(txt(message, 14f, white).apply { setPadding(0, dp(5), 0, 0) })
            if (role == "assistant") bubble.addView(btn("SPEAK RESPONSE") { speak(message) }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            addCard(bubble, 8)
        }
        val compose = card()
        compose.addView(txt("TRANSMIT MESSAGE", 12f, cyan, true))
        composer = EditText(this).apply {
            hint = "Enter a command for Ember..."
            setHintTextColor(muted); setTextColor(white); textSize = 15f
            minLines = 2; maxLines = 5; gravity = Gravity.TOP
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(2, 12, 23)); cornerRadius = dp(8).toFloat(); setStroke(dp(1), Color.rgb(19, 79, 108)) }
        }
        compose.addView(composer, LinearLayout.LayoutParams(-1, dp(100)).apply { topMargin = dp(8) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(btn("MIC") { listen() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, dp(8), dp(4), 0) })
        actions.addView(btn("SEND", true) { send() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), dp(8), 0, 0) })
        compose.addView(actions)
        addCard(compose)
    }

    private fun showSettings() {
        content.removeAllViews()
        heading("CORE CONFIGURATION", "PROVIDER ROUTING  //  DEVICE PREFERENCES")
        val config = card()
        config.addView(txt("AI PROVIDER CONNECTION", 15f, cyan, true))
        config.addView(txt("OpenAI-compatible HTTPS endpoint. Configure your provider URL, model ID and API key.", 11f, muted).apply { setPadding(0, dp(6), 0, dp(10)) })
        endpoint = field("Base URL  e.g. https://api.example.com/v1", prefs.getString("endpoint", "") ?: "")
        model = field("Model ID", prefs.getString("model", "") ?: "")
        apiKey = field("API key", prefs.getString("key", "") ?: "", true)
        config.addView(endpoint)
        config.addView(model, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        config.addView(apiKey, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        config.addView(btn("SAVE PROVIDER SETTINGS", true) {
            prefs.edit().putString("endpoint", endpoint.text.toString().trim().trimEnd('/'))
                .putString("model", model.text.toString().trim()).putString("key", apiKey.text.toString()).apply()
            Toast.makeText(this, "Settings saved on this device", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        addCard(config)
        val voice = card()
        voice.addView(txt("VOICE INTERFACE", 15f, cyan, true))
        voice.addView(txt("Android speech recognition and text-to-speech. Speech recognition depends on a compatible device service.", 11f, muted).apply { setPadding(0, dp(6), 0, dp(10)) })
        voice.addView(btn("TEST VOICE OUTPUT") { speak("Ember neural interface online. Voice output is ready.") }, LinearLayout.LayoutParams(-1, dp(46)))
        addCard(voice)
        val info = card()
        info.addView(txt("PRIVACY & ENGINE STATUS", 15f, cyan, true))
        info.addView(txt("API keys are stored in app preferences on this device. Use trusted HTTPS endpoints. Local model access requires a compatible server reachable over the network. This app does not bundle a language model.", 11f, muted))
        addCard(info)
    }

    private fun field(h: String, value: String, secret: Boolean = false) = EditText(this).apply {
        hint = h; setHintTextColor(muted); setTextColor(white); textSize = 13f; singleLine = true
        setPadding(dp(10), 0, dp(10), 0)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(2, 12, 23)); cornerRadius = dp(8).toFloat(); setStroke(dp(1), Color.rgb(19, 79, 108)) }
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        setText(value)
    }

    private fun configured() = !prefs.getString("endpoint", "").isNullOrBlank() && !prefs.getString("model", "").isNullOrBlank()

    private fun send() {
        val prompt = composer.text.toString().trim()
        if (prompt.isEmpty()) return
        history.add("user" to prompt)
        history.add("assistant" to "Connecting to neural core...")
        showChat()
        if (!configured()) {
            history[history.lastIndex] = "assistant" to "Provider not configured. Open Settings and add an HTTPS OpenAI-compatible endpoint and model ID."
            showChat()
            return
        }
        val base = prefs.getString("endpoint", "")!!.trimEnd('/')
        val modelName = prefs.getString("model", "")!!
        val secret = prefs.getString("key", "") ?: ""
        val messages = JSONArray()
        history.dropLast(1).takeLast(20).forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        thread {
            val answer = try {
                val conn = URL("$base/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 60000; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (secret.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $secret")
                val body = JSONObject().put("model", modelName).put("messages", messages).put("stream", false)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code in 200..299) JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "Empty response")
                else "Provider error HTTP $code. Check endpoint, model and API key."
            } catch (e: Exception) {
                "Connection failed: " + e.javaClass.simpleName + ". Check internet and provider settings."
            }
            runOnUiThread { history[history.lastIndex] = "assistant" to answer; showChat() }
        }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is unavailable", Toast.LENGTH_LONG).show(); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { runOnUiThread { status.text = "● LISTENING" } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { runOnUiThread { Toast.makeText(this@MainActivity, "Voice input ended. Try again.", Toast.LENGTH_SHORT).show() } }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                runOnUiThread { if (!words.isNullOrBlank()) { composer.setText(words); composer.setSelection(words.length) } }
            }
            override fun onPartialResults(results: Bundle?) {}
            override fun onEvent(type: Int, params: Bundle?) {}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        })
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ember-voice")
    }
    override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) tts.language = Locale.US }
    override fun onDestroy() { recognizer?.destroy(); if (::tts.isInitialized) tts.shutdown(); super.onDestroy() }
}

private class EmberFace(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val x = width / 2f; val y = height / 2f; val r = minOf(width, height) * .39f
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.rgb(0, 220, 255); c.drawCircle(x, y, r, p)
        p.color = Color.rgb(237, 32, 250); p.strokeWidth = 1.5f; c.drawCircle(x, y, r * .82f, p)
        p.style = Paint.Style.FILL; p.color = Color.argb(30, 0, 220, 255); c.drawCircle(x, y, r * .78f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.color = Color.rgb(0, 220, 255)
        val face = Path().apply { moveTo(x-r*.38f,y-r*.18f); quadTo(x,y-r*.62f,x+r*.38f,y-r*.18f); lineTo(x+r*.27f,y+r*.32f); quadTo(x,y+r*.52f,x-r*.27f,y+r*.32f); close() }
        c.drawPath(face, p)
        c.drawLine(x-r*.23f,y-r*.05f,x-r*.07f,y-r*.05f,p); c.drawLine(x+r*.07f,y-r*.05f,x+r*.23f,y-r*.05f,p)
        c.drawArc(x-r*.16f,y+r*.04f,x+r*.16f,y+r*.28f,15f,150f,false,p)
        p.style = Paint.Style.FILL; p.color = Color.rgb(237,32,250); c.drawCircle(x,y-r*.72f,dp(3f),p)
        p.color = Color.rgb(0,220,255)
        for (i in 0..7) { val a = Math.PI*i/4.0; c.drawLine(x+kotlin.math.cos(a).toFloat()*r*1.02f,y+kotlin.math.sin(a).toFloat()*r*1.02f,x+kotlin.math.cos(a).toFloat()*r*1.12f,y+kotlin.math.sin(a).toFloat()*r*1.12f,p) }
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density
}
