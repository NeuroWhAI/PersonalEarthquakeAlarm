package com.neurowhai.pews

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intentService = Intent(context, RestartPewsService::class.java)
            context.startForegroundService(intentService)
        } else {
            val intentService = Intent(context, PewsFirebaseMessagingService::class.java)
            context.startService(intentService)
        }
    }
}
