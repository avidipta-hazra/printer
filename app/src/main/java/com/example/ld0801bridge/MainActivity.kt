package com.example.ld0801bridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.*
import org.json.JSONArray
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var serverButton: Button

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var serverSocket: ServerSocket? = null
    private val printExecutor = Executors.newSingleThreadExecutor()
    private val httpExecutor = Executors.newCachedThreadPool()
    private val sppUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val devices = mutableListOf<BluetoothDevice>()

    companion object {
        private const val REQ_BT = 1001
        private const val PORT = 9101
        private const val WIDTH_DOTS = 400
        private const val WIDTH_BYTES = 50
        private const val HEIGHT_DOTS = 240
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(buildUi())
        requestBluetoothPermissions()
        if (Build.VERSION.SDK_INT < 31 || hasBluetoothPermissions()) {
            refreshBondedDevices()
        }
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 55, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Printer Bridge by A Hazra"
            textSize = 22f
            setPadding(0, 0, 0, 20)
        }
        root.addView(title)

        status = TextView(this).apply {
            text = "Starting..."
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }
        root.addView(status)

        val label = TextView(this).apply {
            text = "Paired Bluetooth printer:"
            textSize = 16f
        }
        root.addView(label)

        deviceSpinner = Spinner(this)
        root.addView(
            deviceSpinner,
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        )

        connectButton = Button(this).apply {
            text = "Connect Printer"
            setOnClickListener { connectSelectedPrinter() }
        }
        root.addView(connectButton)

        serverButton = Button(this).apply {
            text = "Start Local Print Server"
            setOnClickListener {
                startLocalServer()
            }
        }
        root.addView(serverButton)

