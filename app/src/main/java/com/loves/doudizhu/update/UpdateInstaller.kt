package com.loves.doudizhu.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object UpdateInstaller {
    fun install(context: Context, apk: File): InstallResult {
        if (!apk.isFile) return InstallResult.Failed("安装包不存在，请重新下载")
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return InstallResult.PermissionRequired
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(installIntent)
            InstallResult.Started
        }.getOrElse {
            InstallResult.Failed(it.message ?: "无法打开系统安装界面")
        }
    }

    sealed interface InstallResult {
        data object Started : InstallResult
        data object PermissionRequired : InstallResult
        data class Failed(val message: String) : InstallResult
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
