package com.robertopineda.wingynights

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.utils.Array as GdxArray

class MainScreen(private val game: WingyNightsGame) : Screen {
    private val camera = OrthographicCamera()
    private val world = World(Vector2(0f, 0f), true) // Physics world
    private var character: Sprite
    private lateinit var characterBody: Body
    private val enemies = GdxArray<Sprite>()
    private var characterSleepingAtlas: TextureAtlas
    private var characterAnimation: Animation<TextureRegion>? = null // Nullable to handle empty frames
    private var stateTime = 0f
    private lateinit var backgroundStars: GdxArray<Sprite>
    private lateinit var teleportSounds: Array<Sound>
    private lateinit var beginLowSounds: Array<Sound>
    private lateinit var endHighSounds: Array<Sound>
    private lateinit var collisionSound: Sound
    private var isGamePlaying = false
    private var counterEnemies = 0
    private var spawnTimer = 0f
    private val separationBetweenLines = Gdx.graphics.height / 5f
    private val toBetweenRows = separationBetweenLines / 2f
    private var isGameOver = false

    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        characterSleepingAtlas = TextureAtlas(Gdx.files.internal("Atlases/CharacterSleeping.atlas"))
        character = Sprite(Texture(Gdx.files.internal("Characters/Character.png")))
        character.setPosition(Gdx.graphics.width / 12f, separationBetweenLines * 2 + toBetweenRows)
        setupAssets()
        setupBackground()
    }

    private fun setupAssets() {
        // Character animation
        val frames = GdxArray<TextureRegion>()
        characterSleepingAtlas.regions.forEach { frames.add(it) }

        if (frames.size > 0) {
            characterAnimation = Animation(0.07f, frames, Animation.PlayMode.LOOP)
            Gdx.app.log("MainScreen", "Character animation initialized with ${frames.size} frames")
        }

        // Sounds
        teleportSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsTeleport/SoundTeleport${it}.mp3")) }
        beginLowSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsBeginLow/SoundBeginLow${it}.mp3")) }
        endHighSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsEndHigh/SoundEndHigh${it}.mp3")) }
        collisionSound = Gdx.audio.newSound(Gdx.files.internal("Sounds/OtherSounds/SoundCollision.mp3"))
    }

    private fun setupBackground() {
        backgroundStars = GdxArray()
        val starsTextures = listOf(
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsOne.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsTwo.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsThree.png"))
        )
        starsTextures.forEachIndexed { index, texture ->
            val star = Sprite(texture)
            star.setPosition(Gdx.graphics.width * index.toFloat(), Gdx.graphics.height / 2f)
            star.setOriginCenter()
            backgroundStars.add(star)
        }
    }

    override fun show() {
        // Initialize physics for character
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(character.x + character.width / 2, character.y + character.height / 2)
        }
        characterBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = character.width / 2 }
        characterBody.createFixture(shape, 1f).userData = "character"
        shape.dispose()
        characterBody.userData = character

        // Contact listener for collisions
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) {
                val fixtureA = contact.fixtureA
                val fixtureB = contact.fixtureB
                if ((fixtureA.userData == "character" && fixtureB.userData == "enemy") ||
                    (fixtureB.userData == "character" && fixtureA.userData == "enemy")) {
                    if (!isGameOver) {
                        collisionSound.play()
                        endGame()
                    }
                }
            }
            override fun endContact(contact: Contact) {}
            override fun preSolve(contact: Contact, oldManifold: Manifold) {}
            override fun postSolve(contact: Contact, impulse: ContactImpulse) {}
        })
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        game.batch.projectionMatrix = camera.combined
        world.step(1f / 60f, 6, 2)

        stateTime += delta
        characterAnimation?.let { animation ->
            character.setRegion(animation.getKeyFrame(stateTime))
        }

        // Sync character position with physics body
        character.setPosition(characterBody.position.x - character.width / 2, characterBody.position.y - character.height / 2)

        game.batch.begin()
        // Draw background stars
        backgroundStars.forEach {
            it.x -= 0.8f / 4f * Gdx.graphics.deltaTime * 60 // Match iOS speed
            if (it.x <= -Gdx.graphics.width) {
                it.x += Gdx.graphics.width * 3
            }
            it.draw(game.batch)
        }

        character.draw(game.batch)
        enemies.forEach { it.draw(game.batch) }

        if (!isGamePlaying) {
            drawMenu()
        }
        game.batch.end()

        if (isGamePlaying && !isGameOver) {
            updateGameplay(delta)
        }
    }

    private fun drawMenu() {
        val playButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPlay.png"))
        val playButton = Sprite(playButtonTexture)
        playButton.setPosition(Gdx.graphics.width / 2f - playButton.width / 2, separationBetweenLines * 2)
        playButton.draw(game.batch)

        val scoresButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonHome.png")) // Assuming ButtonHome for scores
        val scoresButton = Sprite(scoresButtonTexture)
        scoresButton.setPosition(Gdx.graphics.width - scoresButton.width, scoresButton.height)
        scoresButton.draw(game.batch)

        val titleTexture = Texture(Gdx.files.internal("Symbols/Title.png"))
        val title = Sprite(titleTexture)
        title.setPosition(Gdx.graphics.width / 2f - title.width / 2, separationBetweenLines * 3 + toBetweenRows)
        title.draw(game.batch)
    }

    private fun handleMenuInput() {
        if (Gdx.input.isTouched) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
            val playButtonRect = Rectangle(
                Gdx.graphics.width / 2f - 50f, separationBetweenLines * 2 - 50f, 100f, 100f
            )
            val scoresButtonRect = Rectangle(
                Gdx.graphics.width - 50f, 0f, 50f, 50f
            )
            when {
                playButtonRect.contains(touchX, touchY) -> {
                    isGamePlaying = true
                    spawnEnemies()
                }
                scoresButtonRect.contains(touchX, touchY) -> game.setScreen(ScoresScreen(game, counterEnemies))
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

        // Update enemies
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            enemy.x -= 200 * delta
            if (enemy.x < -enemy.width) {
                iterator.remove()
                counterEnemies++
                if (MathUtils.random(0, 2) == 1) {
                    endHighSounds[MathUtils.random(0, 4)].play()
                }
            }
        }

        // Spawn enemies
        spawnTimer += delta
        if (spawnTimer > MathUtils.random(0f, 1f)) {
            spawnEnemy()
            spawnTimer = 0f
        }
    }

    private fun teleportToRow(row: Int) {
        val newY = separationBetweenLines * row + toBetweenRows
        characterBody.setTransform(characterBody.position.x, newY + character.height / 2, 0f)
        teleportSounds[row].play()
    }

    private fun spawnEnemy() {
        val row = MathUtils.random(0, 4)
        val enemyAtlas = when (MathUtils.random(0, 4)) {
            0 -> "EnemyBlue"
            1 -> "EnemyGreen"
            2 -> "EnemyRed"
            3 -> "EnemyOrange"
            else -> "EnemyPurple"
        }
        val enemyAtlasInstance = TextureAtlas(Gdx.files.internal("Atlases/$enemyAtlas.atlas"))
        val enemy = Sprite(enemyAtlasInstance.findRegion("Enemy"))
        enemy.setPosition(Gdx.graphics.width.toFloat(), separationBetweenLines * row + toBetweenRows)

        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(enemy.x + enemy.width / 2, enemy.y + enemy.height / 2)
        }
        val enemyBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = enemy.width / 2 }
        enemyBody.createFixture(shape, 1f).userData = "enemy"
        shape.dispose()
        enemyBody.userData = enemy
        enemyBody.linearVelocity = Vector2(-200f, 0f)

        enemies.add(enemy)

        if (MathUtils.random(0, 2) == 2) {
            beginLowSounds[row].play()
        }
    }

    private fun spawnEnemies() {
        repeat(5) { spawnEnemy() } // Initial spawn for each row
    }

    private fun endGame() {
        isGamePlaying = false
        isGameOver = true
        game.setScreen(ScoresScreen(game, counterEnemies))
    }

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {
        characterSleepingAtlas.dispose()
        character.texture.dispose()
        teleportSounds.forEach { it.dispose() }
        beginLowSounds.forEach { it.dispose() }
        endHighSounds.forEach { it.dispose() }
        collisionSound.dispose()
        backgroundStars.forEach { it.texture.dispose() }
        world.dispose()
    }
}
