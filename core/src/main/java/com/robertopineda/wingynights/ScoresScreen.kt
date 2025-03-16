package com.robertopineda.wingynights

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas

class ScoresScreen(private val game: WingyNightsGame, private val lastScore: Int) : Screen {
    private val prefs = Gdx.app.getPreferences("WingyNightsPrefs")
    private lateinit var atlas: TextureAtlas
    private var highScore = prefs.getInteger("highScore", 0)
    private var totalEvaded = prefs.getInteger("totalEvaded", 0)

    init {
        atlas = TextureAtlas(Gdx.files.internal("CharacterSleeping.atlas"))
        updateScores()
    }

    private fun updateScores() {
        if (lastScore > highScore) highScore = lastScore
        totalEvaded += lastScore
        prefs.putInteger("highScore", highScore)
        prefs.putInteger("totalEvaded", totalEvaded)
        prefs.flush()
    }

    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        game.batch.begin()
        val homeButton = Sprite(atlas.findRegion("ButtonHome"))
        homeButton.setPosition(Gdx.graphics.width - homeButton.width, homeButton.height)
        homeButton.draw(game.batch)
        game.batch.end()

        if (Gdx.input.isTouched) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
            if (homeButton.getBoundingRectangle().contains(touchX, touchY)) {
                game.setScreen(MainScreen(game))
            }
        }

        // TODO: Draw score labels (use BitmapFont for text)
    }

    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {
        atlas.dispose()
    }
}
