package com.example.grouplauncher

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/** Recibe los accesos directos que crean Chrome, Firefox, Kiwi, etc. ("Añadir a pantalla de inicio"). */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (req != null && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && req.accept()) {
            req.shortcutInfo?.let { si ->
                LauncherState.get(this).addToHome("sc:${si.getPackage()}|${si.id}")
                Toast.makeText(this, "Acceso directo añadido al Home", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
