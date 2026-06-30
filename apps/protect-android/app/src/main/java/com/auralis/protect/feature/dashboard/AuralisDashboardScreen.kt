package com.auralis.protect.feature.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auralis.protect.core.permissions.NotificationPermission
import com.auralis.protect.core.permissions.SmsPermission
import com.auralis.protect.data.battery.BatteryReader
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.data.location.LocationReader
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.network.DeviceAddressReader
import com.auralis.protect.data.network.NetworkReader
import com.auralis.protect.data.recovery.RecoveryStateStore
import com.auralis.protect.data.settings.TrustedSenderStore
import com.auralis.protect.data.sms.SmsCommandStore
import com.auralis.protect.design.theme.AuralisColors
import com.auralis.protect.domain.model.CommandChannelDefaults
import com.auralis.protect.domain.usecase.CommandEngine
import com.auralis.protect.feature.dashboard.components.AdvancedToolsPanel
import com.auralis.protect.feature.dashboard.components.DashboardTab
import com.auralis.protect.feature.dashboard.components.DashboardTabs
import com.auralis.protect.feature.dashboard.components.DeviceStatusPanel
import com.auralis.protect.feature.dashboard.components.EmergencySmsPanel
import com.auralis.protect.feature.dashboard.components.EventLogCard
import com.auralis.protect.feature.dashboard.components.HeaderSection
import com.auralis.protect.feature.dashboard.components.LocalControlCard
import com.auralis.protect.feature.dashboard.components.NotificationPermissionCard
import com.auralis.protect.feature.recovery.RecoveryControls
import kotlinx.coroutines.delay

