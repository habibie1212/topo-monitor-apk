package com.example.topomonitor

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private val baseUrl = "http://192.168.1.12:5002"
    private lateinit var passwordInput: EditText
    private lateinit var resultText: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookieManager)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1320"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = "TOPO MONITOR ($baseUrl)"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }

        passwordInput = EditText(this).apply {
            hint = "Masukkan password admin / viewer"
            setHintTextColor(Color.parseColor("#8899AA"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#162238"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        actionButton = Button(this).apply {
            text = "LOGIN & CEK /API/DATA"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setPadding(24, 28, 24, 28)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
            setOnClickListener {
                val pwd = passwordInput.text.toString()
                loginAndFetchData(pwd)
            }
        }

        resultText = TextView(this).apply {
            text = "Masukkan password lalu tekan tombol di atas."
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 14f
        }

        layout.addView(titleView)
        layout.addView(passwordInput)
        layout.addView(actionButton)
        layout.addView(resultText)
        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun loginAndFetchData(password: String) {
        actionButton.isEnabled = false
        resultText.text = "Mengirim login ke /api/login..."

        Thread {
            try {
                val loginResult = postLogin(password)
                if (loginResult.first !in 200..299) {
                    runOnUiThread {
                        resultText.text = "LOGIN GAGAL — HTTP ${loginResult.first}\n${loginResult.second}"
                        actionButton.isEnabled = true
                    }
                    return@Thread
                }

                runOnUiThread {
                    resultText.text = "Login sukses (${loginResult.second}). Mengambil /api/data..."
                }

                val dataResult = getApiData()
                runOnUiThread {
                    resultText.text = "HTTP ${dataResult.first} (${dataResult.second.length} karakter)\n\n${dataResult.second}"
                    actionButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultText.text = "ERROR: ${e.message}"
                    actionButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun postLogin(password: String): Pair<Int, String> {
        val url = URL("$baseUrl/api/login")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        val escapedPwd = password.replace("\\", "\\\\").replace("\"", "\\\"")
        val payload = """{"password":"$escapedPwd"}"""

        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(payload)
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = readStream(stream)
        conn.disconnect()
        return Pair(code, body)
    }

    private fun getApiData(): Pair<Int, String> {
        val url = URL("$baseUrl/api/data")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = readStream(stream)
        conn.disconnect()
        return Pair(code, body)
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            sb.toString().trim()
        }
    }
}
