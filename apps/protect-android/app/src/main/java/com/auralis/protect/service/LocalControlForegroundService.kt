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
                body = buildControlPanelHtml(token),
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


    private fun buildControlPanelHtml(initialToken: String): String {
        val ip = DeviceAddressReader.primaryAddress()
        val currentToken = LocalControlStore.readToken(applicationContext)
        val initialStatus = if (initialToken == currentToken) {
            CommandEngine.statusText(applicationContext)
        } else {
            "Enter the local control token, then press Refresh Status."
        }
        val status = htmlEscape(initialStatus)
        val tokenSeed = htmlEscape(initialToken)

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
                    .tokenRow {
                        display: grid;
                        grid-template-columns: 1fr auto;
                        gap: 12px;
                        align-items: end;
                        margin-top: 12px;
                    }
                    label {
                        display: block;
                        color: #8FA6B2;
                        font-size: 13px;
                        font-weight: 800;
                        margin-bottom: 8px;
                    }
                    input {
                        width: 100%;
                        box-sizing: border-box;
                        padding: 15px;
                        border-radius: 16px;
                        border: 1px solid rgba(158, 245, 255, 0.25);
                        background: #121D25;
                        color: #FFF7E8;
                        font-size: 16px;
                    }
                    input:focus {
                        outline: none;
                        border-color: #69E7F2;
                        box-shadow: 0 0 0 3px rgba(105, 231, 242, 0.12);
                    }
                    button, a.btn {
                        display: block;
                        border: 0;
                        cursor: pointer;
                        min-height: 52px;
                        text-decoration: none;
                        text-align: center;
                        padding: 16px;
                        border-radius: 16px;
                        font: inherit;
                        font-weight: 800;
                        color: #020406;
                        background: #69E7F2;
                    }
                    button:disabled {
                        cursor: wait;
                        filter: grayscale(0.4) brightness(0.75);
                    }
                    .warn {
                        background: #FFD166;
                    }
                    .danger {
                        background: #FF8A80;
                    }
                    .success {
                        background: #74F2A6;
                    }
                    .dark {
                        color: #FFF7E8;
                        background: #121D25;
                        border: 1px solid rgba(158, 245, 255, 0.25);
                    }
                    .wide {
                        grid-column: 1 / -1;
                    }
                    .outputHead {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        gap: 12px;
                        margin-bottom: 10px;
                    }
                    .state {
                        border-radius: 999px;
                        padding: 8px 11px;
                        background: rgba(143, 166, 178, 0.12);
                        color: #8FA6B2;
                        font-size: 12px;
                        font-weight: 900;
                    }
                    .state.loading {
                        background: rgba(255, 209, 102, 0.12);
                        color: #FFD166;
                    }
                    .state.success {
                        background: rgba(116, 242, 166, 0.12);
                        color: #74F2A6;
                    }
                    .state.error {
                        background: rgba(255, 138, 128, 0.12);
                        color: #FF8A80;
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
                        .grid,
                        .tokenRow {
                            grid-template-columns: 1fr;
                        }
                        .wide {
                            grid-column: auto;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="wrap">
                    <div class="brand">AURALIS PROTECT LOCAL</div>
                    <h1>Protected Device Dashboard</h1>
                    <p>Local Wi-Fi / hotspot dashboard for trusted recovery actions. Commands run in place and report back below.</p>

                    <div class="card">
                        <h2>Access Token</h2>
                        <div class="tokenRow">
                            <div>
                                <label for="token">Local control token</label>
                                <input id="token" type="password" value="$tokenSeed" autocomplete="off" placeholder="Enter token from Auralis Protect" />
                            </div>
                            <button class="dark" type="button" id="rememberToken">Remember</button>
                        </div>
                        <p class="small">Protected routes still require the editable local token. This page does not reveal the saved token by itself.</p>
                    </div>

                    <div class="card">
                        <h2>Controls</h2>
                        <div class="grid">
                            <button type="button" data-route="/boot">Start Recovery</button>
                            <button class="danger" type="button" data-route="/stop">Stop Recovery</button>
                            <button class="warn" type="button" data-route="/ring">Ring Phone</button>
                            <button class="danger" type="button" data-route="/ring-stop">Stop Ring</button>
                            <button class="dark" type="button" data-route="/status">Refresh Status</button>
                            <button class="dark" type="button" data-route="/ping" data-public="true">Ping Panel</button>
                            <button class="wide success" type="button" id="openLive">Open Live Local View</button>
                            <button class="wide dark" type="button" data-route="/snapshot">Recovery Snapshot + Maps</button>
                            <button class="wide dark" type="button" data-route="/report">Evidence Timeline Report</button>
                        </div>
                    </div>

                    <div class="card">
                        <div class="outputHead">
                            <h2>Output</h2>
                            <span id="state" class="state">Ready</span>
                        </div>
                        <pre id="output">$status</pre>
                        <p class="small">Panel URL: http://$ip:${LocalControlStore.PORT}</p>
                    </div>
                </div>
                <script>
                    const tokenInput = document.getElementById('token');
                    const output = document.getElementById('output');
                    const state = document.getElementById('state');
                    const buttons = Array.from(document.querySelectorAll('button[data-route]'));
                    const remembered = localStorage.getItem('auralisLocalToken') || '';
                    const urlToken = new URLSearchParams(window.location.search).get('token') || '';

                    if (!tokenInput.value && urlToken) {
                        tokenInput.value = urlToken;
                    } else if (!tokenInput.value && remembered) {
                        tokenInput.value = remembered;
                    }

                    function setState(kind, text) {
                        state.className = 'state ' + kind;
                        state.textContent = text;
                    }

                    function setBusy(busy) {
                        buttons.forEach((button) => button.disabled = busy);
                    }

                    function protectedUrl(route) {
                        const token = tokenInput.value.trim();
                        if (!token) {
                            throw new Error('Enter the local control token first.');
                        }
                        localStorage.setItem('auralisLocalToken', token);
                        return route + '?token=' + encodeURIComponent(token);
                    }

                    async function requestRoute(route, isPublic) {
                        try {
                            setBusy(true);
                            setState('loading', 'Loading');
                            output.textContent = 'Contacting protected device...';

                            const target = isPublic ? route : protectedUrl(route);
                            const response = await fetch(target, { cache: 'no-store' });
                            const text = await response.text();

                            output.textContent = text || response.status + ' ' + response.statusText;
                            setState(response.ok ? 'success' : 'error', response.ok ? 'Success' : 'Error');
                        } catch (error) {
                            output.textContent = error.message || 'Request failed';
                            setState('error', 'Error');
                        } finally {
                            setBusy(false);
                        }
                    }

                    buttons.forEach((button) => {
                        button.addEventListener('click', () => {
                            requestRoute(button.dataset.route, button.dataset.public === 'true');
                        });
                    });

                    document.getElementById('rememberToken').addEventListener('click', () => {
                        const token = tokenInput.value.trim();
                        if (token) {
                            localStorage.setItem('auralisLocalToken', token);
                            setState('success', 'Saved');
                            output.textContent = 'Local token saved in this browser.';
                        } else {
                            setState('error', 'Missing token');
                            output.textContent = 'Enter a token before saving.';
                        }
                    });

                    document.getElementById('openLive').addEventListener('click', () => {
                        try {
                            window.location.href = protectedUrl('/live');
                        } catch (error) {
                            output.textContent = error.message || 'Token required';
                            setState('error', 'Error');
                        }
                    });
                </script>
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
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
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
