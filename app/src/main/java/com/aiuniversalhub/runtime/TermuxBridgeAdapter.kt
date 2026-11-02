package com.aiuniversalhub.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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

    fun sendApprovedCommand(context: Context, command: String): Boolean {
        // Only catalogued, fixed commands are allowed through this bridge.
        val allowed = RuntimeCatalog.components.map { it.installCommand }.toSet() +
            setOf("pkg update", "pkg upgrade")
        if (command !in allowed) return false

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, false)
        }
        return try {
            context.startService(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
