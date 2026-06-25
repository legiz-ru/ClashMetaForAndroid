package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        when(intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()

                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(uri)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )

                finish()
                return
            }

            Intents.ACTION_TOGGLE_CLASH -> if(isClashRunning()) {
                stopClash()
            }
            else {
                startClash()
            }

            Intents.ACTION_START_CLASH -> if(!isClashRunning()) {
                startClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
            }

            Intents.ACTION_STOP_CLASH -> if(isClashRunning()) {
                stopClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
            }
        }
        return finish()
    }

    // Query the authoritative cross-process running state instead of the cached
    // Remote.broadcasts.clashRunning, which isn't synced yet when this activity is
    // launched cold from an automation app (Samsung Modes/Routines, Koala, …) —
    // that race made STOP/TOGGLE no-ops while START worked.
    private fun isClashRunning(): Boolean = StatusClient(this).currentProfile() != null

    private fun startClash() {
//        if (currentProfile == null) {
//            Toast.makeText(this, R.string.no_profile_selected, Toast.LENGTH_LONG).show()
//            return
//        }
        val vpnRequest = startClashService()
        if (vpnRequest != null) {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}