package com.example.topomonitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class TopoNode(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val status: String,
    val parentId: String,
    val waypoints: List<Pair<Double, Double>>,
    val colorHex: String
)

class MainActivity : AppCompatActivity() {

    private val baseUrl = "http://192.168.1.12:5002"
    private lateinit var passwordInput: EditText
    private lateinit var actionButton: Button
    private lateinit var statusBar: TextView
    private lateinit var mapView: SatelliteTopoMapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookieManager)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B1320"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mapView = SatelliteTopoMapView(this) { newZoom ->
            statusBar.text = "Zoom: ${newZoom.toInt()}  |  Geser layar untuk pan"
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC0F172A"))
            setPadding(24, 36, 24, 20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val loginRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        passwordInput = EditText(this).apply {
            hint = "Password (admin / tamu)"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = 12
            }
        }

        actionButton = Button(this).apply {
            text = "LOGIN & PETA"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                loginAndLoadMap(passwordInput.text.toString())
            }
        }

        statusBar = TextView(this).apply {
            text = "Masukkan password lalu tekan LOGIN & PETA"
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 12f
            setPadding(0, 12, 0, 0)
        }

        loginRow.addView(passwordInput)
        loginRow.addView(actionButton)
        topPanel.addView(loginRow)
        topPanel.addView(statusBar)

        val zoomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                rightMargin = 24
                bottomMargin = 48
            }
        }

        val btnZoomIn = Button(this).apply {
            text = "+"
            setBackgroundColor(Color.parseColor("#E61E293B"))
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(104, 104).apply {
                bottomMargin = 12
            }
            setOnClickListener {
                mapView.zoomBy(1.0)
            }
        }

        val btnZoomOut = Button(this).apply {
            text = "-"
            setBackgroundColor(Color.parseColor("#E61E293B"))
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(104, 104).apply {
                bottomMargin = 12
            }
            setOnClickListener {
                mapView.zoomBy(-1.0)
            }
        }

        val btnFit = Button(this).apply {
            text = "FIT"
            setBackgroundColor(Color.parseColor("#E62563EB"))
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(104, 104)
            setOnClickListener {
                mapView.fitToNodes()
            }
        }

        zoomControls.addView(btnZoomIn)
        zoomControls.addView(btnZoomOut)
        zoomControls.addView(btnFit)

        root.addView(mapView)
        root.addView(topPanel)
        root.addView(zoomControls)
        setContentView(root)
    }

    private fun loginAndLoadMap(password: String) {
        actionButton.isEnabled = false
        statusBar.text = "Login ke /api/login..."

        Thread {
            try {
                val loginRes = postLogin(password)
                if (loginRes.first !in 200..299) {
                    runOnUiThread {
                        statusBar.text = "Login gagal (HTTP ${loginRes.first}): ${loginRes.second}"
                        actionButton.isEnabled = true
                    }
                    return@Thread
                }

                runOnUiThread {
                    statusBar.text = "Mengambil /api/data..."
                }

                val dataRes = getApiData()
                if (dataRes.first !in 200..299) {
                    runOnUiThread {
                        statusBar.text = "Gagal /api/data (HTTP ${dataRes.first})"
                        actionButton.isEnabled = true
                    }
                    return@Thread
                }

                val parsed = parseTopology(dataRes.second)
                runOnUiThread {
                    mapView.setNodes(parsed.nodes)
                    statusBar.text =
                        "Role: ${parsed.role} | Node: ${parsed.nodes.size} | Online: ${parsed.onlineCount} | CPU: ${parsed.cpu}% | Temp: ${parsed.temp}°C"
                    actionButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusBar.text = "Error: ${e.message}"
                    actionButton.isEnabled = true
                }
            }
        }.start()
    }

    private data class ParsedPayload(
        val role: String,
        val cpu: String,
        val temp: String,
        val onlineCount: Int,
        val nodes: List<TopoNode>
    )

    private fun parseTopology(rawJson: String): ParsedPayload {
        val root = JSONObject(rawJson)
        val role = root.optString("role", "-")
        val sys = root.optJSONObject("system")
        val cpu = sys?.opt("cpu")?.toString() ?: "-"
        val temp = sys?.opt("temp")?.toString() ?: "-"

        val topo = root.optJSONObject("topology")
        val nodesMap = LinkedHashMap<String, TopoNode>()
        var onlineCount = 0

        if (topo != null) {
            val keys = topo.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = topo.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val coords = obj.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val lat = coords.optDouble(0, Double.NaN)
                    val lon = coords.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue

                    val rawId = obj.optString("id", "").ifBlank { "${key}_$i" }
                    val name = obj.optString("name", rawId)
                    val status = obj.optString("status", "online")
                    if (status.equals("online", ignoreCase = true)) {
                        onlineCount++
                    }
                    val parentId = obj.optString("parent", "")
                    val colorHex = obj.optString("color", "#2563EB")
                    val waypoints = parseWaypoints(obj.optJSONArray("waypoints"))

                    val node = TopoNode(
                        id = rawId,
                        name = name,
                        lat = lat,
                        lon = lon,
                        status = status,
                        parentId = parentId,
                        waypoints = waypoints,
                        colorHex = colorHex
                    )
                    nodesMap[rawId] = node
                    if (name.isNotBlank() && !nodesMap.containsKey(name)) {
                        nodesMap[name] = node
                    }
                }
            }
        }

        val distinctNodes = nodesMap.values.distinctBy { "${it.id}_${it.lat}_${it.lon}_${it.name}" }
        return ParsedPayload(role, cpu, temp, onlineCount, distinctNodes)
    }

    private fun parseWaypoints(arr: JSONArray?): List<Pair<Double, Double>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until arr.length()) {
            val pt = arr.optJSONArray(i) ?: continue
            if (pt.length() >= 2) {
                val wLat = pt.optDouble(0, Double.NaN)
                val wLon = pt.optDouble(1, Double.NaN)
                if (!wLat.isNaN() && !wLon.isNaN()) {
                    result.add(Pair(wLat, wLon))
                }
            }
        }
        return result
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

