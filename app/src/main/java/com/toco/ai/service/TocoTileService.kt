package com.toco.ai.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: swipe down, one tap, TOCO listens.
 *
 * This exists as the reliable counterpart to the wake-word loop. It costs no
 * battery, cannot be killed, and works when the service has been shut down by
 * the OEM battery manager — which will happen.
 */
class TocoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        // ACTION_TALK skips the wake word and listens for a command directly.
        val intent = Intent(this, WakeWordService::class.java)
            .setAction(WakeWordService.ACTION_TALK)

        startService(intent)
    }
}
