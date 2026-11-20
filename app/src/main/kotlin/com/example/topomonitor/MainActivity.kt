package com.example.topomonitor

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
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
    private lateinit var searchInput: EditText
    private lateinit var summaryText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var actionButton: Button

    private var allClientLines: List<Pair<String, String>> = emptyList()

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
            setPadding(36, 52, 36, 64)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = "TOPO MONITOR"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 18)
        }

        passwordInput = EditText(this).apply {
            hint = "Password (admin / tamu)"
            setHintTextColor(Color.parseColor("#8899AA"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#162238"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        actionButton = Button(this).apply {
            text = "LOGIN & MUAT TOPOLOGI"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setOnClickListener {
                loginAndFetchData(passwordInput.text.toString())
            }
        }

        summaryText = TextView(this).apply {
            text = "Silakan login untuk memuat data sistem & topologi."
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 14f
            setBackgroundColor(Color.parseColor("#111C2E"))
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        searchInput = EditText(this).apply {
            hint = "Cari nama client / status / IP..."
            setHintTextColor(Color.parseColor("#8899AA"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#162238"))
            setPadding(24, 18, 24, 18)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderFilteredClients(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(titleView)
        layout.addView(passwordInput)
        layout.addView(actionButton)
        layout.addView(summaryText)
        layout.addView(searchInput)
        layout.addView(listContainer)
        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun loginAndFetchData(password: String) {
        actionButton.isEnabled = false
        summaryText.text = "Login ke $baseUrl/api/login ..."

        Thread {
            try {
                val loginResult = postLogin(password)
                if (loginResult.first !in 200..299) {
                    runOnUiThread {
                        summaryText.text = "Login gagal (HTTP ${loginResult.first}): ${loginResult.second}"
                        actionButton.isEnabled = true
                    }
                    return@Thread
                }

                runOnUiThread {
                    summaryText.text = "Mengambil data dari /api/data ..."
                }

                val dataResult = getApiData()
                if (dataResult.first !in 200..299) {
                    runOnUiThread {
                        summaryText.text = "Gagal ambil data (HTTP ${dataResult.first}): ${dataResult.second}"
                        actionButton.isEnabled = true
                    }
                    return@Thread
                }

                parseAndDisplay(dataResult.second)
                runOnUiThread {
                    actionButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    summaryText.text = "Error: ${e.message}"
                    actionButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun parseAndDisplay(rawJson: String) {
        val root = JSONObject(rawJson)
        val role = root.optString("role", "-")
        val sys = root.optJSONObject("system")
        val cpu = sys?.opt("cpu")?.toString() ?: "-"
        val ram = sys?.opt("ram")?.toString() ?: "-"
        val disk = sys?.opt("disk")?.toString() ?: "-"
        val temp = sys?.opt("temp")?.toString() ?: "-"

        val topo = root.optJSONObject("topology")
        val clientsArr = topo?.optJSONArray("clients")

        var onlineCount = 0
        var offlineCount = 0
        val parsedList = mutableListOf<Pair<String, String>>()

        if (clientsArr != null) {
            for (i in 0 until clientsArr.length()) {
                val item = clientsArr.optJSONObject(i) ?: continue
                val name = item.optString("name", "Tanpa Nama")
                val status = item.optString("status", "unknown")
                val ip = item.optString("ip", "")
                val parent = item.optString("parent", "")

                if (status.equals("online", ignoreCase = true)) {
                    onlineCount++
                } else {
                    offlineCount++
                }

                val detailParts = mutableListOf<String>()
                detailParts.add("Status: $status")
                if (ip.isNotBlank()) detailParts.add("IP: $ip")
                if (parent.isNotBlank()) detailParts.add("Parent: $parent")

                val searchKey = "$name $status $ip $parent".lowercase()
                val displayText = "$name\n${detailParts.joinToString("  |  ")}"
                parsedList.add(Pair(searchKey, displayText))
            }
        }

        allClientLines = parsedList

        runOnUiThread {
            summaryText.text =
                "Role: $role  |  Online: $onlineCount  |  Offline: $offlineCount\n" +
                "CPU: ${cpu}%  |  RAM: ${ram}%  |  Disk: ${disk}%  |  Temp: ${temp}°C"
            renderFilteredClients(searchInput.text?.toString() ?: "")
        }
    }

    private fun renderFilteredClients(query: String) {
        listContainer.removeAllViews()
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allClientLines
        } else {
            allClientLines.filter { it.first.contains(q) }
        }

        for ((_, text) in filtered) {
            val isOnline = text.contains("Status: online", ignoreCase = true)
            val card = TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 13f
                setBackgroundColor(Color.parseColor("#132036"))
                setPadding(24, 18, 24, 18)
                setShadowLayer(0f, 0f, 0f, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                if (!isOnline) {
                    setTextColor(Color.parseColor("#FCA5A5"))
                }
            }
            listContainer.addView(card)
        }
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
