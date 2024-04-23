package com.qhy040404.fxxkmiuiad

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qhy040404.fxxkmiuiad.base.BaseActivity
import com.qhy040404.fxxkmiuiad.databinding.ActivityMainBinding
import com.qhy040404.fxxkmiuiad.utils.OsUtils
import com.qhy040404.fxxkmiuiad.utils.PackageUtils
import com.qhy040404.fxxkmiuiad.utils.PackageUtils.getApplicationEnableStateAsString
import com.qhy040404.fxxkmiuiad.utils.ShizukuStatus
import com.qhy040404.fxxkmiuiad.utils.ShizukuUtils
import com.qhy040404.fxxkmiuiad.utils.copyToClipboard
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

@SuppressLint("SetTextI18n")
class MainActivity : BaseActivity<ActivityMainBinding>() {
    private val callback = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            this@MainActivity.refreshView()
        } else {
            runCatching {
                PackageUtils.startLaunchAppActivity(this, Constants.SHIZUKU)
                Toast.makeText(this, "授权失败，跳转到 Shizuku 手动授权", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this, "未检测到 Shizuku, 请手动前往 Sui 授权", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val packageList = Constants.FUCKLIST.filter {
        packageManager.getPackageInfo(it, 0) != null
    }

    override fun init() {
        Shizuku.addRequestPermissionResultListener(callback)
        initView()
    }

    override fun onResume() {
        super.onResume()
        refreshView()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(callback)
        super.onDestroy()
    }

    private fun refreshView() {
        if (!OsUtils.isMiui(this)) {
            binding.info.text = "非 MIUI 或 澎湃 OS"
            binding.enableBtn.apply {
                isClickable = false
                isVisible = false
            }
            binding.disableBtn.apply {
                isClickable = false
                isVisible = false
            }
            return
        }

        when (ShizukuUtils.checkStatus(this)) {
            ShizukuStatus.Ok -> {
                binding.runningInfo.text = "Shizuku 已运行"
                binding.permittedInfo.text = "Shizuku 已授权"

                binding.enableBtn.apply {
                    isClickable = true
                    isVisible = true
                }
                binding.disableBtn.apply {
                    isClickable = true
                    isVisible = true
                }

                binding.installBtn.isVisible = false
                binding.jumpBtn.isVisible = false
                binding.requestPermissionBtn.isVisible = false

                val states = mutableMapOf<String, String>()

                packageList.forEach {
                    states[it] = packageManager.getApplicationEnableStateAsString(it)
                }

                binding.info.text = buildString {
                    states.forEach { (name, state) ->
                        append("${name}: ${state}\n")
                    }
                }
            }

            ShizukuStatus.Outdated -> {
                binding.runningInfo.text = "Shizuku 已运行"
                binding.permittedInfo.text = "Shizuku 已授权"
                binding.info.text = "Shizuku 版本过低，请更新"

                binding.enableBtn.apply {
                    isClickable = false
                    isVisible = false
                }
                binding.disableBtn.apply {
                    isClickable = false
                    isVisible = false
                }

                binding.installBtn.isVisible = true
                binding.jumpBtn.isVisible = false
                binding.requestPermissionBtn.isVisible = false
            }

            ShizukuStatus.NotRunning -> {
                binding.runningInfo.text = "Shizuku 未运行"
                binding.permittedInfo.text = "Shizuku 未授权"

                binding.enableBtn.apply {
                    isClickable = false
                    isVisible = false
                }
                binding.disableBtn.apply {
                    isClickable = false
                    isVisible = false
                }

                binding.installBtn.isVisible = false
                binding.jumpBtn.isVisible = true
                binding.requestPermissionBtn.isVisible = false
            }

            ShizukuStatus.NotAuthorized -> {
                binding.runningInfo.text = "Shizuku 已运行"
                binding.permittedInfo.text = "Shizuku 未授权"

                binding.enableBtn.apply {
                    isClickable = false
                    isVisible = false
                }
                binding.disableBtn.apply {
                    isClickable = false
                    isVisible = false
                }

                binding.installBtn.isVisible = false
                binding.jumpBtn.isVisible = false
                binding.requestPermissionBtn.isVisible = true
            }

            ShizukuStatus.NotInstalled -> {
                binding.runningInfo.text = "Shizuku 未运行"
                binding.permittedInfo.text = "Shizuku 未授权"
                binding.info.text = "Shizuku 未安装"

                binding.enableBtn.apply {
                    isClickable = false
                    isVisible = false
                }
                binding.disableBtn.apply {
                    isClickable = false
                    isVisible = false
                }

                binding.installBtn.isVisible = true
                binding.jumpBtn.isVisible = false
                binding.requestPermissionBtn.isVisible = false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        binding.installBtn.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW).apply { data = Constants.SHIZUKU_RELEASE.toUri() }) }
        }
        binding.jumpBtn.setOnClickListener {
            runCatching { PackageUtils.startLaunchAppActivity(this, Constants.SHIZUKU) }
        }
        binding.requestPermissionBtn.setOnClickListener {
            runCatching { Shizuku.requestPermission(0) }
        }
        binding.enableBtn.setOnClickListener {
            packageList.forEach {
                PackageUtils.setApplicationEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            }
            if (packageList.any { packageManager.isPackageSuspended(it) }) {
                thread {
                    Thread.sleep(200L)
                    runOnUiThread {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("修改状态失败")
                            .setMessage(
                                """
                                部分包处于 suspend 状态
                                如需解除请使用 shell 解除
                                点击按钮以复制指令。
                            """.trimIndent()
                            )
                            .setPositiveButton("关闭", null)
                            .setNeutralButton("复制 adb 指令") { _, _ ->
                                buildString {
                                    packageList.forEach {
                                        append("adb shell pm unsuspend $it")
                                        appendLine()
                                    }
                                }.copyToClipboard(this)
                            }
                            .setNegativeButton("复制 shell 指令") { _, _ ->
                                buildString {
                                    packageList.forEach {
                                        append("pm unsuspend $it")
                                        appendLine()
                                    }
                                }.copyToClipboard(this)
                            }
                            .create()
                            .show()
                    }
                }
            } else {
                thread {
                    Thread.sleep(200L)
                    runOnUiThread {
                        Toast.makeText(this, "已启用", Toast.LENGTH_SHORT).show()
                        refreshView()
                    }
                }
            }
        }
        binding.disableBtn.setOnClickListener {
            runCatching {
                packageList.forEach {
                    PackageUtils.setApplicationEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
                }
            }.onSuccess {
                thread {
                    Thread.sleep(200L)
                    runOnUiThread {
                        Toast.makeText(this, "已禁用", Toast.LENGTH_SHORT).show()
                        refreshView()
                    }
                }
            }.onFailure {
                thread {
                    Thread.sleep(200L)
                    runOnUiThread {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("修改状态失败")
                            .setMessage("""
                                当前版本系统不允许修改相关包状态为 disabled
                                请通过 shell 来使对应包 suspend
                                点击按钮以复制指令。
                            """.trimIndent())
                            .setPositiveButton("关闭", null)
                            .setNeutralButton("复制 adb 指令") {_ ,_ ->
                                buildString {
                                    packageList.forEach {
                                        append("adb shell pm suspend $it")
                                        appendLine()
                                    }
                                }.copyToClipboard(this)
                            }
                            .setNegativeButton("复制 shell 指令") {_,_->
                                buildString {
                                    packageList.forEach {
                                        append("pm suspend $it")
                                        appendLine()
                                    }
                                }.copyToClipboard(this)
                            }
                            .create()
                            .show()
                    }
                }
            }
        }
    }
}