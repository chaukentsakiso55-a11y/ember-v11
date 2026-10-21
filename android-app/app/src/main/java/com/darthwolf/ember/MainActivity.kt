package com.darthwolf.ember

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.text.InputType
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var prompt: EditText
    private lateinit var key: EditText
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32); setBackgroundColor(Color.rgb(6,12,24)) }
        fun title(s:String):TextView = TextView(this).apply { text=s; setTextColor(Color.CYAN); textSize=20f; setPadding(0,10,0,12) }
        fun field(h:String):EditText = EditText(this).apply { hint=h; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true) }
        root.addView(title("EMBER  //  ANDROID"))
        root.addView(title("DarthWolf  •  MARK LVI inspired"))
        output=title("System ready. Configure a compatible API endpoint in Settings.")
        root.addView(output)
        prompt=field("Ask Ember")
        root.addView(prompt)
        key=field("API key").apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(getPreferences(0).getString("key","")) }
        endpoint=field("HTTPS OpenAI-compatible base URL").apply { setText(getPreferences(0).getString("endpoint","")) }
        model=field("Model name").apply { setText(getPreferences(0).getString("model","")) }
        val settings=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=LinearLayout.GONE; addView(endpoint); addView(model); addView(key) }
        root.addView(settings)
        val settingsButton=Button(this).apply { text="Engine & Providers"; setOnClickListener { settings.visibility=if(settings.visibility==LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE } }
        root.addView(settingsButton)
        root.addView(Button(this).apply { text="Save settings"; setOnClickListener { getPreferences(0).edit().putString("key",key.text.toString()).putString("endpoint",endpoint.text.toString()).putString("model",model.text.toString()).apply(); output.text="Settings saved on device" } })
        root.addView(Button(this).apply { text="Send"; setOnClickListener { ask(prompt.text.toString()) } })
        root.addView(Button(this).apply { text="Share to WhatsApp"; setOnClickListener { val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,prompt.text.toString()); setPackage("com.whatsapp") }; try { startActivity(intent) } catch(e:Exception) { output.text="WhatsApp unavailable" } } })
        val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
    }
    private fun ask(message:String) {
        val base=endpoint.text.toString().trim().trimEnd('/')
        if (!base.startsWith("https://") || model.text.isBlank()) { output.text="Set an HTTPS endpoint and model first"; return }
        output.text="Thinking..."
        thread {
            try {
                val url=URL("$base/chat/completions")
                val conn=url.openConnection() as HttpURLConnection
                conn.requestMethod="POST"; conn.connectTimeout=12000; conn.readTimeout=25000; conn.doOutput=true
                conn.setRequestProperty("Content-Type","application/json")
                if(key.text.isNotBlank()) conn.setRequestProperty("Authorization","Bearer ${key.text}")
                val body=JSONObject().put("model",model.text.toString()).put("messages",org.json.JSONArray().put(JSONObject().put("role","user").put("content",message)))
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val status=conn.responseCode
                val response=(if(status in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                val result=if(status in 200..299) JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content") else "Provider returned HTTP $status"
                runOnUiThread { output.text=result }
                conn.disconnect()
            } catch(e:Exception) { runOnUiThread { output.text="Request failed: ${e.javaClass.simpleName}" } }
        }
    }
}
