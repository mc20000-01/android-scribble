package com.example.androidscribble.ink

import android.app.Activity
import android.content.Intent

/**
 * Holds a Media Projection grant only in memory.
 *
 * Android does not provide a durable screen-capture grant that can safely be
 * replayed after process death, so onboarding must not write the result Intent
 * to SharedPreferences or disk. Keeping a cloned Intent here lets the already
 * running app process configure a sampler, while naturally dropping the grant
 * when the process exits.
 */
object MediaProjectionPermissionStore {
    private var resultCode: Int? = null
    private var resultData: Intent? = null

    fun update(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            this.resultCode = resultCode
            resultData = Intent(data)
        } else {
            clear()
        }
    }

    fun consume(): Grant? {
        val code = resultCode ?: return null
        val data = resultData ?: return null
        clear()
        return Grant(code, Intent(data))
    }

    fun clear() {
        resultCode = null
        resultData = null
    }

    data class Grant(val resultCode: Int, val data: Intent)
}
