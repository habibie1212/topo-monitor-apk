package com.example.topomonitor

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(32, 48, 32, 32)
        }

        val btnFetch = Button(this).apply {
            text = "Cek /api/data (192.168.1.12:5002)"
        }

        tvStatus = TextView(this).apply {
            text = "Siap memuat data..."
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            setPadding(0, 24, 0, 12)
        }

        tvOutput = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
        }

        val scroll = ScrollView(this).apply {
            addView(tvOutput)
        }

        root.addView(btnFetch)
        root.addView(tvStatus)
        root.addView(scroll)
        setContentView(root)

        btnFetch.setOnClickListener {
            fetchEndpoint("http://192.168.1.12:5002/api/data")
        }

        fetchEndpoint("http://192.168.1.12:5002/api/data")
    }

    private fun fetchEndpoint(urlStr: String) {
        tvStatus.text = "Menghubungi $urlStr ..."
        thread {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                runOnUiThread {
                    tvStatus.text = "HTTP $code (${body.length} karakter)"
                    tvOutput.text = body.take(4000)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Error koneksi"
                    tvOutput.text = e.localizedMessage ?: e.toString()
                }
            }
        }
    }
}
