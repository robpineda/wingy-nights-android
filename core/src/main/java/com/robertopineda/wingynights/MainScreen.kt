package com.robertopineda.wingynights

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import kotlin.collections.Map
import kotlin.math.max

class MainScreen(private val game: WingyNightsGame) : Screen {
    // Virtual resolution for consistent scaling
    private val WORLD_WIDTH = Gdx.graphics.width.toFloat()
    private val WORLD_HEIGHT = Gdx.graphics.height.toFloat()
    private val scaleFactor = 1.5f

    private val camera = OrthographicCamera()
    private val world = World(Vector2(0f, 0f), true) // Physics world

    // Rendering & Assets
    private lateinit var fontGenerator: FreeTypeFontGenerator
    private lateinit var gameFont: BitmapFont // Custom font
    private lateinit var scoreLayout: GlyphLayout
    private lateinit var pauseLayout: GlyphLayout
    private lateinit var gameOverLayout: GlyphLayout

    private var character: AnimatedSprite
    private lateinit var characterBody: Body
    private val enemies = GdxArray<Body>()
    private var characterSleepingAtlas: TextureAtlas
    private var characterAnimation: Animation<TextureRegion>? = null
    private lateinit var backgroundStars: GdxArray<Sprite>
    private lateinit var enemyAtlases: Map<String, TextureAtlas>

    // UI Sprites & Textures
    private lateinit var playButtonTexture: Texture
    private lateinit var scoresButtonTexture: Texture
    private lateinit var titleTexture: Texture
    private lateinit var pauseButtonTexture: Texture
    private lateinit var homeButtonTexture: Texture
    private lateinit var replayButtonTexture: Texture
    private lateinit var playButton: Sprite
    private lateinit var scoresButton: Sprite
    private lateinit var title: Sprite
    private lateinit var pauseButton: Sprite
    private lateinit var homeButton: Sprite
    private lateinit var replayButton: Sprite
    private lateinit var scoreBirdSprite: Sprite

    // Sounds
    private lateinit var teleportSounds: kotlin.Array<Sound>
    private lateinit var beginLowSounds: kotlin.Array<Sound>
    private lateinit var endHighSounds: kotlin.Array<Sound>
    private lateinit var collisionSound: Sound

    // Game State & Logic
    private var stateTime = 0f
    private var score = 0
    private var highScore = 0
    private var finalScore = 0
    private var spawnTimer = 0f
    private val separationBetweenLines = WORLD_HEIGHT / 5f // Using virtual height
    private val toBetweenRows = separationBetweenLines / 2f
    private var isGamePlaying = false
    private var isGameOver = false
    private var isPaused = false
    private var isCharacterRotating = false
    private var characterRotationSpeed = 360f
    private val rowOccupied = BooleanArray(5) { false } // Track occupancy for 5 rows

    // Preferences
    private lateinit var prefs: Preferences

    init {
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT)
        characterSleepingAtlas = TextureAtlas(Gdx.files.internal("Atlases/CharacterSleeping.atlas"))
        character = AnimatedSprite(characterSleepingAtlas.regions.first()) // Initial frame
        character.setPosition(WORLD_WIDTH / 12f, separationBetweenLines * 2 + toBetweenRows - character.height / 2f)
        character.setScale(scaleFactor.coerceIn(1f, 2f)) // Scale up, capped at 2x

        prefs = Gdx.app.getPreferences("WingyNightsPrefs")
        highScore = prefs.getInteger("highScore", 0)

