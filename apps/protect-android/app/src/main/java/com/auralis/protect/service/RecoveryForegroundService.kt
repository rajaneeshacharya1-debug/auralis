package com.auralis.protect.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.auralis.protect.data.location.LocationReader
import com.auralis.protect.data.location.LocationStore
import com.auralis.protect.data.location.LocationTrailStore
import com.auralis.protect.data.recovery.RecoveryStateStore

class RecoveryForegroundService : Service() {
    private var locationManager: LocationManager? = null

    private val locationListener = LocationListener { location: Location ->
        LocationStore.saveLatestLocation(applicationContext, location)
        LocationTrailStore.recordIfRecoveryActive(
            context = applicationContext,
            location = location,
            source = "RECOVERY_SERVICE"
        )
        RecoveryStateStore.markHeartbeat(
            context = applicationContext,
            detail = "Fresh location fix saved by recovery service"
        )
        updateNotification("Auralis Recovery Active", "Location refreshed ${LocationReader.ageText(System.currentTimeMillis())}")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopRecovery()
                stopSelf()
                START_NOT_STICKY
            }

            else -> {
                LocationStore.setRecoveryActive(applicationContext, true)
                RecoveryStateStore.markRecovery(
                    context = applicationContext,
                    active = true,
                    source = "SERVICE",
                    detail = "Foreground recovery service running"
                )

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = "Auralis Recovery Active",
                        text = "Tracking this phone for recovery"
                    )
                )

                startLocationUpdates()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopRecovery()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            updateNotification(
                title = "Auralis Recovery Needs Location",
                text = "Open the app and allow location permission"
            )
            return
        }

        val manager = locationManager ?: return

        try {
            publishBestKnownLocation(manager)

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    LOCATION_MIN_DISTANCE_METERS,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    LOCATION_MIN_DISTANCE_METERS,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            updateNotification(
                title = "Auralis Recovery Active",
                text = "Live location requested every 2 seconds"
            )
        } catch (_: SecurityException) {
            updateNotification(
                title = "Auralis Recovery Needs Permission",
                text = "Location permission was not granted"
            )
        } catch (_: Exception) {
            updateNotification(
                title = "Auralis Recovery Error",
                text = "Location updates could not be started"
            )
        }
    }

    private fun publishBestKnownLocation(manager: LocationManager) {
        val best = LocationReader.bestLastKnownLocation(manager) ?: return
        LocationStore.saveLatestLocation(applicationContext, best)
        LocationTrailStore.recordIfRecoveryActive(
            context = applicationContext,
            location = best,
            source = "RECOVERY_START"
        )
        RecoveryStateStore.markHeartbeat(
            context = applicationContext,
            detail = "Best available location published when recovery started"
        )
    }

    private fun stopRecovery() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
            // Later: route this to Auralis event logs.
        }

        LocationStore.setRecoveryActive(applicationContext, false)
        RecoveryStateStore.markRecovery(
            context = applicationContext,
            active = false,
            source = "SERVICE",
            detail = "Foreground recovery service stopped"
        )

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Service may already be stopped.
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
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
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auralis Recovery",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Foreground recovery mode for Auralis Protect"
            setShowBadge(true)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.auralis.protect.action.START_RECOVERY"
        const val ACTION_STOP = "com.auralis.protect.action.STOP_RECOVERY"

        private const val CHANNEL_ID = "auralis_recovery_channel"
        private const val NOTIFICATION_ID = 4729

        private const val LOCATION_INTERVAL_MS = 2000L
        private const val LOCATION_MIN_DISTANCE_METERS = 0f
    }
}