val info = TextView(this).apply {
    val textContent = """

Printer protocol:
50 × 30 mm label
400 × 240 dots at 203 DPI

Local endpoint:
http://127.0.0.1:9101/print

Keep this app open while functioning.







App is developed by Dr. Avidipta Hazra
""".trimIndent()

    val spannable = android.text.SpannableString(textContent)
    val developerText = "App is developed by Dr. Avidipta Hazra"
    val start = textContent.indexOf(developerText)

    spannable.setSpan(
        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
        start,
        start + developerText.length,
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    text = spannable
    textSize = 14f
    setPadding(0, 30, 0, 0)
}
root.addView(info)

        return root
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), REQ_BT)
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                refreshBondedDevices()
            } else {
                status.text = "Bluetooth permission denied. Allow Nearby devices permission."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            status.text = "Bluetooth is not available on this phone."
            return
        }

        try {
            devices.clear()
            devices.addAll(adapter.bondedDevices.sortedBy { it.name ?: it.address })
            val names = devices.map { "${it.name ?: "Unnamed"} (${it.address})" }

            deviceSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names.ifEmpty { listOf("No paired Bluetooth devices") }
            )

            status.text = "Select LD0801 and connect."
        } catch (e: SecurityException) {
            status.text = "Bluetooth permission is required."
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSelectedPrinter() {
        if (devices.isEmpty()) {
            refreshBondedDevices()
            Toast.makeText(this, "Pair the LD0801 in Android Bluetooth settings first.", Toast.LENGTH_LONG).show()
            return
        }

        val device = devices[deviceSpinner.selectedItemPosition.coerceIn(0, devices.lastIndex)]

        Thread {
            try {
                socket?.close()
                socket = null
                output = null

                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()

                socket = s
                output = BufferedOutputStream(s.outputStream)

                runOnUiThread {
                    status.text = "CONNECTED: ${device.name ?: device.address}"
                    connectButton.text = "Reconnect Printer"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Bluetooth connection failed: ${e.message}"
                }
            }
        }.start()
    }

    private fun startLocalServer() {
        if (serverSocket != null) {
            status.text = "Print server already running on port $PORT."
            return
        }

        httpExecutor.execute {
            try {
                serverSocket = ServerSocket(PORT, 50, java.net.InetAddress.getByName("127.0.0.1"))

                runOnUiThread {
                    status.text = "READY — printer + local server connected on :$PORT"
                    serverButton.text = "Print Server Running"
                    serverButton.isEnabled = false
                }

                while (!serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    httpExecutor.execute { handleHttp(client) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Server stopped: ${e.message}"
                }
            }
        }
    }

    private fun handleHttp(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30000

            val input = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream())

            val headerBytes = readUntilHeaderEnd(input)
            if (headerBytes == null) {
                writeHttp(out, 400, """{"error":"Bad HTTP request"}""")
                return
            }

            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val firstLine = headerText.substringBefore("\r\n")
            val method = firstLine.substringBefore(" ")
            val path = firstLine.substringAfter(" ").substringBefore(" ")

            if (method.equals("OPTIONS", true)) {
                writeHttp(out, 200, """{"ok":true}""")
                return
            }

            if (method.equals("GET", true) && path == "/") {
                writeHttp(out, 200, """{"service":"LD0801 Native Bridge","status":"ok"}""")
                return
            }

            if (!method.equals("POST", true) || path != "/print") {
                writeHttp(out, 404, """{"error":"Use POST /print"}""")
                return
            }

            val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$")
                .find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            if (contentLength <= 0 || contentLength > 2_000_000) {
                writeHttp(out, 400, """{"error":"Invalid Content-Length"}""")
                return
            }

            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n < 0) break
                read += n
            }

            if (read != contentLength) {
                writeHttp(out, 400, """{"error":"Incomplete request body"}""")
                return
            }

            try {
                val json = org.json.JSONObject(String(body, Charsets.UTF_8))
                val content = json.optJSONArray("content")
                    ?: throw IllegalArgumentException("content array missing")

                var base64: String? = null
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "image") {
                        base64 = item.optString("base64")
                        break
                    }
                }

                if (base64.isNullOrBlank()) {
                    throw IllegalArgumentException("image base64 missing")
                }

                val future: Future<*> = printExecutor.submit {
                    printBase64Png(base64!!)
                }
                future.get(60, TimeUnit.SECONDS)

                writeHttp(out, 200, """{"status":"printed"}""")
            } catch (e: Exception) {
                writeHttp(out, 500, """{"status":"error","error":${org.json.JSONObject.quote(e.message ?: "Print failed")}}""")
            }
        }
    }

    private fun readUntilHeaderEnd(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var state = 0
        while (buffer.size() < 65536) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                else -> 0
            }
            if (state == 4) return buffer.toByteArray()
        }
        return null
    }

    private fun writeHttp(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }

        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)

        out.write(headers)
        out.write(bytes)
        out.flush()
    }

    private fun printBase64Png(base64: String) {
        val bytes = Base64.getDecoder().decode(base64)
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Invalid PNG image")

        val bitmap = if (source.width == WIDTH_DOTS && source.height == HEIGHT_DOTS) {
            source
        } else {
            Bitmap.createScaledBitmap(source, WIDTH_DOTS, HEIGHT_DOTS, true)
        }

        try {
            sendDeTongLabel(bitmap)
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    private fun sendDeTongLabel(bitmap: Bitmap) {
        val out = output ?: throw IOException("Printer is not connected")

        // DeTong bitmap protocol:
        // 1B 40       initialize
        // 1F 27 01 n 88   set print width, n = (dots+7)/8
        // 1F 2A nL nH <row bytes>   print one raster row
        // 0C          locate next label boundary
        //
        // 400 dots = 50 bytes = 50 mm at 8 dots/mm.

        val header = byteArrayOf(
            0x1B, 0x40,
            0x1F, 0x27, 0x01, WIDTH_BYTES.toByte(), 0x88.toByte()
        )

        out.write(header)

        val row = ByteArray(WIDTH_BYTES)

        for (y in 0 until HEIGHT_DOTS) {
            java.util.Arrays.fill(row, 0)

            for (x in 0 until WIDTH_DOTS) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel ushr 24) and 0xFF

                val gray = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
                val black = a > 20 && gray < 180

                if (black) {
                    row[x / 8] =
                        (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }

            val command = byteArrayOf(
                0x1F, 0x2A,
                (WIDTH_DOTS and 0xFF).toByte(),
                ((WIDTH_DOTS shr 8) and 0xFF).toByte()
            )

            out.write(command)
            out.write(row)

            // Small chunks keep the Bluetooth printer buffer comfortable.
            if (y % 16 == 15) {
                out.flush()
                Thread.sleep(8)
            }
        }

        // CRITICAL: advance to the next physical die-cut label.
        out.write(byteArrayOf(0x0C))
        out.flush()

        // Give the label sensor time to position the next label.
        Thread.sleep(120)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        printExecutor.shutdownNow()
        httpExecutor.shutdownNow()
    }
}
