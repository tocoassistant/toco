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

        // A tile cannot draw its own UI, so it opens the listening screen. On
        // Android 14+ a tile is allowed to start an activity from the click.
        val intent = Intent(this, com.toco.ai.ui.listening.ListeningActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivityAndCollapse(intent)
    }
}
