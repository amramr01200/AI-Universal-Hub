package com.aiuniversalhub.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: return
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(context, PREFS, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        prefs.edit()
            .putString("label", intent.getStringExtra(EXTRA_LABEL).orEmpty())
            .putString("stdout", result.getString(EXTRA_STDOUT).orEmpty())
            .putString("stderr", result.getString(EXTRA_STDERR).orEmpty())
            .putInt("exit_code", result.getInt(EXTRA_EXIT_CODE, -1))
            .putInt("error", result.getInt(EXTRA_ERROR, 0))
            .putString("error_message", result.getString(EXTRA_ERROR_MESSAGE).orEmpty())
            .putString("stdout_original_length", result.getString(EXTRA_STDOUT_LENGTH).orEmpty())
            .putString("stderr_original_length", result.getString(EXTRA_STDERR_LENGTH).orEmpty())
            .putLong("received_at", System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val EXTRA_LABEL = "agent_label"
        const val PREFS = "termux_bridge_results_secure"
        const val EXTRA_RESULT_BUNDLE = "result"
        const val EXTRA_STDOUT = "stdout"
        const val EXTRA_STDERR = "stderr"
        const val EXTRA_EXIT_CODE = "exitCode"
        const val EXTRA_ERROR = "err"
        const val EXTRA_ERROR_MESSAGE = "errmsg"
        const val EXTRA_STDOUT_LENGTH = "stdout_original_length"
        const val EXTRA_STDERR_LENGTH = "stderr_original_length"
    }
}
