package com.auralis.protect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.data.location.LocationReader
import com.auralis.protect.data.network.DeviceAddressReader
import com.auralis.protect.design.theme.AuralisColors
import com.auralis.protect.domain.model.AuralisCommand
import com.auralis.protect.domain.model.CommandSource
import com.auralis.protect.domain.usecase.CommandEngine
import com.auralis.protect.feature.dashboard.AuralisDashboardScreen
import com.auralis.protect.service.LocalControlForegroundService

private val AuralisDarkColorScheme = darkColorScheme(
    primary = AuralisColors.Cyan,
    onPrimary = Color(0xFF001014),
    secondary = AuralisColors.Success,
    onSecondary = Color(0xFF00160A),
    background = AuralisColors.DeepBlack,
    onBackground = AuralisColors.TextPrimary,
    surface = AuralisColors.CardSoft,
    onSurface = AuralisColors.TextPrimary,
    surfaceVariant = Color(0xFF132029),
    onSurfaceVariant = AuralisColors.TextSecondary,
    outline = Color(0xFFD8D1DE),
    outlineVariant = Color(0xFF827988),
    error = AuralisColors.Danger,
    onError = Color(0xFF2B0000)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = AuralisDarkColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AuralisColors.DeepBlack
                ) {
                    AuralisDashboardScreen(
                        onStartRecoveryService = {
                            val result = CommandEngine.execute(
                                context = applicationContext,
                                source = CommandSource.MANUAL,
                                command = AuralisCommand.BOOT
                            )

                            Toast.makeText(
                                this,
                                result.publicMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onStopRecoveryService = {
                            val result = CommandEngine.execute(
                                context = applicationContext,
                                source = CommandSource.MANUAL,
                                command = AuralisCommand.STOP
                            )

                            Toast.makeText(
                                this,
                                result.publicMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onStartLocalControlService = { startLocalControlService() },
                        onStopLocalControlService = { stopLocalControlService() },
                        onShareControllerLink = { shareControllerLink() },
                        onShareLiveBeaconLink = { shareLiveBeaconLink() },
                        onShareRecoverySnapshot = { shareRecoverySnapshot() },
                        onShareEvidenceReport = { shareEvidenceReport() },
                        onStartRing = {
                            val result = CommandEngine.execute(
                                context = applicationContext,
                                source = CommandSource.MANUAL,
                                command = AuralisCommand.RING
                            )

                            Toast.makeText(
                                this,
                                result.publicMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onStopRing = {
                            val result = CommandEngine.execute(
                                context = applicationContext,
                                source = CommandSource.MANUAL,
                                command = AuralisCommand.RING_STOP
                            )

                            Toast.makeText(
                                this,
                                result.publicMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    private fun startLocalControlService() {
        try {
            LocalControlStore.saveStatus(
                context = applicationContext,
                active = false,
                command = "PANEL STARTING",
                detail = "Local Wi-Fi command panel is starting"
            )

            val intent = Intent(this, LocalControlForegroundService::class.java).apply {
                action = LocalControlForegroundService.ACTION_START
            }

            ContextCompat.startForegroundService(this, intent)

            Toast.makeText(
                this,
                "Auralis local control starting",
                Toast.LENGTH_SHORT
            ).show()
        } catch (error: Exception) {
            LocalControlStore.saveStatus(
                context = applicationContext,
                active = false,
                command = "PANEL FAILED",
                detail = error.message ?: "Local control could not start"
            )

            Toast.makeText(
                this,
                "Local control failed: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopLocalControlService() {
        try {
            val intent = Intent(this, LocalControlForegroundService::class.java).apply {
                action = LocalControlForegroundService.ACTION_STOP
            }

            startService(intent)
            LocalControlStore.saveStatus(
                context = applicationContext,
                active = false,
                command = "PANEL STOPPED",
                detail = "Local Wi-Fi command panel stopped"
            )

            Toast.makeText(
                this,
                "Auralis local control stopped",
                Toast.LENGTH_SHORT
            ).show()
        } catch (error: Exception) {
            Toast.makeText(
                this,
                "Local control stop failed: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareControllerLink() {
        val ip = DeviceAddressReader.primaryAddress()
        val token = LocalControlStore.readToken(applicationContext)

        val body = if (ip == "unavailable") {
            """
                Auralis Protect Local Controller

                The protected device is not showing a Wi-Fi / hotspot IP address yet.

                1. Connect the protected phone to Wi-Fi or hotspot.
                2. Open Auralis Protect.
                3. Start the Local Wi-Fi Control panel.
                4. Share the controller link again.

                Current local token: $token
            """.trimIndent()
        } else {
            val baseAddress = "http://$ip:${LocalControlStore.PORT}"

            """
                Auralis Protect Local Controller

                Use this only with a trusted controller device on the same Wi-Fi or hotspot.

                Panel:
                $baseAddress

                Direct commands:
                Status: $baseAddress/status?token=$token
                Live Local View: $baseAddress/live?token=$token
                Snapshot: $baseAddress/snapshot?token=$token
                Evidence Report: $baseAddress/report?token=$token
                Start Recovery: $baseAddress/boot?token=$token
                Stop Recovery: $baseAddress/stop?token=$token
                Ring Phone: $baseAddress/ring?token=$token
                Stop Ring: $baseAddress/ring-stop?token=$token

                Token: $token
            """.trimIndent()
        }

        shareText(
            chooserTitle = "Share Auralis controller link",
            subject = "Auralis Protect Local Controller",
            body = body
        )
    }


    private fun shareLiveBeaconLink() {
        val ip = DeviceAddressReader.primaryAddress()
        val token = LocalControlStore.readToken(applicationContext)
        val localStatus = LocalControlStore.readStatus(applicationContext)

        val body = if (ip == "unavailable") {
            """
                Auralis Protect Live Local View

                The protected device is not showing a Wi-Fi / hotspot IP address yet.

                1. Connect the protected phone to Wi-Fi or hotspot.
                2. Open Auralis Protect.
                3. Start the Local Wi-Fi Control panel.
                4. Share the live local view again.

                Current local token: $token
            """.trimIndent()
        } else {
            val baseAddress = "http://$ip:${LocalControlStore.PORT}"
            val panelLine = if (localStatus.active) {
                "The Local Wi-Fi Control panel is currently online."
            } else {
                "Start the Local Wi-Fi Control panel on the protected phone before opening this link."
            }

            """
                Auralis Protect Live Local View

                $panelLine

                Live Local View:
                $baseAddress/live?token=$token

                Recovery Snapshot:
                $baseAddress/snapshot?token=$token

                Evidence Report:
                $baseAddress/report?token=$token

                Full Panel:
                $baseAddress

                Token: $token
            """.trimIndent()
        }

        shareText(
            chooserTitle = "Share Auralis live local view",
            subject = "Auralis Protect Live Local View",
            body = body
        )
    }


    private fun shareRecoverySnapshot() {
        val snapshot = CommandEngine.snapshotText(applicationContext)
        val location = LocationReader.readLastKnownLocation(applicationContext)
        val body = buildString {
            append(snapshot)
            if (location.mapsUrl != null) {
                append("\n\nOpen map:\n")
                append(location.mapsUrl)
            }
        }

        shareText(
            chooserTitle = "Share Auralis recovery snapshot",
            subject = "Auralis Protect Recovery Snapshot",
            body = body
        )
    }



    private fun shareEvidenceReport() {
        val body = CommandEngine.evidenceReportText(applicationContext)

        shareText(
            chooserTitle = "Share Auralis evidence report",
            subject = "Auralis Protect Evidence Report",
            body = body
        )
    }

    private fun shareText(
        chooserTitle: String,
        subject: String,
        body: String
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    chooserTitle
                )
            )
        } catch (error: Exception) {
            Toast.makeText(
                this,
                "Share failed: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

}
