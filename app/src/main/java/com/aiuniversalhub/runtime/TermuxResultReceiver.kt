package com.aiuniversalhub.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESULT) return
        val result = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("label", intent.getStringExtra(EXTRA_LABEL).orEmpty())
            .putString("stdout", result.getString(EXTRA_STDOUT).orEmpty())
            .putString("stderr", result.getString(EXTRA_STDERR).orEmpty())
            .putInt("exit_code", result.getInt(EXTRA_EXIT_CODE, -1))
            .putInt("error", result.getInt(EXTRA_ERROR, 0))
            .putString("error_message", result.getString(EXTRA_ERROR_MESSAGE).orEmpty())
            .putLong("received_at", System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val ACTION_RESULT = "com.aiuniversalhub.runtime.TERMUX_RESULT"
        const val EXTRA_LABEL = "agent_label"
        const val PREFS = "termux_bridge_results"
        const val EXTRA_RESULT_BUNDLE = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE"
        const val EXTRA_STDOUT = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE_STDOUT"
        const val EXTRA_STDERR = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE_STDERR"
        const val EXTRA_EXIT_CODE = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE_EXIT_CODE"
        const val EXTRA_ERROR = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE_ERR"
        const val EXTRA_ERROR_MESSAGE = "com.termux.util.TERMINAL_PLUGIN_RESULT_BUNDLE_ERRMSG"
    }
}
