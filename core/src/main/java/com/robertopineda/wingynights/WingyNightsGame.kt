package com.robertopineda.wingynights

import com.badlogic.gdx.Game
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class WingyNightsGame : Game() {
    lateinit var batch: SpriteBatch

    override fun create() {
        batch = SpriteBatch()
        setScreen(MainScreen(this)) // Start with MainScreen
    }

    override fun dispose() {
        batch.dispose()
        super.dispose()
    }
}
