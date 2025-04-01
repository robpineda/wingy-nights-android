package com.robertopineda.wingynights

import com.badlogic.gdx.Game
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class WingyNightsGame : Game() {
    lateinit var batch: SpriteBatch

    // Companion Object to hold constants like PPM
    companion object {
        // Pixels Per Meter
        // Common values are 32, 64, 100. A higher value means Box2D objects are smaller relative to pixels.
        const val PPM = 100f
    }

    override fun create() {
        batch = SpriteBatch()
        setScreen(MainScreen(this))
    }

    override fun dispose() {
        batch.dispose()
        screen?.dispose() // Added safe call to dispose the current screen
        super.dispose()
    }
}