        setupAssets()
        setupMenuAndUISprites()
        setupBackground()
    }

    private fun setupAssets() {
        // Font Loading
        try {
            fontGenerator = FreeTypeFontGenerator(Gdx.files.internal("Fonts/launica.ttf"))
            val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (24 * scaleFactor).toInt().coerceAtLeast(24) // Scale font, minimum 24
                color = Color.WHITE
                borderWidth = 1f * scaleFactor
                borderColor = Color.BLACK
                shadowOffsetX = (2 * scaleFactor).toInt()
                shadowOffsetY = (2 * scaleFactor).toInt()
                shadowColor = Color(0f, 0f, 0f, 0.5f)
            }
            gameFont = fontGenerator.generateFont(parameter)
        } catch (e: Exception) {
            Gdx.app.error("Font", "Failed to load launica.ttf", e)
            gameFont = BitmapFont()
        }
        scoreLayout = GlyphLayout()
        pauseLayout = GlyphLayout()
        gameOverLayout = GlyphLayout()

        // Character Animation
        val frames = GdxArray<TextureRegion>()
        characterSleepingAtlas.regions.forEach { frames.add(it) }
        if (frames.size > 0) {
            characterAnimation = Animation(0.07f, frames, Animation.PlayMode.LOOP)
            character.setAnimation(characterAnimation!!)
        } else {
            Gdx.app.error("MainScreen", "Character animation has no frames!")
        }
        character.setOriginCenter()

        // Sounds
        teleportSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsTeleport/SoundTeleport$it.mp3")) }
        beginLowSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsBeginLow/SoundBeginLow$it.mp3")) }
        endHighSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsEndHigh/SoundEndHigh$it.mp3")) }
        collisionSound = Gdx.audio.newSound(Gdx.files.internal("Sounds/OtherSounds/SoundCollision.mp3"))

        // UI Textures
        playButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPlay.png"))
        scoresButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonArrowDown.png"))
        titleTexture = Texture(Gdx.files.internal("Symbols/Title.png"))
        pauseButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPause.png"))
        homeButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonHome.png"))
        replayButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonReplay.png"))

        // Enemy Atlases
        val enemyTypes = listOf("EnemyBlue", "EnemyGreen", "EnemyRed", "EnemyOrange", "EnemyPurple")
        enemyAtlases = enemyTypes.associateWith { TextureAtlas(Gdx.files.internal("Atlases/$it.atlas")) }

        // Score Bird Sprite
        val blueAtlas = enemyAtlases["EnemyBlue"]
        val birdRegion = blueAtlas?.regions?.firstOrNull() ?: TextureRegion(Texture(Gdx.files.internal("Characters/Character.png")))
        scoreBirdSprite = Sprite(birdRegion)
        scoreBirdSprite.setSize(birdRegion.regionWidth * 0.6f * scaleFactor, birdRegion.regionHeight * 0.6f * scaleFactor)
        scoreBirdSprite.setOriginCenter()
    }

    private fun setupMenuAndUISprites() {
        playButton = Sprite(playButtonTexture).apply {
            setScale(scaleFactor.coerceIn(1f, 2f))
            setPosition(WORLD_WIDTH / 2f - width / 2, separationBetweenLines * 2f - height / 2f)
        }
        scoresButton = Sprite(scoresButtonTexture).apply {
            setScale(scaleFactor.coerceIn(1f, 2f))
            setPosition(WORLD_WIDTH - width * 1.5f, height * 0.5f)
        }
        title = Sprite(titleTexture).apply {
            setScale(scaleFactor.coerceIn(1f, 2f))
            setPosition(WORLD_WIDTH / 2f - width / 2, separationBetweenLines * 3f + toBetweenRows)
        }
        pauseButton = Sprite(pauseButtonTexture).apply {
            setScale(scaleFactor.coerceIn(1f, 2f) * 0.7f) // Smaller relative scale
            setPosition(20f, WORLD_HEIGHT - height - 20f)
        }
        homeButton = Sprite(homeButtonTexture).apply { setScale(scaleFactor.coerceIn(1f, 2f)) }
        replayButton = Sprite(replayButtonTexture).apply { setScale(scaleFactor.coerceIn(1f, 2f)) }
        val buttonY = WORLD_HEIGHT / 2f - 100f * scaleFactor
        val buttonSpacing = 40f * scaleFactor
        homeButton.setPosition(WORLD_WIDTH / 2f - homeButton.width - buttonSpacing / 2, buttonY)
        replayButton.setPosition(WORLD_WIDTH / 2f + buttonSpacing / 2, buttonY)
    }

    private fun setupBackground() {
        backgroundStars = GdxArray()
        val starsTextures = listOf(
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsOne.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsTwo.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsThree.png"))
        )
        starsTextures.forEachIndexed { index, texture ->
            val star = Sprite(texture).apply {
                setScale(scaleFactor.coerceIn(1f, 2f))
                setPosition(WORLD_WIDTH * index.toFloat(), 0f)
            }
            backgroundStars.add(star)
        }
    }

    private fun resetGame(startGame: Boolean = false) {
        Gdx.app.log("Game", "Resetting Game State. Start immediately: $startGame")
        score = 0
        isGameOver = false
        isPaused = false
        isCharacterRotating = false
        character.rotation = 0f

        enemies.forEach { world.destroyBody(it) }
        enemies.clear()
        rowOccupied.fill(false) // Reset row occupancy

        val initialX = WORLD_WIDTH / 12f
        val initialY = separationBetweenLines * 2 + toBetweenRows - character.height / 2f
        character.setPosition(initialX, initialY)
        if (::characterBody.isInitialized) {
            characterBody.setTransform((initialX + character.width / 2f) / WingyNightsGame.PPM, (initialY + character.height / 2f) / WingyNightsGame.PPM, 0f)
            characterBody.linearVelocity = Vector2(0f, 0f)
            characterBody.angularVelocity = 0f
            characterBody.isAwake = true
        }

        spawnTimer = 1.5f
        isGamePlaying = startGame
    }

    private fun restartGame() {
        resetGame(startGame = true)
    }

    override fun show() {
        Gdx.app.log("Screen", "Showing MainScreen")
        highScore = prefs.getInteger("highScore", 0)

        if (!::characterBody.isInitialized) {
            Gdx.app.log("Physics", "Initializing Character Body")
            val bodyDef = BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                position.set((character.x + character.width / 2) / WingyNightsGame.PPM, (character.y + character.height / 2) / WingyNightsGame.PPM)
                fixedRotation = false
            }
            characterBody = world.createBody(bodyDef)
            val shape = CircleShape().apply { radius = character.width / 2.1f / WingyNightsGame.PPM }
            val fixtureDef = FixtureDef().apply {
                this.shape = shape
                density = 1f
                isSensor = false
            }
            characterBody.createFixture(fixtureDef).userData = "character"
            shape.dispose()
            characterBody.userData = character

            world.setContactListener(object : ContactListener {
                override fun beginContact(contact: Contact) {
                    val fixtureA = contact.fixtureA
                    val fixtureB = contact.fixtureB
                    val isCharA = fixtureA.userData == "character"
                    val isCharB = fixtureB.userData == "character"
                    val isEnemyA = fixtureA.userData == "enemy"
                    val isEnemyB = fixtureB.userData == "enemy"

                    if ((isCharA && isEnemyB) || (isCharB && isEnemyA)) {
                        if (!isGameOver && isGamePlaying) {
                            Gdx.app.log("Collision", "Character hit enemy!")
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
        resetGame(startGame = false)
    }

    override fun render(delta: Float) {
        val effectiveDelta = if (isPaused || isGameOver) 0f else delta

        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        game.batch.projectionMatrix = camera.combined

        if (isGamePlaying && !isPaused && !isGameOver) {
            world.step(1f / 60f, 6, 2)
        }

        if (isGamePlaying && !isPaused) {
            stateTime += delta
        }

        if (isCharacterRotating) {
            character.rotate(characterRotationSpeed * delta)
        }

        if (::characterBody.isInitialized) {
            val charBodyPos = characterBody.position
            character.setPosition(
                charBodyPos.x * WingyNightsGame.PPM - character.width / 2,
                charBodyPos.y * WingyNightsGame.PPM - character.height / 2
            )
            if (!isCharacterRotating) {
                character.rotation = MathUtils.radiansToDegrees * characterBody.angle
            }
        }

        character.update(delta) // Update animation

        enemies.forEach { enemyBody ->
            val enemySprite = enemyBody.userData as? AnimatedSprite
            enemySprite?.let { sprite ->
                val enemyBodyPos = enemyBody.position
                sprite.setPosition(
                    enemyBodyPos.x * WingyNightsGame.PPM - sprite.width / 2,
                    enemyBodyPos.y * WingyNightsGame.PPM - sprite.height / 2
                )
                sprite.rotation = MathUtils.radiansToDegrees * enemyBody.angle
                sprite.update(delta) // Update enemy animation
            }
        }

        game.batch.begin()

        backgroundStars.forEach { star ->
            star.x -= (0.8f / 4f * 60f) * delta
            if (star.x <= -star.width) {
                star.x += star.width * backgroundStars.size
            }
            star.draw(game.batch)
        }

        character.draw(game.batch)
        enemies.forEach { (it.userData as? Sprite)?.draw(game.batch) }

        if (isGameOver) drawGameOverUI()
        else if (isPaused) drawPauseUI()
        else if (isGamePlaying) drawGameplayUI()
        else drawMainMenuUI()

        game.batch.end()

        handleInput(delta)
        if (isGamePlaying && !isPaused && !isGameOver) {
            updateGameplay(delta)
        }
    }

    private fun drawMainMenuUI() {
        title.draw(game.batch)
        playButton.draw(game.batch)
        scoresButton.draw(game.batch)
    }

    private fun drawGameplayUI() {
        pauseButton.draw(game.batch)
        val scoreText = " $score"
        scoreLayout.setText(gameFont, scoreText)
        val scoreX = WORLD_WIDTH - scoreLayout.width - 20f * scaleFactor
        val scoreY = WORLD_HEIGHT - 20f * scaleFactor
        gameFont.draw(game.batch, scoreLayout, scoreX, scoreY)
        scoreBirdSprite.setPosition(scoreX - scoreBirdSprite.width - 5f * scaleFactor, scoreY - scoreLayout.height / 2 - scoreBirdSprite.height / 2)
        scoreBirdSprite.draw(game.batch)
    }

    private fun drawPauseUI() {
        val pauseText = "Paused"
        pauseLayout.setText(gameFont, pauseText)
        val textX = WORLD_WIDTH / 2f - pauseLayout.width / 2
        val textY = WORLD_HEIGHT / 2f + pauseLayout.height / 2
        gameFont.draw(game.batch, pauseLayout, textX, textY)

        playButton.setPosition(WORLD_WIDTH / 2f - playButton.width / 2f, WORLD_HEIGHT / 2f - playButton.height * 1.5f)
        playButton.draw(game.batch)

        val scoreText = " $score"
        scoreLayout.setText(gameFont, scoreText)
        val scoreX = WORLD_WIDTH - scoreLayout.width - 20f * scaleFactor
        val scoreY = WORLD_HEIGHT - 20f * scaleFactor
        gameFont.draw(game.batch, scoreLayout, scoreX, scoreY)
        scoreBirdSprite.setPosition(scoreX - scoreBirdSprite.width - 5f * scaleFactor, scoreY - scoreLayout.height / 2 - scoreBirdSprite.height / 2)
        scoreBirdSprite.draw(game.batch)
    }

    private fun drawGameOverUI() {
        val scoreStr = "SCORE: $finalScore"
        val highScoreStr = "HIGH SCORE: $highScore"
        gameOverLayout.setText(gameFont, "$scoreStr\n$highScoreStr", Color.WHITE, 0f, Align.center, false)
        val textX = WORLD_WIDTH / 2f
        val textY = WORLD_HEIGHT / 2f + gameOverLayout.height / 2 + 50f * scaleFactor
        gameFont.draw(game.batch, gameOverLayout, textX, textY)

        homeButton.draw(game.batch)
        replayButton.draw(game.batch)
    }

    private fun handleInput(delta: Float) {
        if (Gdx.input.justTouched()) {
            val touchX = (Gdx.input.x.toFloat() / Gdx.graphics.width) * WORLD_WIDTH
            val touchY = WORLD_HEIGHT - (Gdx.input.y.toFloat() / Gdx.graphics.height) * WORLD_HEIGHT

            if (isGameOver) {
                if (homeButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Home button touched")
                    resetGame(startGame = false)
                } else if (replayButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Replay button touched")
                    restartGame()
                }
            } else if (isPaused) {
                if (playButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Resume button touched")
                    isPaused = false
                    pauseButton.setPosition(20f, WORLD_HEIGHT - pauseButton.height - 20f)
                }
            } else if (isGamePlaying) {
                if (pauseButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Pause button touched")
                    isPaused = true
                } else {
                    val row = (touchY / separationBetweenLines).toInt().coerceIn(0, 4)
                    teleportToRow(row)
                }
            } else {
                if (playButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Play button touched")
                    isGamePlaying = true
                    spawnTimer = 1.0f
                } else if (scoresButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Scores button touched")
                    game.screen.pause()
                    game.setScreen(ScoresScreen(game, highScore))
                }
            }
        }
    }

    private fun updateGameplay(delta: Float) {
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemyBody = iterator.next()
            val bodyX = enemyBody.position.x * WingyNightsGame.PPM
            val spriteWidth = (enemyBody.userData as? Sprite)?.width ?: 0f
            val row = ((enemyBody.position.y * WingyNightsGame.PPM - toBetweenRows) / separationBetweenLines).toInt().coerceIn(0, 4)

            if (bodyX + spriteWidth / 2 < -10f) {
                world.destroyBody(enemyBody)
                iterator.remove()
                rowOccupied[row] = false // Free the row
                score++
                Gdx.app.log("Gameplay", "Enemy evaded. Score: $score")
                if (MathUtils.random(0, 2) == 1) {
                    endHighSounds[MathUtils.random(0, 4)].play()
                }
            }
        }

        spawnTimer -= delta
        if (spawnTimer <= 0f) {
            spawnEnemy()
            spawnTimer = MathUtils.random(0.5f, 1.5f)
        }
    }

    private fun teleportToRow(row: Int) {
        val targetY = (separationBetweenLines * row + toBetweenRows) / WingyNightsGame.PPM
        characterBody.setTransform(characterBody.position.x, targetY, 0f)
        characterBody.linearVelocity = Vector2(characterBody.linearVelocity.x, 0f)
        teleportSounds[row.coerceIn(0, teleportSounds.size - 1)].play()
        Gdx.app.log("Gameplay", "Teleported to row $row")
    }

    private fun spawnEnemy() {
        val row = (0..4).filter { !rowOccupied[it] }.randomOrNull() ?: return // Pick an unoccupied row
        rowOccupied[row] = true

        val enemyType = when (MathUtils.random(0, 4)) {
            0 -> "EnemyBlue"
            1 -> "EnemyGreen"
            2 -> "EnemyRed"
            3 -> "EnemyOrange"
            else -> "EnemyPurple"
        }
        val atlas = enemyAtlases[enemyType] ?: return
        val enemy = AnimatedSprite(atlas.regions.first()).apply {
            setAnimation(Animation(0.07f, atlas.regions, Animation.PlayMode.LOOP))
            setScale(scaleFactor.coerceIn(1f, 2f))
            val spawnX = WORLD_WIDTH + width / 2f
            val spawnY = separationBetweenLines * row + toBetweenRows
            setPosition(spawnX, spawnY - height / 2f)
            setOriginCenter()
        }

        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody
            position.set((enemy.x + enemy.width / 2f) / WingyNightsGame.PPM, (enemy.y + enemy.height / 2f) / WingyNightsGame.PPM)
        }
        val enemyBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = enemy.width / 2.2f / WingyNightsGame.PPM }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            isSensor = false
        }
        enemyBody.createFixture(fixtureDef).userData = "enemy"
        shape.dispose()
        enemyBody.userData = enemy

        val enemySpeed = -200f / WingyNightsGame.PPM
        enemyBody.linearVelocity = Vector2(enemySpeed, 0f)

        enemies.add(enemyBody)

        if (MathUtils.random(0, 2) == 2) {
            beginLowSounds[row.coerceIn(0, beginLowSounds.size - 1)].play()
        }
    }

    private fun endGame() {
        Gdx.app.log("Game", "Ending Game. Final Score: $score")
        isGamePlaying = false
        isGameOver = true
        isPaused = false
        isCharacterRotating = true
        finalScore = score

        characterBody.linearVelocity = Vector2.Zero
        characterBody.angularVelocity = 0f
        enemies.forEach {
            it.linearVelocity = Vector2.Zero
            it.angularVelocity = 0f
        }

        if (score > highScore) {
            Gdx.app.log("Game", "New High Score: $score")
            highScore = score
            prefs.putInteger("highScore", highScore)
            prefs.flush()
        }
    }

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT)
        camera.viewportWidth = WORLD_WIDTH
        camera.viewportHeight = WORLD_HEIGHT * (width.toFloat() / height.toFloat() * WORLD_HEIGHT / WORLD_WIDTH)
        camera.update()
    }

    override fun pause() {
        Gdx.app.log("Game", "Paused (Lifecycle)")
        if (isGamePlaying && !isGameOver) {
            isPaused = true
        }
    }

    override fun resume() {
        Gdx.app.log("Game", "Resumed (Lifecycle)")
    }

    override fun hide() {
        Gdx.app.log("Game", "Hiding MainScreen")
        isPaused = true
    }

    override fun dispose() {
        Gdx.app.log("Game", "Disposing MainScreen assets")
        character.texture.dispose()
        playButtonTexture.dispose()
        scoresButtonTexture.dispose()
        titleTexture.dispose()
        pauseButtonTexture.dispose()
        homeButtonTexture.dispose()
        replayButtonTexture.dispose()
        scoreBirdSprite.texture.dispose()
        backgroundStars.forEach { it.texture.dispose() }
        characterSleepingAtlas.dispose()
        enemyAtlases.values.forEach { it.dispose() }
        teleportSounds.forEach { it.dispose() }
        beginLowSounds.forEach { it.dispose() }
        endHighSounds.forEach { it.dispose() }
        collisionSound.dispose()
        gameFont.dispose()
        if (::fontGenerator.isInitialized) fontGenerator.dispose()
        world.dispose()
        Gdx.app.log("Dispose", "MainScreen disposed.")
    }

    // Helper class for animated sprites
    inner class AnimatedSprite(region: TextureRegion) : Sprite(region) {
        private var animation: Animation<TextureRegion>? = null
        private var animTime = 0f

        fun setAnimation(anim: Animation<TextureRegion>) {
            animation = anim
            animTime = 0f
        }

        fun update(delta: Float) {
            animation?.let {
                animTime += delta
                setRegion(it.getKeyFrame(animTime))
                setSize(regionWidth.toFloat(), regionHeight.toFloat())
            }
        }
    }
}
