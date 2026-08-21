package com.gearboost.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gearboost.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var boosting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshRamStatus()

        binding.btnBoost.setOnClickListener {
            if (!boosting) startBoost() else stopBoost()
        }

        binding.switchCloseApps.isChecked = true
        binding.switchDnd.isChecked = true
        binding.switchNetwork.isChecked = true
        binding.switchGameMode.isChecked = true
    }

    private fun startBoost() {
        var closedCount = 0

        if (binding.switchCloseApps.isChecked) {
            if (!hasUsageAccess()) {
                requestUsageAccess()
                return
            }
            closedCount = closeBackgroundApps()
        }

        if (binding.switchDnd.isChecked) {
            if (!hasDndAccess()) {
                requestDndAccess()
                return
            }
            enableDnd()
        }

        if (binding.switchNetwork.isChecked) {
            prioritizeWifiNetwork()
        }

        if (binding.switchGameMode.isChecked) {
            enableGameModeHint()
        }

        boosting = true
        binding.btnBoost.text = getString(R.string.btn_boost_active)
        binding.statusApps.text = getString(R.string.status_apps_closed, closedCount)
        refreshRamStatus()

        Toast.makeText(this, "Бустеу қосылды", Toast.LENGTH_SHORT).show()
    }

    private fun stopBoost() {
        boosting = false
        binding.btnBoost.text = getString(R.string.btn_boost)

        if (hasDndAccess()) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }

        Toast.makeText(this, "Бустеу тоқтатылды", Toast.LENGTH_SHORT).show()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageAccess() {
        Toast.makeText(this, getString(R.string.perm_usage_msg), Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun closeBackgroundApps(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
        var closed = 0

        for (appInfo in installedApps) {
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystemApp && appInfo.packageName != packageName) {
                try {
                    am.killBackgroundProcesses(appInfo.packageName)
                    closed++
                } catch (_: SecurityException) {
                }
            }
        }
        return closed
    }

    private fun hasDndAccess(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun requestDndAccess() {
        Toast.makeText(this, getString(R.string.perm_dnd_msg), Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun enableDnd() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun prioritizeWifiNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.bindProcessToNetwork(network)
                }
            }
        })
    }

    private fun enableGameModeHint() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun refreshRamStatus() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availableMb = memInfo.availMem / (1024 * 1024)
        binding.statusRam.text = getString(R.string.status_ram, "${availableMb} MB")
    }
}
