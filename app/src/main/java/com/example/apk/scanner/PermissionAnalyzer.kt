package com.example.apk.scanner

import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanPermissionInfo

object PermissionAnalyzer {

    data class PermissionDef(
        val category: ProtectionCategory,
        val risk: RiskLevel,
        val reason: String,
        val description: String
    )

    private val PERMISSION_REGISTRY: Map<String, PermissionDef> = mapOf(
        // DANGEROUS (Runtime permissions)
        "android.permission.READ_CALENDAR" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants read access to user calendar events and schedule data.",
            "Allows an application to read the user's calendar data."
        ),
        "android.permission.WRITE_CALENDAR" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants write access to modify, add, or delete user calendar events.",
            "Allows an application to add or change calendar events."
        ),
        "android.permission.CAMERA" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants direct access to the camera hardware to capture photos or video.",
            "Required to be able to access the camera device."
        ),
        "android.permission.READ_CONTACTS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants read access to all stored contacts and address book data.",
            "Allows an application to read the user's contacts data."
        ),
        "android.permission.WRITE_CONTACTS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to modify or delete contacts in the user address book.",
            "Allows an application to modify the user's contacts data."
        ),
        "android.permission.GET_ACCOUNTS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to account credentials and account lists on device.",
            "Allows access to the list of accounts in the Accounts Service."
        ),
        "android.permission.ACCESS_FINE_LOCATION" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants precise GPS location tracking of the user device.",
            "Allows an app to access precise location from GPS and cell towers."
        ),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants approximate location tracking based on cell towers and Wi-Fi.",
            "Allows an app to access approximate location."
        ),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Allows continuous location tracking even when app is not in foreground.",
            "Allows an app to access location in the background."
        ),
        "android.permission.RECORD_AUDIO" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to record raw audio from the microphone hardware.",
            "Allows an application to record audio."
        ),
        "android.permission.READ_PHONE_STATE" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to phone state, cellular network status, and device IDs.",
            "Allows read only access to phone state, including phone number and carrier."
        ),
        "android.permission.READ_PHONE_NUMBERS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to device phone numbers.",
            "Allows read access to the device's phone numbers."
        ),
        "android.permission.CALL_PHONE" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Allows initiating phone calls without going through the Dialer user interface.",
            "Allows an application to initiate a phone call without dialing confirmation."
        ),
        "android.permission.ANSWER_PHONE_CALLS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Allows answering incoming phone calls automatically.",
            "Allows the app to answer an incoming phone call."
        ),
        "android.permission.READ_CALL_LOG" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants access to the full incoming/outgoing phone call history.",
            "Allows an application to read the user's call log."
        ),
        "android.permission.WRITE_CALL_LOG" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Allows modifying or erasing phone call logs.",
            "Allows an application to write the user's call log data."
        ),
        "android.permission.SEND_SMS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants permission to transmit SMS messages which may incur charges.",
            "Allows an application to send SMS messages."
        ),
        "android.permission.RECEIVE_SMS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants permission to intercept incoming SMS messages (possible 2FA interception).",
            "Allows an application to receive SMS messages."
        ),
        "android.permission.READ_SMS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants permission to read stored SMS text messages.",
            "Allows an application to read SMS messages."
        ),
        "android.permission.RECEIVE_WAP_PUSH" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants permission to monitor incoming WAP push messages.",
            "Allows an application to receive WAP push messages."
        ),
        "android.permission.RECEIVE_MMS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.CRITICAL,
            "Grants permission to monitor incoming MMS messages.",
            "Allows an application to monitor incoming MMS messages."
        ),
        "android.permission.READ_EXTERNAL_STORAGE" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants read access to shared external storage files.",
            "Allows an application to read from external storage."
        ),
        "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants write access to shared external storage files.",
            "Allows an application to write to external storage."
        ),
        "android.permission.READ_MEDIA_IMAGES" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants read access to user images on device storage.",
            "Allows an application to read image files from external storage."
        ),
        "android.permission.READ_MEDIA_VIDEO" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants read access to user videos on device storage.",
            "Allows an application to read video files from external storage."
        ),
        "android.permission.READ_MEDIA_AUDIO" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Grants read access to user audio recordings on device storage.",
            "Allows an application to read audio files from external storage."
        ),
        "android.permission.BODY_SENSORS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.HIGH,
            "Grants access to heart rate and biometric body sensors.",
            "Allows an application to access data from sensors that measure biometric conditions."
        ),
        "android.permission.ACTIVITY_RECOGNITION" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows recognizing physical activity (walking, driving, biking).",
            "Allows an application to recognize physical activity."
        ),
        "android.permission.POST_NOTIFICATIONS" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows posting notifications to the user.",
            "Required to be able to post notifications to the user on Android 13+."
        ),
        "android.permission.BLUETOOTH_SCAN" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows discovering and tracking nearby Bluetooth devices.",
            "Required to be able to discover and pair nearby Bluetooth devices."
        ),
        "android.permission.BLUETOOTH_CONNECT" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows connecting to paired Bluetooth devices.",
            "Required to be able to connect to paired Bluetooth devices."
        ),
        "android.permission.BLUETOOTH_ADVERTISE" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows advertising to nearby Bluetooth devices.",
            "Required to be able to advertise to nearby Bluetooth devices."
        ),
        "android.permission.NEARBY_WIFI_DEVICES" to PermissionDef(
            ProtectionCategory.DANGEROUS, RiskLevel.WARNING,
            "Allows discovering nearby Wi-Fi devices without location access.",
            "Required to be able to advertise and connect to nearby devices over Wi-Fi."
        ),

        // SPECIAL (App-op / settings permissions)
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.CRITICAL,
            "Allows creating overlay windows on top of all other applications (tapjacking risk).",
            "Allows an app to create windows shown on top of all other apps."
        ),
        "android.permission.WRITE_SETTINGS" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.HIGH,
            "Allows modifying system settings and system preferences.",
            "Allows an application to read or write the system settings."
        ),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.CRITICAL,
            "Grants broad access to all files across external storage.",
            "Allows an application broad access to shared storage in all-files access."
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.CRITICAL,
            "Allows the app to request installation of arbitrary APK packages.",
            "Allows an application to request installing other packages."
        ),
        "android.permission.PACKAGE_USAGE_STATS" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.HIGH,
            "Allows querying usage statistics of other apps on device.",
            "Allows an application to collect component usage statistics."
        ),
        "android.permission.SCHEDULE_EXACT_ALARM" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.WARNING,
            "Allows scheduling exact alarms which can wake device from doze mode.",
            "Allows applications to use exact alarm APIs."
        ),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.CRITICAL,
            "Must be required by an AccessibilityService to inspect UI elements.",
            "Must be required by an AccessibilityService to ensure that only the system can bind to it."
        ),
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to PermissionDef(
            ProtectionCategory.SPECIAL, RiskLevel.HIGH,
            "Permission to prompt user to disable battery optimizations.",
            "Permission an application must hold in order to use Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS."
        ),

        // SIGNATURE (Platform / system privileged)
        "android.permission.BIND_DEVICE_ADMIN" to PermissionDef(
            ProtectionCategory.SIGNATURE, RiskLevel.HIGH,
            "Must be required by device administration receivers.",
            "Must be required by device administration receivers to ensure that only the system can interact."
        ),
        "android.permission.STATUS_BAR" to PermissionDef(
            ProtectionCategory.SIGNATURE, RiskLevel.WARNING,
            "Allows application to open, close, or disable the status bar.",
            "Allows an application to open, close, or disable the status bar and its icons."
        ),
        "android.permission.WRITE_SECURE_SETTINGS" to PermissionDef(
            ProtectionCategory.SIGNATURE, RiskLevel.CRITICAL,
            "Allows read/write access to secure system settings (restricted to system/platform).",
            "Allows an application to read or write the secure system settings."
        ),
        "android.permission.INSTALL_PACKAGES" to PermissionDef(
            ProtectionCategory.SIGNATURE, RiskLevel.CRITICAL,
            "Allows direct installation of packages without user interaction (privileged).",
            "Allows an application to install packages."
        ),
        "android.permission.DELETE_PACKAGES" to PermissionDef(
            ProtectionCategory.SIGNATURE, RiskLevel.CRITICAL,
            "Allows direct deletion/uninstallation of packages (privileged).",
            "Allows an application to delete packages."
        ),

        // NORMAL (Install-time granted automatically)
        "android.permission.INTERNET" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Grants access to create network sockets and connect to internet hosts.",
            "Allows applications to open network sockets."
        ),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows viewing status of network connections (Wi-Fi, mobile).",
            "Allows applications to access information about networks."
        ),
        "android.permission.ACCESS_WIFI_STATE" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows viewing Wi-Fi connection details.",
            "Allows applications to access information about Wi-Fi networks."
        ),
        "android.permission.CHANGE_WIFI_STATE" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows connecting/disconnecting from Wi-Fi access points.",
            "Allows applications to change Wi-Fi connectivity state."
        ),
        "android.permission.WAKE_LOCK" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows preventing the processor from sleeping or screen from dimming.",
            "Allows using PowerManager WakeLocks to keep processor from sleeping."
        ),
        "android.permission.VIBRATE" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows accessing the device vibration motor.",
            "Allows access to the vibrator."
        ),
        "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows app to start background components right after system boots.",
            "Allows an application to receive the ACTION_BOOT_COMPLETED broadcast."
        ),
        "android.permission.FOREGROUND_SERVICE" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows application to run foreground services.",
            "Allows a regular application to use Service.startForeground."
        ),
        "android.permission.BLUETOOTH" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows applications to connect to paired Bluetooth devices.",
            "Allows applications to connect to paired bluetooth devices."
        ),
        "android.permission.BLUETOOTH_ADMIN" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows applications to discover and pair bluetooth devices.",
            "Allows applications to discover and pair bluetooth devices."
        ),
        "android.permission.NFC" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows applications to perform I/O operations over NFC.",
            "Allows applications to perform I/O operations over NFC."
        ),
        "android.permission.MODIFY_AUDIO_SETTINGS" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows modifying global audio settings.",
            "Allows an application to modify global audio settings."
        ),
        "android.permission.USE_BIOMETRIC" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows app to use device biometric hardware for authentication.",
            "Allows an app to use device-supported biometric modalities."
        ),
        "android.permission.USE_FINGERPRINT" to PermissionDef(
            ProtectionCategory.NORMAL, RiskLevel.INFO,
            "Allows app to use fingerprint hardware for authentication.",
            "This constant was deprecated in API level 28. Applications should use USE_BIOMETRIC."
        )
    )

    fun analyzePermission(permName: String): ScanPermissionInfo {
        val registered = PERMISSION_REGISTRY[permName]
        if (registered != null) {
            return ScanPermissionInfo(
                name = permName,
                protectionCategory = registered.category,
                riskIndicator = registered.risk,
                reason = registered.reason,
                description = registered.description
            )
        }

        // Heuristic analysis for other standard android.permission entries
        val upper = permName.uppercase()
        val simple = permName.substringAfterLast(".")

        val category: ProtectionCategory
        val risk: RiskLevel
        val reason: String

        when {
            upper.startsWith("ANDROID.PERMISSION.BIND_") -> {
                category = ProtectionCategory.SIGNATURE
                risk = RiskLevel.WARNING
                reason = "Signature or system level binding permission."
            }
            upper.contains("INSTALL") || upper.contains("DELETE_PACKAGES") -> {
                category = ProtectionCategory.SPECIAL
                risk = RiskLevel.CRITICAL
                reason = "Package installation or modification capability."
            }
            upper.contains("LOCATION") || upper.contains("CAMERA") || upper.contains("AUDIO") ||
            upper.contains("SMS") || upper.contains("CONTACT") || upper.contains("CALL") ||
            upper.contains("STORAGE") -> {
                category = ProtectionCategory.DANGEROUS
                risk = if (upper.contains("SMS") || upper.contains("CALL")) RiskLevel.CRITICAL else RiskLevel.HIGH
                reason = "Grants access to sensitive user privacy resources or hardware sensors."
            }
            upper.startsWith("ANDROID.PERMISSION.FOREGROUND_SERVICE") -> {
                category = ProtectionCategory.NORMAL
                risk = RiskLevel.INFO
                reason = "Standard foreground service permission."
            }
            upper.startsWith("ANDROID.PERMISSION.") -> {
                category = ProtectionCategory.NORMAL
                risk = RiskLevel.INFO
                reason = "Standard Android platform permission."
            }
            else -> {
                category = ProtectionCategory.UNKNOWN
                risk = RiskLevel.INFO
                reason = "Custom or vendor-specific application permission."
            }
        }

        return ScanPermissionInfo(
            name = permName,
            protectionCategory = category,
            riskIndicator = risk,
            reason = reason,
            description = "Permission identifier: $simple"
        )
    }
}
