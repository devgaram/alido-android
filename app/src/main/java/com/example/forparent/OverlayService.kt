package com.example.forparent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.LayoutInflater

class OverlayService: Service() {
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleStart()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleStart() {
        (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.view_overlay_service, null)
    }
}