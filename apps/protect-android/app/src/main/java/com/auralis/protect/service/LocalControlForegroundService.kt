package com.auralis.protect.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.network.DeviceAddressReader
import com.auralis.protect.domain.model.AuralisCommand
import com.auralis.protect.domain.model.CommandSource
import com.auralis.protect.domain.usecase.CommandEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class LocalControlForegroundService : Service() {
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopLocalControl()
                stopSelf()
                START_NOT_STICKY
            }

            else -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = "Auralis Local Control",
                        text = "Starting local command panel"
                    )
                )

                startLocalControl()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        CommandEngine.execute(
            context = applicationContext,
            source = CommandSource.SYSTEM,
            command = AuralisCommand.RING_STOP,
            logEvent = false
        )
        stopLocalControl()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocalControl() {
        if (running) return

        running = true

        serverThread = Thread {
            try {
                serverSocket = ServerSocket(LocalControlStore.PORT).apply {
                    reuseAddress = true
                    soTimeout = 1000
                }

                val ip = DeviceAddressReader.primaryAddress()
                val detail = "Open http://$ip:${LocalControlStore.PORT}"

                LocalControlStore.saveStatus(
                    context = applicationContext,
                    active = true,
                    command = "PANEL STARTED",
                    detail = detail
                )

                EventLogStore.append(
                    context = applicationContext,
                    channel = CommandSource.LOCAL.label,
                    command = "PANEL STARTED",
                    detail = detail
                )

                updateNotification(
                    title = "Auralis Local Control Online",
                    text = "http://$ip:${LocalControlStore.PORT}"
                )

                while (running) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            handleClient(client)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue checking running flag.
                    }
                }
            } catch (error: Exception) {
                val detail = error.message ?: "Local command panel failed"

                LocalControlStore.saveStatus(
                    context = applicationContext,
                    active = false,
                    command = "SERVER ERROR",
                    detail = detail
                )

                EventLogStore.append(
                    context = applicationContext,
                    channel = CommandSource.LOCAL.label,
                    command = "SERVER ERROR",
                    detail = detail
                )

                updateNotification(
                    title = "Auralis Local Control Error",
                    text = detail
                )
            }
        }.apply {
            name = "AuralisLocalCommandServer"
            start()
        }
    }

    private fun stopLocalControl() {
        running = false

        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {
            // Ignore.
        }

        LocalControlStore.saveStatus(
            context = applicationContext,
            active = false,
            command = "PANEL STOPPED",
            detail = "Local Wi-Fi command panel stopped"
        )

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Service may already be stopped.
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val path = requestLine.split(" ").getOrNull(1).orEmpty()

            val response = handleRequest(path)

            val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)

            val output = client.getOutputStream()
            output.write(headers)
            output.write(bodyBytes)
            output.flush()
        }
    }

    private fun handleRequest(path: String): LocalHttpResponse {
        if (path.isBlank()) {
            return LocalHttpResponse(
                statusCode = 400,
                statusText = "Bad Request",
                body = "AURALIS LOCAL: Invalid request"
            )
        }

        val uri = Uri.parse("http://auralis.local$path")
        val route = uri.path.orEmpty()
        val token = uri.getQueryParameter("token").orEmpty()

        if (route == "/favicon.ico") {
            return LocalHttpResponse(
                statusCode = 204,
                statusText = "No Content",
                body = ""
            )
        }

        if (route == "/" || route == "/panel") {
            return LocalHttpResponse(
                statusCode = 200,
                statusText = "OK",
                body = buildControlPanelHtml(),
                contentType = "text/html; charset=utf-8"
            )
        }

        if (route == "/ping") {
            val result = CommandEngine.execute(
                context = applicationContext,
                source = CommandSource.LOCAL,
                command = AuralisCommand.PING
            )

            val ip = DeviceAddressReader.primaryAddress()

            return LocalHttpResponse(
                statusCode = 200,
                statusText = "OK",
                body = "${result.publicMessage}\nPORT: ${LocalControlStore.PORT}\nPANEL: http://$ip:${LocalControlStore.PORT}"
            )
        }

        val protectedRoutes = setOf(
            "/live",
            "/status",
            "/snapshot",
            "/report",
            "/boot",
            "/stop",
            "/ring",
            "/ring-stop"
        )

        val currentToken = LocalControlStore.readToken(applicationContext)

        if (route in protectedRoutes && token != currentToken) {
            val detail = "Local command rejected because token was missing or invalid"

            LocalControlStore.saveStatus(
                context = applicationContext,
                active = true,
                command = "REJECTED",
                detail = detail
            )

            EventLogStore.append(
                context = applicationContext,
                channel = CommandSource.LOCAL.label,
                command = "REJECTED",
                detail = detail
            )

            return LocalHttpResponse(
                statusCode = 403,
                statusText = "Forbidden",
                body = "AURALIS LOCAL: token rejected"
            )
        }

        return when (route) {
            "/live" -> openLiveBeaconRoute(token)
            "/status" -> executeLocalRoute(AuralisCommand.STATUS, "LOCAL STATUS")
            "/snapshot" -> executeLocalRoute(AuralisCommand.SNAPSHOT, "LOCAL SNAPSHOT")
            "/report" -> executeLocalRoute(AuralisCommand.REPORT, "LOCAL REPORT")
            "/boot" -> executeLocalRoute(AuralisCommand.BOOT, "LOCAL BOOT")
            "/stop" -> executeLocalRoute(AuralisCommand.STOP, "LOCAL STOP")
            "/ring" -> executeLocalRoute(AuralisCommand.RING, "LOCAL RING")
            "/ring-stop" -> executeLocalRoute(AuralisCommand.RING_STOP, "LOCAL RING STOP")
            else -> {
                LocalHttpResponse(
                    statusCode = 404,
                    statusText = "Not Found",
                    body = usageText()
                )
            }
        }
    }


    private fun openLiveBeaconRoute(token: String): LocalHttpResponse {
        val detail = "Live local view opened from local controller"

        LocalControlStore.saveStatus(
            context = applicationContext,
            active = true,
            command = "LIVE BEACON",
            detail = detail
        )

        EventLogStore.append(
            context = applicationContext,
            channel = CommandSource.LOCAL.label,
            command = "LIVE BEACON",
            detail = detail
        )

        return LocalHttpResponse(
            statusCode = 200,
            statusText = "OK",
            body = buildLiveBeaconHtml(token),
            contentType = "text/html; charset=utf-8"
        )
    }


    private fun executeLocalRoute(
        command: AuralisCommand,
        storeCommand: String
    ): LocalHttpResponse {
        val result = CommandEngine.execute(
            context = applicationContext,
            source = CommandSource.LOCAL,
            command = command
        )

        LocalControlStore.saveStatus(
            context = applicationContext,
            active = true,
            command = storeCommand,
            detail = result.detail
        )

        return LocalHttpResponse(
            statusCode = if (result.success) 200 else 500,
            statusText = if (result.success) "OK" else "Error",
            body = result.publicMessage
        )
    }


    private fun buildLiveBeaconHtml(token: String): String {
        val ip = DeviceAddressReader.primaryAddress()
        val encodedToken = Uri.encode(token)
        val status = htmlEscape(CommandEngine.statusText(applicationContext))
        val snapshot = htmlEscape(CommandEngine.snapshotText(applicationContext))
        val report = htmlEscape(CommandEngine.evidenceReportText(applicationContext))
        val baseAddress = "http://$ip:${LocalControlStore.PORT}"

        return """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <meta http-equiv="refresh" content="5" />
                <title>Auralis Live Local View</title>
                <style>
                    body {
                        margin: 0;
                        font-family: Arial, sans-serif;
                        background: linear-gradient(180deg, #031018, #020406);
                        color: #FFF7E8;
                    }
                    .wrap {
                        max-width: 760px;
                        margin: 0 auto;
                        padding: 24px;
                    }
                    .brand {
                        color: #69E7F2;
                        font-weight: 900;
                        letter-spacing: 0.08em;
                        font-size: 13px;
                    }
                    h1 {
                        margin: 8px 0 8px;
                        font-size: 34px;
                    }
                    h2 {
                        margin-top: 0;
                    }
                    p {
                        color: #D4E1E8;
                        line-height: 1.45;
                    }
                    .pill {
                        display: inline-block;
                        padding: 8px 12px;
                        border-radius: 999px;
                        background: rgba(116, 242, 166, 0.14);
                        color: #74F2A6;
                        font-weight: 900;
                        font-size: 13px;
                        margin-top: 8px;
                    }
                    .card {
                        background: rgba(11, 19, 26, 0.94);
                        border-radius: 24px;
                        padding: 18px;
                        margin-top: 16px;
                        border: 1px solid rgba(105, 231, 242, 0.16);
                    }
                    .grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 12px;
                    }
                    a.btn {
                        display: block;
                        text-decoration: none;
                        text-align: center;
                        padding: 16px;
                        border-radius: 16px;
                        font-weight: 900;
                        color: #020406;
                        background: #69E7F2;
                    }
                    a.success {
                        background: #74F2A6;
                    }
                    a.warn {
                        background: #FFD166;
                    }
                    a.danger {
                        background: #FF8A80;
                    }
                    a.dark {
                        color: #FFF7E8;
                        background: #121D25;
                        border: 1px solid rgba(158, 245, 255, 0.25);
                    }
                    a.wide {
                        grid-column: 1 / -1;
                    }
                    pre {
                        white-space: pre-wrap;
                        color: #D4E1E8;
                        background: #121D25;
                        padding: 14px;
                        border-radius: 14px;
                        overflow-x: auto;
                    }
                    .small {
                        font-size: 13px;
                        color: #8FA6B2;
                    }
                    @media (max-width: 560px) {
                        .grid {
                            grid-template-columns: 1fr;
                        }
                        a.wide {
                            grid-column: auto;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="wrap">
                    <div class="brand">AURALIS LIVE LOCAL VIEW</div>
                    <h1>Recovery Command Center</h1>
                    <p>Self-refreshing trusted-controller view. Keep this page open on another trusted phone or laptop during recovery.</p>
                    <div class="pill">Auto refresh: 5 seconds</div>

                    <div class="card">
                        <div class="grid">
                            <a class="btn success" href="/live?token=$encodedToken">Refresh Live View</a>
                            <a class="btn dark" href="/snapshot?token=$encodedToken">Snapshot Text</a>
                            <a class="btn" href="/boot?token=$encodedToken">Start Recovery</a>
                            <a class="btn danger" href="/stop?token=$encodedToken">Stop Recovery</a>
                            <a class="btn warn" href="/ring?token=$encodedToken">Ring Phone</a>
                            <a class="btn danger" href="/ring-stop?token=$encodedToken">Stop Ring</a>
                            <a class="btn wide dark" href="/report?token=$encodedToken">Evidence Report</a>
                            <a class="btn wide dark" href="/panel">Full Local Panel</a>
                        </div>
                    </div>

                    <div class="card">
                        <h2>Live Status</h2>
                        <pre>$status</pre>
                    </div>

                    <div class="card">
                        <h2>Recovery Snapshot</h2>
                        <pre>$snapshot</pre>
                        <p class="small">Live URL: $baseAddress/live?token=$token</p>
                    </div>

                    <div class="card">
                        <h2>Evidence Report</h2>
                        <pre>$report</pre>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }


    private fun buildControlPanelHtml(): String {
        val ip = DeviceAddressReader.primaryAddress()
        val status = CommandEngine.statusText(applicationContext)
        val token = LocalControlStore.readToken(applicationContext)

        return """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Auralis Local Control</title>
                <style>
                    body {
                        margin: 0;
                        font-family: Arial, sans-serif;
                        background: linear-gradient(180deg, #031018, #020406);
                        color: #FFF7E8;
                    }
                    .wrap {
                        max-width: 720px;
                        margin: 0 auto;
                        padding: 24px;
                    }
                    .brand {
                        color: #69E7F2;
                        font-weight: 800;
                        letter-spacing: 0.02em;
                    }
                    h1 {
                        margin: 8px 0 8px;
                        font-size: 34px;
                    }
                    p {
                        color: #D4E1E8;
                        line-height: 1.45;
                    }
                    .card {
                        background: rgba(11, 19, 26, 0.92);
                        border-radius: 24px;
                        padding: 18px;
                        margin-top: 16px;
                        border: 1px solid rgba(105, 231, 242, 0.16);
                    }
                    .grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 12px;
                    }
                    a.btn {
                        display: block;
                        text-decoration: none;
                        text-align: center;
                        padding: 16px;
                        border-radius: 16px;
                        font-weight: 800;
                        color: #020406;
                        background: #69E7F2;
                    }
                    a.warn {
                        background: #FFD166;
                    }
                    a.danger {
                        background: #FF8A80;
                    }
                    a.dark {
                        color: #FFF7E8;
                        background: #121D25;
                        border: 1px solid rgba(158, 245, 255, 0.25);
                    }
                    a.wide {
                        grid-column: 1 / -1;
                    }
                    pre {
                        white-space: pre-wrap;
                        color: #D4E1E8;
                        background: #121D25;
                        padding: 14px;
                        border-radius: 14px;
                        overflow-x: auto;
                    }
                    .small {
                        font-size: 13px;
                        color: #8FA6B2;
                    }
                </style>
            </head>
            <body>
                <div class="wrap">
                    <div class="brand">AURALIS LOCAL</div>
                    <h1>Protect Control Panel</h1>
                    <p>Local Wi-Fi / hotspot command panel for live recovery actions.</p>

                    <div class="card">
                        <div class="grid">
                            <a class="btn" href="/boot?token=$token">Start Recovery</a>
                            <a class="btn danger" href="/stop?token=$token">Stop Recovery</a>
                            <a class="btn warn" href="/ring?token=$token">Ring Phone</a>
                            <a class="btn danger" href="/ring-stop?token=$token">Stop Ring</a>
                            <a class="btn dark" href="/status?token=$token">Status Text</a>
                            <a class="btn dark" href="/ping">Ping</a>
                            <a class="btn wide" href="/live?token=$token">Live Local Command Center</a>
                            <a class="btn wide" href="/snapshot?token=$token">Recovery Snapshot + Maps</a>
                            <a class="btn wide dark" href="/report?token=$token">Evidence Timeline Report</a>
                        </div>
                    </div>

                    <div class="card">
                        <h2>Status</h2>
                        <pre>$status</pre>
                        <p class="small">Panel URL: http://$ip:${LocalControlStore.PORT}</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun usageText(): String {
        val ip = DeviceAddressReader.primaryAddress()
        val token = LocalControlStore.readToken(applicationContext)

        return """
            AURALIS LOCAL CONTROL
            Panel: http://$ip:${LocalControlStore.PORT}
            Ping: http://$ip:${LocalControlStore.PORT}/ping
            Status: http://$ip:${LocalControlStore.PORT}/status?token=$token
            Live Local View: http://$ip:${LocalControlStore.PORT}/live?token=$token
            Snapshot: http://$ip:${LocalControlStore.PORT}/snapshot?token=$token
            Evidence Report: http://$ip:${LocalControlStore.PORT}/report?token=$token
            Boot: http://$ip:${LocalControlStore.PORT}/boot?token=$token
            Stop: http://$ip:${LocalControlStore.PORT}/stop?token=$token
            Ring: http://$ip:${LocalControlStore.PORT}/ring?token=$token
            Stop Ring: http://$ip:${LocalControlStore.PORT}/ring-stop?token=$token
        """.trimIndent()
    }


    private fun htmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }


    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(title = title, text = text)
        )
    }

    private fun buildNotification(title: String, text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auralis Local Control",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Local Wi-Fi and hotspot command panel for Auralis Protect"
            setShowBadge(true)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.auralis.protect.action.START_LOCAL_CONTROL"
        const val ACTION_STOP = "com.auralis.protect.action.STOP_LOCAL_CONTROL"

        private const val CHANNEL_ID = "auralis_local_control_channel"
        private const val NOTIFICATION_ID = 4730
    }
}

data class LocalHttpResponse(
    val statusCode: Int,
    val statusText: String,
    val body: String,
    val contentType: String = "text/plain; charset=utf-8"
)
