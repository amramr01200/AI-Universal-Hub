package com.aiuniversalhub.runtime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

object TermuxBridgeAdapter {
    const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private val requestIds = AtomicInteger(1000)

    fun isInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun canRunCommands(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    fun sendApprovedCommand(context: Context, componentId: String): Result {
        if (!isInstalled(context)) return Result(false, "Termux is not installed.")
        if (!canRunCommands(context)) return Result(false, "Grant Termux RUN_COMMAND permission to AI Universal Hub, then retry.")
        val component = RuntimeCatalog.components.firstOrNull { it.id == componentId }
            ?: return Result(false, "Unknown package component.")
        val command = component.installCommand
        if (command !in RuntimeCatalog.components.map { it.installCommand }.toSet()) {
            return Result(false, "Command is not in the approved package catalog.")
        }
        return submit(context, command, "/data/data/com.termux/files/home", "catalog:$componentId")
    }

    // Fixed, read-only diagnostics only; no arbitrary shell input is accepted.
    fun sendDiagnostic(context: Context, diagnosticId: String): Result {
        if (!isInstalled(context)) return Result(false, "Termux is not installed.")
        if (!canRunCommands(context)) return Result(false, "Grant Termux RUN_COMMAND permission to AI Universal Hub, then retry.")
        val command = when (diagnosticId) {
            "pwd" -> "pwd"
            "date" -> "date"
            "uname" -> "uname -a"
            "list_home" -> "ls -la"
            else -> return Result(false, "Diagnostic is not allowlisted.")
        }
        return submit(context, command, "/data/data/com.termux/files/home", "diagnostic:$diagnosticId")
    }

    private fun submit(context: Context, command: String, workDir: String, label: String): Result {
        val requestCode = requestIds.incrementAndGet()
        val callbackIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxResultReceiver.ACTION_RESULT
            putExtra(TermuxResultReceiver.EXTRA_LABEL, label)
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, requestCode, callbackIntent, flags)
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_PENDING_INTENT, callback)
        }
        return try {
            context.startService(intent)
            Result(true, "Command submitted; waiting for Termux result.")
        } catch (e: Exception) {
            Result(false, e.message ?: "Could not start Termux command service.")
        }
    }

    data class Result(val submitted: Boolean, val message: String)
}
