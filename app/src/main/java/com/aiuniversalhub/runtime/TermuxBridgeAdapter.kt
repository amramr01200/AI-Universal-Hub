package com.aiuniversalhub.runtime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object TermuxBridgeAdapter {
    const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    fun isInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

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
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, true)
        }
        return try {
            context.startService(intent)
            Result(true, "Command submitted to Termux. Check Termux notifications/output for completion.")
        } catch (e: Exception) {
            Result(false, e.message ?: "Could not start Termux command service.")
        }
    }

    data class Result(val submitted: Boolean, val message: String)
}
