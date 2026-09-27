package com.yxiao.floatinput

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yxiao.floatinput.databinding.ActivityMainBinding
import com.yxiao.floatinput.service.FloatWindowService
import com.yxiao.floatinput.updater.UpdateInfo
import com.yxiao.floatinput.updater.UpdateManager
import com.yxiao.floatinput.util.AppCrashHandler
import com.yxiao.floatinput.util.HapticHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var updateManager: UpdateManager
    private var isUpdatingSwitch = false
    private var cachedUpdateInfo: UpdateInfo? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateUiState()
        if (granted && hasOverlayPermission()) {
            FloatWindowService.start(this)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUiState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateManager = UpdateManager(this)
        binding.tvCurrentVersion.text = "当前版本: v${updateManager.getCurrentVersionName()} (永久统一签名)"

        setupWindowInsets()
        setupListeners()
        setupUpdateListeners()

        // Background check for update after 1.5s
        binding.root.postDelayed({
            checkForUpdates(isManual = false)
        }, 1500)
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        checkAndDisplayCrashReport()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topToolbar.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    private fun setupListeners() {
        // Overlay permission button
        binding.btnGrantOverlay.setOnClickListener {
            HapticHelper.performClick(it)
            requestOverlayPermission()
        }

        // Notification permission button
        binding.btnGrantNotification.setOnClickListener {
            HapticHelper.performClick(it)
            requestNotificationPermission()
        }

        // Toggle service button
        binding.btnToggleFloating.setOnClickListener {
            HapticHelper.performClick(it)
            toggleService()
        }

        // Switch toggle
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingSwitch && isChecked != FloatWindowService.isServiceRunning) {
                toggleService()
            }
        }

        // Crash log buttons
        binding.btnCopyCrash.setOnClickListener {
            val crash = AppCrashHandler.getLastCrash(this)
            if (!crash.isNullOrEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("CrashLog", crash))
                Toast.makeText(this, "崩溃日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                HapticHelper.performConfirm(it)
            }
        }

        binding.btnClearCrash.setOnClickListener {
            AppCrashHandler.clearCrash(this)
            binding.cardCrashReport.visibility = View.GONE
            Toast.makeText(this, "崩溃日志已清除", Toast.LENGTH_SHORT).show()
            HapticHelper.performClick(it)
        }
    }

    private fun setupUpdateListeners() {
        binding.btnCheckUpdate.setOnClickListener {
            HapticHelper.performClick(it)
            checkForUpdates(isManual = true)
        }

        binding.btnDownloadInstall.setOnClickListener {
            HapticHelper.performClick(it)
            val info = cachedUpdateInfo ?: return@setOnClickListener
            startDownloadAndInstall(info)
        }
    }

    private fun checkForUpdates(isManual: Boolean) {
        if (isManual) {
            Toast.makeText(this, "正在从 GitHub 获取最新版本信息…", Toast.LENGTH_SHORT).show()
            binding.btnCheckUpdate.isEnabled = false
        }

        updateManager.checkUpdate { result ->
            if (isManual) {
                binding.btnCheckUpdate.isEnabled = true
            }

            result.onSuccess { info ->
                if (info.hasUpdate) {
                    cachedUpdateInfo = info
                    binding.layoutUpdateAvailable.visibility = View.VISIBLE
                    binding.tvNewVersionTitle.text = "🎉 发现新版本: v${info.latestVersion}"
                    val sizeMb = if (info.apkSize > 0) String.format("%.2f MB", info.apkSize / (1024f * 1024f)) else "约 2 MB"
                    binding.tvChangelog.text = "安装包大小: $sizeMb\n\n更新说明:\n${info.changelog}"
                    if (isManual) {
                        Toast.makeText(this, "发现新版本 v${info.latestVersion}！", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (isManual) {
                        Toast.makeText(this, "当前已是最新版本 (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { err ->
                if (isManual) {
                    Toast.makeText(this, "检查更新失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startDownloadAndInstall(info: UpdateInfo) {
        if (info.downloadUrl.isEmpty()) {
            Toast.makeText(this, "未能获取到下载链接，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnDownloadInstall.isEnabled = false
        binding.pbDownload.visibility = View.VISIBLE
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.pbDownload.progress = 0
        binding.tvDownloadProgress.text = "正在极速下载更新包: 0%"

        updateManager.downloadApk(
            downloadUrl = info.downloadUrl,
            onProgress = { percent, _ ->
                binding.pbDownload.progress = percent
                binding.tvDownloadProgress.text = "正在极速下载更新包: $percent%"
            },
            onComplete = { apkFile ->
                binding.btnDownloadInstall.isEnabled = true
                binding.tvDownloadProgress.text = "下载完成，正在调起系统安装器…"
                val success = updateManager.installApk(apkFile)
                if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Toast.makeText(this, "请先允许「安装未知应用」权限以覆盖升级", Toast.LENGTH_LONG).show()
                }
            },
            onError = { err ->
                binding.btnDownloadInstall.isEnabled = true
                binding.tvDownloadProgress.text = "下载失败: ${err.message}"
                Toast.makeText(this, "下载更新失败: ${err.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun checkAndDisplayCrashReport() {
        val lastCrash = AppCrashHandler.getLastCrash(this)
        if (!lastCrash.isNullOrEmpty()) {
            binding.cardCrashReport.visibility = View.VISIBLE
            binding.tvCrashContent.text = lastCrash
        } else {
            binding.cardCrashReport.visibility = View.GONE
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, R.string.permission_overlay_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "通知权限已就绪", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, R.string.permission_overlay_required, Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            updateUiState()
            return
        }

        if (FloatWindowService.isServiceRunning) {
            FloatWindowService.stop(this)
        } else {
            // If on Android 13+ and no notification permission, request it first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            try {
                FloatWindowService.start(this)
            } catch (e: Throwable) {
                AppCrashHandler.saveCrash(this, e)
                Toast.makeText(this, "启动服务发生异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.root.postDelayed({
            updateUiState()
        }, 200)
    }

    private fun updateUiState() {
        val hasOverlay = hasOverlayPermission()
        val hasNotification = hasNotificationPermission()
        val isRunning = FloatWindowService.isServiceRunning

        // Overlay status
        if (hasOverlay) {
            binding.tvOverlayPermStatus.text = "已就绪"
            binding.tvOverlayPermStatus.setTextColor(ContextCompat.getColor(this, R.color.color_copy))
            binding.btnGrantOverlay.text = "已授权"
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.tvOverlayPermStatus.text = "未授予 (必需)"
            binding.tvOverlayPermStatus.setTextColor(ContextCompat.getColor(this, R.color.color_clear))
            binding.btnGrantOverlay.text = "去授权"
            binding.btnGrantOverlay.isEnabled = true
        }

        // Notification status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.btnGrantNotification.visibility = View.VISIBLE
            if (hasNotification) {
                binding.tvNotifPermStatus.text = "已就绪"
                binding.tvNotifPermStatus.setTextColor(ContextCompat.getColor(this, R.color.color_copy))
                binding.btnGrantNotification.text = "已授权"
                binding.btnGrantNotification.isEnabled = false
            } else {
                binding.tvNotifPermStatus.text = "未授予 (建议开启)"
                binding.tvNotifPermStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                binding.btnGrantNotification.text = "授权"
                binding.btnGrantNotification.isEnabled = true
            }
        } else {
            binding.btnGrantNotification.visibility = View.GONE
            binding.tvNotifPermStatus.text = "默认支持"
        }

        // Service running status without triggering loop
        isUpdatingSwitch = true
        binding.switchService.isChecked = isRunning
        isUpdatingSwitch = false

        if (isRunning) {
            binding.tvServiceStatus.text = getString(R.string.status_running)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.color_copy))
            binding.btnToggleFloating.text = getString(R.string.action_stop_service)
        } else {
            binding.tvServiceStatus.text = getString(R.string.status_stopped)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            binding.btnToggleFloating.text = getString(R.string.action_start_service)
        }
    }
}