@Composable
fun AuralisDashboardScreen(
    onStartRecoveryService: () -> Unit,
    onStopRecoveryService: () -> Unit,
    onStartLocalControlService: () -> Unit,
    onStopLocalControlService: () -> Unit,
    onShareControllerLink: () -> Unit = {},
    onShareLiveBeaconLink: () -> Unit = {},
    onShareRecoverySnapshot: () -> Unit = {},
    onShareEvidenceReport: () -> Unit = {},
    onStartRing: () -> Unit,
    onStopRing: () -> Unit
) {
    val context = LocalContext.current
    val initialRuntimeState = remember { RecoveryStateStore.read(context) }

    var selectedTab by remember { mutableStateOf(DashboardTab.Controls) }
    var recoveryActive by remember { mutableStateOf(initialRuntimeState.recoveryActive) }
    var ringActive by remember { mutableStateOf(initialRuntimeState.ringActive) }
    var batteryPercent by remember { mutableIntStateOf(BatteryReader.readBatteryPercent(context)) }
    var networkStatus by remember { mutableStateOf(NetworkReader.read(context)) }
    var locationSnapshot by remember { mutableStateOf(LocationReader.readLastKnownLocation(context)) }
    var notificationsAllowed by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    var smsReceiveGranted by remember { mutableStateOf(SmsPermission.canReceive(context)) }
    var smsSendGranted by remember { mutableStateOf(SmsPermission.canSend(context)) }
    var smsCommandStatus by remember { mutableStateOf(SmsCommandStore.readStatus(context)) }
    var trustedSender by remember { mutableStateOf(TrustedSenderStore.readTrustedSender(context)) }
    var draftTrustedSender by remember { mutableStateOf(trustedSender) }
    var localControlStatus by remember { mutableStateOf(LocalControlStore.readStatus(context)) }
    var primaryIpAddress by remember { mutableStateOf(DeviceAddressReader.primaryAddress()) }
    var localToken by remember { mutableStateOf(LocalControlStore.readToken(context)) }
    var draftLocalToken by remember { mutableStateOf(localToken) }
    var eventLogs by remember { mutableStateOf(EventLogStore.readEvents(context)) }

    fun refreshDashboardState() {
        CommandEngine.reconcileRuntimeState(context)
        val runtimeState = RecoveryStateStore.read(context)

        batteryPercent = BatteryReader.readBatteryPercent(context)
        networkStatus = NetworkReader.read(context)
        locationSnapshot = LocationReader.readLastKnownLocation(context)
        recoveryActive = runtimeState.recoveryActive
        ringActive = runtimeState.ringActive
        notificationsAllowed = NotificationPermission.isGranted(context)
        smsReceiveGranted = SmsPermission.canReceive(context)
        smsSendGranted = SmsPermission.canSend(context)
        smsCommandStatus = SmsCommandStore.readStatus(context)
        trustedSender = TrustedSenderStore.readTrustedSender(context)
        localControlStatus = LocalControlStore.readStatus(context)
        primaryIpAddress = DeviceAddressReader.primaryAddress()
        localToken = LocalControlStore.readToken(context)
        eventLogs = EventLogStore.readEvents(context)
    }

    fun startRecoveryIfReady() {
        if (!LocationReader.hasLocationPermission(context)) return
        if (!NotificationPermission.isGranted(context)) return
        recoveryActive = true
        onStartRecoveryService()
        refreshDashboardState()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { locationSnapshot = LocationReader.readLastKnownLocation(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        notificationsAllowed = NotificationPermission.isGranted(context)
        startRecoveryIfReady()
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsReceiveGranted = SmsPermission.canReceive(context)
        smsSendGranted = SmsPermission.canSend(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshDashboardState()
            delay(1000)
        }
    }

    val smsReady = smsReceiveGranted && smsSendGranted && TrustedSenderStore.hasTrustedSender(context)
    val channelStatuses = CommandChannelDefaults.statuses(
        smsReady = smsReady,
        localControlReady = localControlStatus.active
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuralisColors.BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderSection(
                recoveryActive = recoveryActive,
                ringActive = ringActive,
                batteryPercent = batteryPercent,
                smsReady = smsReady
            )

            DashboardTabs(
                selected = selectedTab,
                onSelected = { selectedTab = it }
            )

            if (!notificationsAllowed) {
                NotificationPermissionCard(
                    onAllowNotifications = {
                        notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
                    }
                )
            }

            when (selectedTab) {
                DashboardTab.Controls -> {
                    RecoveryControls(
                        recoveryActive = recoveryActive,
                        ringActive = ringActive,
                        onStartRecovery = {
                            when {
                                !LocationReader.hasLocationPermission(context) -> {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }

                                !NotificationPermission.isGranted(context) -> {
                                    notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
                                }

                                else -> {
                                    recoveryActive = true
                                    onStartRecoveryService()
                                    refreshDashboardState()
                                }
                            }
                        },
                        onStopRecovery = {
                            recoveryActive = false
                            ringActive = false
                            onStopRing()
                            onStopRecoveryService()
                            refreshDashboardState()
                        },
                        onStartRing = {
                            ringActive = true
                            onStartRing()
                            refreshDashboardState()
                        },
                        onStopRing = {
                            ringActive = false
                            onStopRing()
                            refreshDashboardState()
                        }
                    )

                    LocalControlCard(
                        localStatus = localControlStatus,
                        primaryIpAddress = primaryIpAddress,
                        localToken = localToken,
                        onStartLocalControl = {
                            if (!NotificationPermission.isGranted(context)) {
                                notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
                            } else {
                                onStartLocalControlService()
                                refreshDashboardState()
                            }
                        },
                        onStopLocalControl = {
                            onStopLocalControlService()
                            refreshDashboardState()
                        }
                    )
                }

                DashboardTab.Status -> {
                    DeviceStatusPanel(
                        batteryPercent = batteryPercent,
                        networkStatus = networkStatus,
                        recoveryActive = recoveryActive,
                        ringActive = ringActive,
                        locationSnapshot = locationSnapshot,
                        onRequestPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onRefreshLocation = {
                            if (!LocationReader.hasLocationPermission(context)) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                locationSnapshot = LocationReader.readLastKnownLocation(context)
                                EventLogStore.append(
                                    context = context,
                                    channel = "MANUAL",
                                    command = "LOCATION REFRESH",
                                    detail = "Manual location refresh from dashboard"
                                )
                                eventLogs = EventLogStore.readEvents(context)
                            }
                        }
                    )
                }

                DashboardTab.Sms -> {
                    EmergencySmsPanel(
                        receivePermissionGranted = smsReceiveGranted,
                        sendPermissionGranted = smsSendGranted,
                        trustedSender = trustedSender,
                        draftTrustedSender = draftTrustedSender,
                        onDraftChanged = { draftTrustedSender = it },
                        onSaveTrustedSender = {
                            TrustedSenderStore.saveTrustedSender(context, draftTrustedSender)
                            trustedSender = TrustedSenderStore.readTrustedSender(context)
                            draftTrustedSender = trustedSender
                            EventLogStore.append(
                                context = context,
                                channel = "SETTINGS",
                                command = "TRUSTED SENDER",
                                detail = "Trusted controller number updated"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        onClearTrustedSender = {
                            TrustedSenderStore.clearTrustedSender(context)
                            trustedSender = ""
                            draftTrustedSender = ""
                            EventLogStore.append(
                                context = context,
                                channel = "SETTINGS",
                                command = "TRUSTED REMOVED",
                                detail = "Trusted controller number removed"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        smsCommandStatus = smsCommandStatus,
                        onRequestSmsPermissions = {
                            smsPermissionLauncher.launch(
                                arrayOf(
                                    SmsPermission.RECEIVE_PERMISSION,
                                    SmsPermission.SEND_PERMISSION
                                )
                            )
                        }
                    )
                }

                DashboardTab.Log -> {
                    EventLogCard(
                        events = eventLogs,
                        onClearLogs = {
                            EventLogStore.clear(context)
                            eventLogs = EventLogStore.readEvents(context)
                        }
                    )
                }

                DashboardTab.Advanced -> {
                    AdvancedToolsPanel(
                        localControlActive = localControlStatus.active,
                        primaryIpAddress = primaryIpAddress,
                        localToken = localToken,
                        draftLocalToken = draftLocalToken,
                        onDraftLocalTokenChanged = { draftLocalToken = it },
                        onSaveLocalToken = {
                            LocalControlStore.saveToken(context, draftLocalToken)
                            localToken = LocalControlStore.readToken(context)
                            draftLocalToken = localToken
                            EventLogStore.append(
                                context = context,
                                channel = "SETTINGS",
                                command = "LOCAL TOKEN",
                                detail = "Local command token updated"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        onResetLocalToken = {
                            LocalControlStore.resetToken(context)
                            localToken = LocalControlStore.readToken(context)
                            draftLocalToken = localToken
                            EventLogStore.append(
                                context = context,
                                channel = "SETTINGS",
                                command = "LOCAL TOKEN RESET",
                                detail = "Local command token reset to default"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        recoveryActive = recoveryActive,
                        locationSnapshot = locationSnapshot,
                        eventCount = eventLogs.size,
                        channels = channelStatuses,
                        onShareControllerLink = {
                            onShareControllerLink()
                            EventLogStore.append(
                                context = context,
                                channel = "SHARE",
                                command = "CONTROLLER LINK",
                                detail = "Trusted local controller link share sheet opened"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        onShareLiveBeaconLink = {
                            onShareLiveBeaconLink()
                            EventLogStore.append(
                                context = context,
                                channel = "SHARE",
                                command = "LIVE VIEW",
                                detail = "Live local view share sheet opened"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        onShareRecoverySnapshot = {
                            locationSnapshot = LocationReader.readLastKnownLocation(context)
                            onShareRecoverySnapshot()
                            EventLogStore.append(
                                context = context,
                                channel = "SHARE",
                                command = "SNAPSHOT",
                                detail = "Recovery snapshot share sheet opened"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        },
                        onShareEvidenceReport = {
                            locationSnapshot = LocationReader.readLastKnownLocation(context)
                            onShareEvidenceReport()
                            EventLogStore.append(
                                context = context,
                                channel = "SHARE",
                                command = "EVIDENCE REPORT",
                                detail = "Evidence timeline report share sheet opened"
                            )
                            eventLogs = EventLogStore.readEvents(context)
                        }
                    )
                }
            }
        }
    }
}
