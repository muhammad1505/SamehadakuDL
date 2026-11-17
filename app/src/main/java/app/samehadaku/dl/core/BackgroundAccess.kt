package app.samehadaku.dl.core

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * One snapshot of the system rules that can delay the periodic auto-check.
 * Values that the current Android version does not expose stay `null` so the
 * settings screen can hide those rows instead of showing a false warning.
 */
data class BackgroundAccessStatus(
    val batteryOptimizationIgnored: Boolean,
    val backgroundRestricted: Boolean?,
    val dataSaverRestricted: Boolean?,
    val notificationsEnabled: Boolean,
    val notificationPermissionRequired: Boolean,
) {
    val needsAttention: Boolean
        get() = !batteryOptimizationIgnored ||
            backgroundRestricted == true ||
            dataSaverRestricted == true ||
            !notificationsEnabled
}

/** A settings screen to try, newest/OEM-specific first. */
data class BackgroundAccessTarget(
    val label: String,
    val intent: Intent,
)

/**
 * Reads the Android/OEM background policy and builds the matching settings
 * screens. Intents are returned in priority order instead of being resolved
 * with PackageManager: Android 11 package visibility can hide OEM components,
 * and component names differ between ROM versions, so the UI starts them one
 * by one and keeps the first that actually opens.
 */
class BackgroundAccess(context: Context) {