class SatelliteTopoMapView(
    context: Context,
    private val onZoomChanged: (Double) -> Unit
) : View(context) {

    private var nodes: List<TopoNode> = emptyList()
    private var nodeLookup: Map<String, TopoNode> = emptyMap()

    private var centerLat = -0.2650
    private var centerLon = 110.8205
    private var zoom = 15.0

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val tileCache = ConcurrentHashMap<String, Bitmap>()
    private val loadingTiles = ConcurrentHashMap.newKeySet<String>()

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint().apply { color = Color.parseColor("#0B1320") }
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D90F172A")
        style = Paint.Style.FILL
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
    }

    fun setNodes(newNodes: List<TopoNode>) {
        nodes = newNodes
        val map = HashMap<String, TopoNode>()
        for (n in newNodes) {
            map[n.id] = n
            if (!map.containsKey(n.name)) {
                map[n.name] = n
            }
        }
        nodeLookup = map
        fitToNodes()
    }

    fun fitToNodes() {
        if (nodes.isEmpty()) {
            invalidate()
            return
        }
        val avgLat = nodes.map { it.lat }.average()
        val avgLon = nodes.map { it.lon }.average()
        centerLat = avgLat
        centerLon = avgLon
        zoom = 15.0
        onZoomChanged(zoom)
        invalidate()
    }

    fun zoomBy(delta: Double) {
        zoom = (zoom + delta).coerceIn(3.0, 19.0)
        onZoomChanged(zoom)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                val scale = 256.0 * 2.0.pow(zoom)
                val centerWorldX = lonToWorldX(centerLon, scale) - dx
                val centerWorldY = latToWorldY(centerLat, scale) - dy

                centerLon = worldXToLon(centerWorldX, scale)
                centerLat = worldYToLat(centerWorldY, scale)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (width == 0 || height == 0) return

        val zInt = zoom.toInt().coerceIn(0, 19)
        val tileScale = 256.0 * 2.0.pow(zInt.toDouble())
        val viewScale = 256.0 * 2.0.pow(zoom)

        val centerViewX = lonToWorldX(centerLon, viewScale)
        val centerViewY = latToWorldY(centerLat, viewScale)

        val topLeftViewX = centerViewX - width / 2.0
        val topLeftViewY = centerViewY - height / 2.0

        val ratio = tileScale / viewScale
        val startTileX = floor((topLeftViewX * ratio) / 256.0).toInt()
        val endTileX = floor(((topLeftViewX + width) * ratio) / 256.0).toInt()
        val startTileY = floor((topLeftViewY * ratio) / 256.0).toInt()
        val endTileY = floor(((topLeftViewY + height) * ratio) / 256.0).toInt()

        val maxTile = (1 shl zInt) - 1
        val tileScreenSize = (256.0 / ratio).toFloat()

        for (tx in startTileX..endTileX) {
            if (tx < 0 || tx > maxTile) continue
            for (ty in startTileY..endTileY) {
                if (ty < 0 || ty > maxTile) continue
                val screenX = ((tx * 256.0) / ratio - topLeftViewX).toFloat()
                val screenY = ((ty * 256.0) / ratio - topLeftViewY).toFloat()
                val dst = RectF(screenX, screenY, screenX + tileScreenSize + 1f, screenY + tileScreenSize + 1f)

                val key = "$zInt/$tx/$ty"
                val bmp = tileCache[key]
                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, null, dst, tilePaint)
                } else {
                    requestTileAsync(zInt, tx, ty, key)
                }
            }
        }

        for (node in nodes) {
            val parent = nodeLookup[node.parentId]
            if (parent != null || node.waypoints.isNotEmpty()) {
                val path = Path()
                var started = false

                if (parent != null) {
                    val px = (lonToWorldX(parent.lon, viewScale) - topLeftViewX).toFloat()
                    val py = (latToWorldY(parent.lat, viewScale) - topLeftViewY).toFloat()
                    path.moveTo(px, py)
                    started = true
                }

                for (wp in node.waypoints) {
                    val wx = (lonToWorldX(wp.second, viewScale) - topLeftViewX).toFloat()
                    val wy = (latToWorldY(wp.first, viewScale) - topLeftViewY).toFloat()
                    if (!started) {
                        path.moveTo(wx, wy)
                        started = true
                    } else {
                        path.lineTo(wx, wy)
                    }
                }

                val nx = (lonToWorldX(node.lon, viewScale) - topLeftViewX).toFloat()
                val ny = (latToWorldY(node.lat, viewScale) - topLeftViewY).toFloat()
                if (!started) {
                    path.moveTo(nx, ny)
                } else {
                    path.lineTo(nx, ny)
                }

                canvas.drawPath(path, lineGlowPaint)
                canvas.drawPath(path, linePaint)
            }
        }

        for (node in nodes) {
            val sx = (lonToWorldX(node.lon, viewScale) - topLeftViewX).toFloat()
            val sy = (latToWorldY(node.lat, viewScale) - topLeftViewY).toFloat()

            if (sx < -200f || sx > width + 200f || sy < -200f || sy > height + 200f) continue

            val isOnline = node.status.equals("online", ignoreCase = true)
            dotPaint.color = if (!isOnline) {
                Color.parseColor("#EF4444")
            } else {
                parseColorSafe(node.colorHex, Color.parseColor("#22C55E"))
            }

            canvas.drawCircle(sx, sy, 11f, dotPaint)
            canvas.drawCircle(sx, sy, 11f, borderPaint)

            val textWidth = labelTextPaint.measureText(node.name)
            val padH = 10f
            val padV = 6f
            val boxLeft = sx + 16f
            val boxTop = sy - 18f - padV
            val boxRight = boxLeft + textWidth + padH * 2
            val boxBottom = sy + 10f + padV

            val pill = RectF(boxLeft, boxTop, boxRight, boxBottom)
            canvas.drawRoundRect(pill, 10f, 10f, labelBgPaint)
            canvas.drawText(node.name, boxLeft + padH, sy + 4f, labelTextPaint)
        }
    }

    private fun requestTileAsync(z: Int, x: Int, y: Int, key: String) {
        if (!loadingTiles.add(key)) return
        Thread {
            try {
                val urlStr = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "TopoMonitorAndroid/1.0")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            tileCache[key] = bmp
                            postInvalidate()
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            } finally {
                loadingTiles.remove(key)
            }
        }.start()
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            val clean = if (hex.startsWith("#")) hex else "#$hex"
            Color.parseColor(clean)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun lonToWorldX(lon: Double, scale: Double): Double {
        return (lon + 180.0) / 360.0 * scale
    }

    private fun latToWorldY(lat: Double, scale: Double): Double {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clampedLat * PI / 180.0
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * scale
    }

    private fun worldXToLon(x: Double, scale: Double): Double {
        return x / scale * 360.0 - 180.0
    }

    private fun worldYToLat(y: Double, scale: Double): Double {
        val n = PI - 2.0 * PI * y / scale
        return 180.0 / PI * atan(sinh(n))
    }
}
