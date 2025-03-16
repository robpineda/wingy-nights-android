package com.robertopineda.wingynights

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.utils.Array as GdxArray

class MainScreen(private val game: WingyNightsGame) : Screen {
    private val camera = OrthographicCamera()
    private val world = World(Vector2(0f, 0f), true) // Physics world
    private lateinit var character: Sprite
    private val enemies = GdxArray<Sprite>()
    private lateinit var atlas: TextureAtlas
    private lateinit var teleportSound: Sound
    private var isGamePlaying = false
    private var counterEnemies = 0
    private var spawnTimer = 0f
    private val separationBetweenLines = Gdx.graphics.height / 5f

    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        atlas = TextureAtlas(Gdx.files.internal("CharacterSleeping.atlas"))
        character = Sprite(atlas.findRegion("Character"))
        character.setPosition(Gdx.graphics.width / 12f, separationBetweenLines * 2)
        teleportSound = Gdx.audio.newSound(Gdx.files.internal("SoundTeleportTwo.mp3"))
    }

    override fun show() {
        // Initialize physics for character
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(character.x, character.y)
        }
        val characterBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = character.width / 2 }
        characterBody.createFixture(shape, 1f)
        shape.dispose()
        characterBody.userData = character
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        game.batch.projectionMatrix = camera.combined

        world.step(1f / 60f, 6, 2) // Physics step

        game.batch.begin()
        character.draw(game.batch)
        enemies.forEach { it.draw(game.batch) }
        if (!isGamePlaying) {
            // Draw Play button (simplified)
            val playButton = Sprite(atlas.findRegion("ButtonPlay"))
            playButton.setPosition(Gdx.graphics.width / 2f - playButton.width / 2, separationBetweenLines * 2)
            playButton.draw(game.batch)
        }
        game.batch.end()

        if (isGamePlaying) {
            updateGameplay(delta)
        } else {
            handleMenuInput()
        }
    }

    private fun handleMenuInput() {
        if (Gdx.input.isTouched) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
            val playButtonRect = Rectangle(
                Gdx.graphics.width / 2f - 50f, separationBetweenLines * 2 - 50f, 100f, 100f
            )
            if (playButtonRect.contains(touchX, touchY)) {
                isGamePlaying = true
            }
        }
    }

    private fun updateGameplay(delta: Float) {
        // Touch controls
        if (Gdx.input.isTouched) {
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
            val row = (touchY / separationBetweenLines).toInt().coerceIn(0, 4)
            teleportToRow(row)
        }

        // Spawn enemies
        spawnTimer += delta
        if (spawnTimer > MathUtils.random(0f, 1f)) {
            spawnEnemy()
            spawnTimer = 0f
        }

        // Update enemies
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            enemy.x -= 200 * delta // Move left
            if (enemy.x < -enemy.width) {
                iterator.remove()
                counterEnemies++
            }
            if (enemy.getBoundingRectangle().overlaps(character.getBoundingRectangle())) {
                endGame()
            }
        }
    }

    private fun teleportToRow(row: Int) {
        character.y = separationBetweenLines * row + separationBetweenLines / 2
        teleportSound.play()
    }

    private fun spawnEnemy() {
        val row = MathUtils.random(0, 4)
        val enemy = Sprite(atlas.findRegion("Enemy"))
        enemy.setPosition(Gdx.graphics.width.toFloat(), separationBetweenLines * row + separationBetweenLines / 2)
        enemies.add(enemy)
    }

    private fun endGame() {
        isGamePlaying = false
        // Transition to ScoresScreen (implement later)
        game.setScreen(ScoresScreen(game, counterEnemies))
    }

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {
        atlas.dispose()
        teleportSound.dispose()
        world.dispose()
    }
}