    private val appContext = context.applicationContext
    private val packageName = appContext.packageName
    private val deviceFamily = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.US)

    fun status(): BackgroundAccessStatus {
        val batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            appContext.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(packageName) == true
        } else {
            // Battery optimization does not exist below Android 6.
            true
        }

        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appContext.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
        } else {
            null
        }

        val dataSaverRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when (
                appContext.getSystemService(ConnectivityManager::class.java)
                    ?.restrictBackgroundStatus
            ) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> true
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED,
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> false

                else -> null
            }
        } else {
            null
        }

        val notificationsEnabled = NotificationManagerCompat.from(appContext)
            .areNotificationsEnabled()
        val notificationPermissionRequired =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED

        return BackgroundAccessStatus(
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            backgroundRestricted = backgroundRestricted,
            dataSaverRestricted = dataSaverRestricted,
            notificationsEnabled = notificationsEnabled,
            notificationPermissionRequired = notificationPermissionRequired,
        )
    }

    /**
     * Battery screens. When the app is still optimized the first target is the
     * system exemption dialog (Android 6+); the battery optimization list and
     * app info are always kept as fallbacks for OEM ROMs that lack the dialog.
     */
    fun batteryTargets(): List<BackgroundAccessTarget> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!status().batteryOptimizationIgnored) {
                add(
                    BackgroundAccessTarget(
                        label = "izin optimasi baterai",
                        intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName")),
                    ),
                )
            }
            add(
                BackgroundAccessTarget(
                    label = "optimasi baterai",
                    intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                ),
            )
        }
        addAll(oemBatteryTargets())
        add(BackgroundAccessTarget("info aplikasi", appDetailsIntent()))
    }

    /**
     * OEM autostart managers, then generic battery optimization and app info.
     * Only the current device family is added so unrelated components are not
     * probed with failed intents.
     */
    fun autoStartTargets(): List<BackgroundAccessTarget> = buildList {
        addAll(oemAutoStartTargets())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(
                BackgroundAccessTarget(
                    label = "optimasi baterai",
                    intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                ),
            )
        }
        add(BackgroundAccessTarget("info aplikasi", appDetailsIntent()))
    }

    fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            appDetailsIntent()
        }

    fun dataSaverIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Intent(Settings.ACTION_DATA_SAVER_SETTINGS)
    } else {
        null
    }

    fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    )

    /** Name shown on the autostart button so the user knows which app to open. */
    fun autoStartLabel(): String = when {
        matches("xiaomi", "redmi", "poco") -> "MIUI Security"
        matches("huawei") -> "Phone Manager"
        matches("honor") -> "Honor Manager"
        matches("oppo", "realme") -> "ColorOS"
        matches("oneplus") -> "OxygenOS"
        matches("vivo", "iqoo") -> "iManager"
        matches("samsung") -> "Device Care"
        matches("asus") -> "Mobile Manager"
        matches("nokia", "hmd") -> "Power Saver"
        matches("meizu") -> "Security"
        matches("letv") -> "Letv Safe"
        matches("lenovo") -> "Security"
        matches("infinix", "tecno", "itel") -> "Phone Master"
        else -> "Info aplikasi"
    }

    /** Short OEM-specific steps, with a general fallback for other devices. */
    fun guide(): String = when {
        matches("xiaomi", "redmi", "poco") ->
            "Di MIUI/HyperOS buka Keamanan → Autostart, aktifkan Samehadaku DL, " +
                "lalu pilih Baterai → Tanpa batasan."

        matches("huawei", "honor") ->
            "Buka Pengelola Telepon → Peluncuran aplikasi, matikan pengelolaan otomatis " +
                "untuk Samehadaku DL, lalu izinkan aktivitas latar belakang."

        matches("oppo", "realme", "oneplus") ->
            "Buka Baterai → Optimasi baterai → Samehadaku DL → Jangan optimalkan. " +
                "Aktifkan juga Izinkan aktivitas latar belakang dan Auto-start."

        matches("vivo", "iqoo") ->
            "Buka iManager → Pengelolaan aplikasi → Autostart, aktifkan Samehadaku DL, " +
                "lalu izinkan latar belakang daya tinggi."

        matches("samsung") ->
            "Buka Perawatan perangkat → Baterai → Batas penggunaan latar belakang, " +
                "lalu tambahkan Samehadaku DL ke aplikasi yang tidak pernah tidur."

        matches("asus") ->
            "Buka Mobile Manager → PowerMaster → Auto-start Manager, " +
                "izinkan Samehadaku DL berjalan di latar belakang."

        matches("infinix", "tecno", "itel") ->
            "Buka Phone Master → Autostart, izinkan Samehadaku DL, lalu aktifkan " +
                "Power Marathon untuk aktivitas latar belakang."

        matches("nokia", "hmd") ->
            "Buka Power Saver → Exception list, lalu tambahkan Samehadaku DL."

        matches("meizu") ->
            "Buka Keamanan → Izin → Autostart, lalu izinkan Samehadaku DL."

        matches("letv") ->
            "Buka Letv Safe → Autoboot, lalu izinkan Samehadaku DL."

        else ->
            "Buka Info aplikasi → Baterai → Jangan optimalkan/Tanpa batasan. Jika " +
                "perangkat punya pengelola autostart, izinkan Samehadaku DL di sana."
    }

    private fun oemBatteryTargets(): List<BackgroundAccessTarget> = buildList {
        if (matches("samsung")) {
            add(
                componentTarget(
                    label = "Device Care",
                    packageId = "com.samsung.android.lool",
                    className = "com.samsung.android.sm.ui.battery.BatteryActivity",
                ),
            )
        }
        if (matches("huawei", "honor")) {
            add(
                componentTarget(
                    label = "pengelola daya",
                    packageId = "com.huawei.systemmanager",
                    className = "com.huawei.systemmanager.power.ui.HwPowerManagerActivity",
                ),
            )
        }
    }

    private fun oemAutoStartTargets(): List<BackgroundAccessTarget> = buildList {
        if (matches("xiaomi", "redmi", "poco")) {
            add(
                componentTarget(
                    label = "MIUI Security",
                    packageId = "com.miui.securitycenter",
                    className = "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )
        }
        if (matches("huawei")) {
            add(
                componentTarget(
                    label = "Phone Manager",
                    packageId = "com.huawei.systemmanager",
                    className = "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            )
            add(
                componentTarget(
                    label = "Phone Manager",
                    packageId = "com.huawei.systemmanager",
                    className = "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            )
        }
        if (matches("honor")) {
            add(
                componentTarget(
                    label = "Honor Manager",
                    packageId = "com.hihonor.systemmanager",
                    className = "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            )
        }
        if (matches("oppo", "realme", "oneplus")) {
            add(
                componentTarget(
                    label = "ColorOS",
                    packageId = "com.coloros.safecenter",
                    className = "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            )
            add(
                componentTarget(
                    label = "ColorOS",
                    packageId = "com.coloros.oppoguardelf",
                    className = "com.coloros.oppoguardelf.permission.startup.StartupAppListActivity",
                ),
            )
            add(
                componentTarget(
                    label = "ColorOS",
                    packageId = "com.oppo.safe",
                    className = "com.oppo.safe.permission.startup.StartupAppListActivity",
                ),
            )
        }
        if (matches("oneplus")) {
            add(
                componentTarget(
                    label = "OxygenOS",
                    packageId = "com.oneplus.security",
                    className = "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            )
        }
        if (matches("vivo", "iqoo")) {
            add(
                componentTarget(
                    label = "iManager",
                    packageId = "com.vivo.permissionmanager",
                    className = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
            )
            add(
                componentTarget(
                    label = "iManager",
                    packageId = "com.iqoo.secure",
                    className = "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                ),
            )
        }
        if (matches("samsung")) {
            add(
                componentTarget(
                    label = "Device Care",
                    packageId = "com.samsung.android.lool",
                    className = "com.samsung.android.sm.ui.battery.BatteryActivity",
                ),
            )
        }
        if (matches("asus")) {
            add(
                componentTarget(
                    label = "Mobile Manager",
                    packageId = "com.asus.mobilemanager",
                    className = "com.asus.mobilemanager.powersaver.PowerSaverSettings",
                ),
            )
            add(
                componentTarget(
                    label = "Mobile Manager",
                    packageId = "com.asus.mobilemanager",
                    className = "com.asus.mobilemanager.MainActivity",
                ),
            )
        }
        if (matches("nokia", "hmd")) {
            add(
                componentTarget(
                    label = "Power Saver",
                    packageId = "com.evenwell.powersaving.g3",
                    className = "com.evenwell.powersaving.g3.exceptionlist.ExceptionListActivity",
                ),
            )
        }
        if (matches("letv")) {
            add(
                componentTarget(
                    label = "Letv Safe",
                    packageId = "com.letv.android.letvsafe",
                    className = "com.letv.android.letvsafe.AutobootManageActivity",
                ),
            )
        }
        if (matches("meizu")) {
            add(
                componentTarget(
                    label = "Security",
                    packageId = "com.meizu.safe",
                    className = "com.meizu.safe.security.SecureMainActivity",
                ),
            )
        }
        if (matches("lenovo")) {
            add(
                componentTarget(
                    label = "Security",
                    packageId = "com.lenovo.security",
                    className = "com.lenovo.security.purebackground.PureBackgroundActivity",
                ),
            )
        }
        if (matches("infinix", "tecno", "itel")) {
            add(
                componentTarget(
                    label = "Phone Master",
                    packageId = "com.transsion.phonemaster",
                    className = "com.transsion.phonemaster.ui.autostart.AutoStartActivity",
                ),
            )
        }
    }

    private fun componentTarget(
        label: String,
        packageId: String,
        className: String,
    ): BackgroundAccessTarget = BackgroundAccessTarget(
        label = label,
        intent = Intent().setComponent(ComponentName(packageId, className)),
    )

    private fun matches(vararg keys: String): Boolean =
        keys.any { key -> key in deviceFamily }
}
