package com.aiuniversalhub.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Opens a user-visible Termux terminal for the Claude Code setup/runtime.
 * No API keys or arbitrary shell text are accepted by this bridge.
 */
object ClaudeCodeLauncher {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    private const val HOME = "/data/data/com.termux/files/home"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    fun openSetup(context: Context): Result = openTerminal(
        context,
        "if [ ! -d \"$HOME/AI-Universal-Hub/.git\" ]; then " +
            "pkg install -y git && git clone --branch feature/claude-code-termux " +
            "https://github.com/amramr01200/AI-Universal-Hub.git \"$HOME/AI-Universal-Hub\"; fi; " +
            "bash \"$HOME/AI-Universal-Hub/scripts/claude-code-termux-setup.sh\""
    )

    fun openClaudeCode(context: Context): Result = openTerminal(
        context,
        "proot-distro login ubuntu -- bash -lc 'cd \"$HOME/AI-Universal-Hub\" 2>/dev/null || cd \"$HOME\"; exec claude'"
    )

    private fun openTerminal(context: Context, command: String): Result {
        if (!isTermuxInstalled(context)) return Result(false, "Termux is not installed.")
        if (ContextCompat.checkSelfPermission(context, "com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            return Result(false, "Grant Termux RUN_COMMAND permission to AI Universal Hub first.")
        }
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(EXTRA_PATH, BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, HOME)
            // false requests a visible Termux terminal session so prompts and Claude's
            // permission confirmations remain under the user's direct control.
            putExtra(EXTRA_BACKGROUND, false)
        }
        return try {
            context.startService(intent)
            Result(true, "Opened a visible Termux session.")
        } catch (e: Exception) {
            Result(false, e.message ?: "Could not open the Termux terminal.")
        }
    }

    private fun isTermuxInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    data class Result(val opened: Boolean, val message: String)
}
