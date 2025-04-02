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
    private val camera = OrthographicCamera()
    private val world = World(Vector2(0f, 0f), true) // Physics world

    // Rendering & Assets
    private lateinit var fontGenerator: FreeTypeFontGenerator
    private lateinit var gameFont: BitmapFont // Custom font
    private lateinit var scoreLayout: GlyphLayout // For centering/positioning text
    private lateinit var pauseLayout: GlyphLayout
    private lateinit var gameOverLayout: GlyphLayout

    private var character: Sprite
    private lateinit var characterBody: Body
    private val enemies = GdxArray<Body>()
    private var characterSleepingAtlas: TextureAtlas
    private var characterAnimation: Animation<TextureRegion>? = null
    private lateinit var backgroundStars: GdxArray<Sprite>
    private lateinit var enemyAtlases: Map<String, TextureAtlas>

    // UI Sprites & Textures
    private lateinit var playButtonTexture: Texture
    private lateinit var scoresButtonTexture: Texture // Now ButtonArrowDown
    private lateinit var titleTexture: Texture
    private lateinit var pauseButtonTexture: Texture
    private lateinit var homeButtonTexture: Texture // For Game Over
    private lateinit var replayButtonTexture: Texture // For Game Over

    private lateinit var playButton: Sprite
    private lateinit var scoresButton: Sprite // Arrow down initially
    private lateinit var title: Sprite
    private lateinit var pauseButton: Sprite
    private lateinit var homeButton: Sprite // Game Over Home
    private lateinit var replayButton: Sprite // Game Over Replay
    private lateinit var scoreBirdSprite: Sprite // Miniature bird for score

    // Sounds
    private lateinit var teleportSounds: kotlin.Array<Sound>
    private lateinit var beginLowSounds: kotlin.Array<Sound>
    private lateinit var endHighSounds: kotlin.Array<Sound>
    private lateinit var collisionSound: Sound

    // Game State & Logic
    private var stateTime = 0f
    private var score = 0
    private var highScore = 0
    private var finalScore = 0 // Score when game ends
    private var spawnTimer = 0f
    private val separationBetweenLines = Gdx.graphics.height / 5f
    private val toBetweenRows = separationBetweenLines / 2f
    private var isGamePlaying = false
    private var isGameOver = false
    private var isPaused = false
    private var isCharacterRotating = false // For game over animation
    private var characterRotationSpeed = 360f // Degrees per second

    // Preferences
    private lateinit var prefs: Preferences

    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        characterSleepingAtlas = TextureAtlas(Gdx.files.internal("Atlases/CharacterSleeping.atlas"))
        character = Sprite(Texture(Gdx.files.internal("Characters/Character.png"))) // Fallback texture
        character.setPosition(Gdx.graphics.width / 12f, separationBetweenLines * 2 + toBetweenRows - character.height / 2f)

        // Initialize Preferences
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
                size = (Gdx.graphics.density * 24).toInt() // Adjust size as needed (scaled by density)
                color = Color.WHITE
                borderWidth = 1f
                borderColor = Color.BLACK
                shadowOffsetX = 2
                shadowOffsetY = 2
                shadowColor = Color(0f, 0f, 0f, 0.5f)
            }
            gameFont = fontGenerator.generateFont(parameter)
        } catch (e: Exception) {
            Gdx.app.error("Font", "Failed to load launica.ttf, using default font.", e)
            gameFont = BitmapFont() // Fallback to default font
        }
        scoreLayout = GlyphLayout()
        pauseLayout = GlyphLayout()
        gameOverLayout = GlyphLayout()


        // Character Animation
        val frames = GdxArray<TextureRegion>()
        characterSleepingAtlas.regions.forEach { frames.add(it) }
        if (frames.size > 0) {
            characterAnimation = Animation(0.07f, frames, Animation.PlayMode.LOOP)
            character.setRegion(characterAnimation?.getKeyFrame(0f))
            character.setSize(character.regionWidth.toFloat(), character.regionHeight.toFloat())
        } else {
            Gdx.app.error("MainScreen", "Character animation has no frames!")
        }
        character.setOriginCenter()

        // Sounds
        teleportSounds = kotlin.Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsTeleport/SoundTeleport${it}.mp3")) }
        beginLowSounds = kotlin.Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsBeginLow/SoundBeginLow${it}.mp3")) }
        endHighSounds = kotlin.Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsEndHigh/SoundEndHigh${it}.mp3")) }
        collisionSound = Gdx.audio.newSound(Gdx.files.internal("Sounds/OtherSounds/SoundCollision.mp3"))

        // UI Textures
        playButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPlay.png"))
        scoresButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonArrowDown.png")) // Use ArrowDown for scores access
        titleTexture = Texture(Gdx.files.internal("Symbols/Title.png"))
        pauseButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPause.png"))
        homeButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonHome.png")) // Specific Home Button for Game Over
        replayButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonReplay.png"))


        // Enemy Atlases
        val enemyTypes = listOf("EnemyBlue", "EnemyGreen", "EnemyRed", "EnemyOrange", "EnemyPurple")
        enemyAtlases = enemyTypes.associateWith { TextureAtlas(Gdx.files.internal("Atlases/$it.atlas")) }

        // Score Bird Sprite (using first frame of blue enemy)
        try {
            val blueAtlas = enemyAtlases["EnemyBlue"]
            val birdRegion = blueAtlas?.regions?.firstOrNull()
            if (birdRegion != null) {
                scoreBirdSprite = Sprite(birdRegion)
                scoreBirdSprite.setSize(scoreBirdSprite.width * 0.6f, scoreBirdSprite.height * 0.6f) // Scale it down
                scoreBirdSprite.setOriginCenter()
            } else {
                Gdx.app.error("Assets", "Could not create score bird sprite")
                // Create a placeholder or handle error
                scoreBirdSprite = Sprite(Texture(Gdx.files.internal("Characters/Character.png"))) // Placeholder
                scoreBirdSprite.setSize(30f, 30f)
            }
        } catch (e: Exception) {
            Gdx.app.error("Assets", "Error creating score bird sprite", e)
            scoreBirdSprite = Sprite(Texture(Gdx.files.internal("Characters/Character.png"))) // Placeholder
            scoreBirdSprite.setSize(30f, 30f)
        }
    }

    private fun setupMenuAndUISprites() {
        // Main Menu Items
        playButton = Sprite(playButtonTexture)
        playButton.setPosition(Gdx.graphics.width / 2f - playButton.width / 2, separationBetweenLines * 2f - playButton.height / 2f)

        scoresButton = Sprite(scoresButtonTexture) // ArrowDown button
        scoresButton.setPosition(Gdx.graphics.width - scoresButton.width * 1.5f, scoresButton.height * 0.5f) // Bottom right

        title = Sprite(titleTexture)
        title.setPosition(Gdx.graphics.width / 2f - title.width / 2, separationBetweenLines * 3f + toBetweenRows)

        // Gameplay UI Items
        pauseButton = Sprite(pauseButtonTexture)
        pauseButton.setSize(pauseButton.width * 0.7f, pauseButton.height * 0.7f) // Make it smaller
        pauseButton.setPosition(20f, Gdx.graphics.height - pauseButton.height - 20f) // Top left

        // Game Over UI Items
        homeButton = Sprite(homeButtonTexture)
        replayButton = Sprite(replayButtonTexture)
        // Position them centered horizontally, spaced vertically below score text
        val buttonY = Gdx.graphics.height / 2f - 100f // Adjust Y offset as needed
        val buttonSpacing = 40f
        homeButton.setPosition(Gdx.graphics.width / 2f - homeButton.width - buttonSpacing / 2, buttonY)
        replayButton.setPosition(Gdx.graphics.width / 2f + buttonSpacing / 2, buttonY)
    }

    private fun setupBackground() {
        // ... (setupBackground code remains the same)
        backgroundStars = GdxArray()
        val starsTextures = listOf(
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsOne.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsTwo.png")),
            Texture(Gdx.files.internal("BackgroundStarsiPhone5/BackgroundStarsThree.png"))
        )
        starsTextures.forEachIndexed { index, texture ->
            val star = Sprite(texture)
            star.setPosition(Gdx.graphics.width * index.toFloat(), 0f)
            backgroundStars.add(star)
        }
    }

    private fun resetGame(startGame: Boolean = false) {
        Gdx.app.log("Game", "Resetting Game State. Start immediately: $startGame")
        score = 0
        isGameOver = false
        isPaused = false
        isCharacterRotating = false
        character.rotation = 0f // Reset rotation

        // Clear enemies
        enemies.forEach { world.destroyBody(it) }
        enemies.clear()

        // Reset character position and physics
        val initialX = Gdx.graphics.width / 12f
        val initialY = separationBetweenLines * 2 + toBetweenRows - character.height / 2f
        character.setPosition(initialX, initialY)
        if (::characterBody.isInitialized) {
            characterBody.setTransform((initialX + character.width/2f) / WingyNightsGame.PPM, (initialY + character.height/2f) / WingyNightsGame.PPM, 0f)
            characterBody.linearVelocity = Vector2(0f, 0f)
            characterBody.angularVelocity = 0f
            // Wake up the body if it was sleeping
            characterBody.isAwake = true
        }

        spawnTimer = 1.5f // Initial delay before first spawn

        isGamePlaying = startGame // Only set to true if restarting immediately
    }

    private fun restartGame() {
        resetGame(startGame = true)
    }

    override fun show() {
        Gdx.app.log("Screen", "Showing MainScreen")
        // Load high score every time screen is shown
        highScore = prefs.getInteger("highScore", 0)

        // Initialize physics for character ONLY if not already initialized
        if (!::characterBody.isInitialized) {
            Gdx.app.log("Physics", "Initializing Character Body")
            val bodyDef = BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                position.set(
                    (character.x + character.width / 2) / WingyNightsGame.PPM,
                    (character.y + character.height / 2) / WingyNightsGame.PPM
                )
                fixedRotation = false // Allow rotation for game over effect
            }
            characterBody = world.createBody(bodyDef)
            val shape = CircleShape().apply { radius = character.width / 2.1f / WingyNightsGame.PPM } // Slightly smaller radius?
            val fixtureDef = FixtureDef().apply {
                this.shape = shape
                density = 1f
                isSensor = false
            }
            characterBody.createFixture(fixtureDef).userData = "character"
            shape.dispose()
            characterBody.userData = character

            // Contact listener
            world.setContactListener(object : ContactListener {
                override fun beginContact(contact: Contact) {
                    // ... (Collision logic remains the same - calls endGame())
                    val fixtureA = contact.fixtureA
                    val fixtureB = contact.fixtureB
                    val isCharA = fixtureA.userData == "character"
                    val isCharB = fixtureB.userData == "character"
                    val isEnemyA = fixtureA.userData == "enemy"
                    val isEnemyB = fixtureB.userData == "enemy"

                    if ((isCharA && isEnemyB) || (isCharB && isEnemyA)) {
                        if (!isGameOver && isGamePlaying) { // Only trigger if playing and not already game over
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
        // Reset game state when screen becomes active (returns to main menu)
        resetGame(startGame = false)
    }

    override fun render(delta: Float) {
        // Determine effective delta (0 if paused)
        val effectiveDelta = if (isPaused || isGameOver) 0f else delta // Stop time during pause/game over

        // Clear screen
        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Update camera
        camera.update()
        game.batch.projectionMatrix = camera.combined

        // Step the physics world ONLY if game is playing and not paused
        if (isGamePlaying && !isPaused && !isGameOver) {
            world.step(1f / 60f, 6, 2) // Use fixed time step for physics
        }

        // Update animations ONLY if game is playing and not paused
        if (isGamePlaying && !isPaused) {
            stateTime += delta // Animation time progresses even if game over, but not if paused
        }

        // Character Rotation during Game Over
        if (isCharacterRotating) {
            character.rotate(characterRotationSpeed * delta) // Use real delta for smooth visual
        }

        // Sync character sprite position with physics body (always sync visual)
        if (::characterBody.isInitialized) {
            val charBodyPos = characterBody.position
            character.setPosition(
                charBodyPos.x * WingyNightsGame.PPM - character.width / 2,
                charBodyPos.y * WingyNightsGame.PPM - character.height / 2
            )
            // Apply rotation from physics ONLY IF NOT in game over rotation override
            if (!isCharacterRotating) {
                character.rotation = MathUtils.radiansToDegrees * characterBody.angle
            }
        }

        // Update character visual from animation (if not paused)
        if (!isPaused) {
            characterAnimation?.let { animation ->
                val currentFrame = animation.getKeyFrame(stateTime)
                character.setRegion(currentFrame)
                character.setSize(currentFrame.regionWidth.toFloat(), currentFrame.regionHeight.toFloat())
            }
        }


        // Sync enemy sprites with their physics bodies
        enemies.forEach { enemyBody ->
            val enemySprite = enemyBody.userData as? Sprite
            enemySprite?.let { sprite ->
                val enemyBodyPos = enemyBody.position
                sprite.setPosition(
                    enemyBodyPos.x * WingyNightsGame.PPM - sprite.width / 2,
                    enemyBodyPos.y * WingyNightsGame.PPM - sprite.height / 2
                )
                // Apply rotation from physics
                sprite.rotation = MathUtils.radiansToDegrees * enemyBody.angle
                // Optional: Update enemy animation if they have one (check if !isPaused)
            }
        }

        // --- Begin Drawing ---
        game.batch.begin()

        // Draw background stars (always scroll)
        backgroundStars.forEach { star ->
            star.x -= (0.8f / 4f * 60f) * delta // Use real delta for smooth visual
            if (star.x <= -star.width) {
                star.x += star.width * backgroundStars.size
            }
            star.draw(game.batch)
        }

        // Draw character
        character.draw(game.batch)

        // Draw enemies
        enemies.forEach { enemyBody ->
            (enemyBody.userData as? Sprite)?.draw(game.batch)
        }

        // --- UI Drawing based on State ---
        if (isGameOver) {
            drawGameOverUI()
        } else if (isPaused) {
            drawPauseUI()
        } else if (isGamePlaying) {
            drawGameplayUI()
        } else {
            // Main Menu State
            drawMainMenuUI()
        }

        game.batch.end()
        // --- End Drawing ---


        // --- Input Handling & State Updates ---
        handleInput(delta) // Pass delta for potential time-based input checks

        if (isGamePlaying && !isPaused && !isGameOver) {
            updateGameplay(delta) // Pass effective delta? No, use real delta for timers.
        }
    }


    private fun drawMainMenuUI() {
        title.draw(game.batch)
        playButton.draw(game.batch)
        scoresButton.draw(game.batch) // ArrowDown button
    }

    private fun drawGameplayUI() {
        // Draw Pause Button
        pauseButton.draw(game.batch)

        // Draw Score top-right
        val scoreText = " $score" // Add space for bird alignment
        scoreLayout.setText(gameFont, scoreText)
        val scoreX = Gdx.graphics.width - scoreLayout.width - 20f // Right align with padding
        val scoreY = Gdx.graphics.height - 20f // Top align with padding
        gameFont.draw(game.batch, scoreLayout, scoreX, scoreY)

        // Draw score bird slightly left of the score text
        scoreBirdSprite.setPosition(scoreX - scoreBirdSprite.width - 5f, scoreY - scoreLayout.height / 2 - scoreBirdSprite.height / 2)
        scoreBirdSprite.draw(game.batch)
    }

    private fun drawPauseUI() {
        // Optionally dim the background slightly
        // game.batch.setColor(0f, 0f, 0f, 0.5f) // Example dimming
        // game.batch.draw(pixelTexture, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        // game.batch.setColor(Color.WHITE) // Reset color

        // Draw "Paused" text in center
        val pauseText = "Paused"
        pauseLayout.setText(gameFont, pauseText)
        val textX = Gdx.graphics.width / 2f - pauseLayout.width / 2
        val textY = Gdx.graphics.height / 2f + pauseLayout.height / 2
        gameFont.draw(game.batch, pauseLayout, textX, textY)

        // Draw Play button (reuse texture, position over pause button or center?)
        // Example: Center the play button visually
        playButton.setPosition(Gdx.graphics.width/2f - playButton.width/2f, Gdx.graphics.height/2f - playButton.height * 1.5f)
        playButton.draw(game.batch)

        // Draw Score top-right (still visible when paused)
        val scoreText = " $score"
        scoreLayout.setText(gameFont, scoreText)
        val scoreX = Gdx.graphics.width - scoreLayout.width - 20f
        val scoreY = Gdx.graphics.height - 20f
        gameFont.draw(game.batch, scoreLayout, scoreX, scoreY)
        scoreBirdSprite.setPosition(scoreX - scoreBirdSprite.width - 5f, scoreY - scoreLayout.height / 2 - scoreBirdSprite.height / 2)
        scoreBirdSprite.draw(game.batch)

    }

    private fun drawGameOverUI() {
        // Draw Score / High Score Text Centered
        val scoreStr = "SCORE: $finalScore"
        val highScoreStr = "HIGH SCORE: $highScore"
        gameOverLayout.setText(gameFont, "$scoreStr\n$highScoreStr", Color.WHITE, 0f, Align.center, false) // Center align multi-line

        val textX = Gdx.graphics.width / 2f
        val textY = Gdx.graphics.height / 2f + gameOverLayout.height / 2 + 50f // Position above buttons
        gameFont.draw(game.batch, gameOverLayout, textX, textY) // Draw centered text

        // Draw Buttons
        homeButton.draw(game.batch)
        replayButton.draw(game.batch)
    }


    private fun handleInput(delta: Float) {
        if (Gdx.input.justTouched()) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat() // Invert Y

            if (isGameOver) {
                // Handle Game Over Input
                if (homeButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Home button touched")
                    resetGame(startGame = false) // Go back to main menu state
                } else if (replayButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Replay button touched")
                    restartGame() // Restart the game immediately
                }
            } else if (isPaused) {
                // Handle Paused Input (only check play button)
                // Use the position set in drawPauseUI
                if (playButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Resume button touched")
                    isPaused = false
                    // Restore pause button position
                    pauseButton.setPosition(20f, Gdx.graphics.height - pauseButton.height - 20f)
                }

            } else if (isGamePlaying) {
                // Handle Gameplay Input
                // Check Pause Button FIRST
                if (pauseButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Pause button touched")
                    isPaused = true
                } else {
                    // Handle character movement touch
                    val row = (touchY / separationBetweenLines).toInt().coerceIn(0, 4)
                    teleportToRow(row)
                }
            } else {
                // Handle Main Menu Input
                if (playButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Play button touched")
                    // resetGame(startGame = true) // Reset and start game
                    isGamePlaying = true // Simpler state change, resetGame called in show/restart
                    spawnTimer = 1.0f // Set initial spawn delay
                } else if (scoresButton.boundingRectangle.contains(touchX, touchY)) {
                    Gdx.app.log("Input", "Scores button touched")
                    // Navigate to Scores Screen - pass the loaded high score
                    game.screen.pause() // Pause current screen logic if needed
                    game.setScreen(ScoresScreen(game, highScore)) // Pass high score
                }
            }
        }
    }


    private fun updateGameplay(delta: Float) {
        // Update and remove enemies
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemyBody = iterator.next()
            val bodyX = enemyBody.position.x * WingyNightsGame.PPM
            val spriteWidth = (enemyBody.userData as? Sprite)?.width ?: 0f

            if (bodyX + spriteWidth / 2 < -10f) { // Check if fully off-screen left
                world.destroyBody(enemyBody)
                iterator.remove()
                score++
                Gdx.app.log("Gameplay", "Enemy evaded. Score: $score")
                if (MathUtils.random(0, 2) == 1) {
                    endHighSounds[MathUtils.random(0, 4)].play()
                }
            }
        }

        // Spawn new enemies periodically
        spawnTimer -= delta
        if (spawnTimer <= 0f) {
            spawnEnemy()
            spawnTimer = MathUtils.random(0.5f, 1.5f)
        }
    }

    private fun teleportToRow(row: Int) {
        // ... (teleportToRow code remains the same)
        val targetY = (separationBetweenLines * row + toBetweenRows) / WingyNightsGame.PPM
        characterBody.setTransform(characterBody.position.x, targetY, 0f)
        characterBody.linearVelocity = Vector2(characterBody.linearVelocity.x, 0f)
        teleportSounds[row.coerceIn(0, teleportSounds.size - 1)].play()
        Gdx.app.log("Gameplay", "Teleported to row $row")
    }

    private fun spawnEnemy() {
        // ... (spawnEnemy code mostly the same, ensure body type allows rotation if desired)
        val row = MathUtils.random(0, 4)
        val enemyType = when (MathUtils.random(0, 4)) {
            0 -> "EnemyBlue"
            1 -> "EnemyGreen"
            2 -> "EnemyRed"
            3 -> "EnemyOrange"
            else -> "EnemyPurple"
        }
        val atlas = enemyAtlases[enemyType] ?: return
        val enemyRegion = atlas.regions.firstOrNull() ?: return

        val enemy = Sprite(enemyRegion)
        val spawnX = Gdx.graphics.width.toFloat() + enemy.width / 2f
        val spawnY = separationBetweenLines * row + toBetweenRows

        enemy.setPosition(spawnX, spawnY - enemy.height/2f)
        enemy.setSize(enemyRegion.regionWidth.toFloat(), enemyRegion.regionHeight.toFloat())
        enemy.setOriginCenter()

        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody // Kinematic is fine
            position.set(
                (spawnX + enemy.width / 2f) / WingyNightsGame.PPM,
                (spawnY) / WingyNightsGame.PPM
            )
            // fixedRotation = false // Allow enemies to rotate if needed
        }
        val enemyBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = enemy.width / 2.2f / WingyNightsGame.PPM } // Smaller shape?
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
        // Gdx.app.log("Spawn", "Spawned $enemyType at row $row") // Reduce log spam
    }

    private fun endGame() {
        Gdx.app.log("Game", "Ending Game. Final Score: $score")
        isGamePlaying = false
        isGameOver = true
        isPaused = false // Ensure not paused
        isCharacterRotating = true // Start rotation animation
        finalScore = score // Store final score

        // Stop physics movement (might not be needed if updates stop)
        characterBody.linearVelocity = Vector2.Zero
        characterBody.angularVelocity = 0f // Stop physics rotation before manual override
        enemies.forEach {
            it.linearVelocity = Vector2.Zero
            it.angularVelocity = 0f
        }

        // Update High Score if needed
        if (score > highScore) {
            Gdx.app.log("Game", "New High Score: $score")
            highScore = score
            prefs.putInteger("highScore", highScore)
            prefs.flush() // Save preferences immediately
        }
    }

    override fun resize(width: Int, height: Int) {
        // ... (resize code remains the same)
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun pause() {
        Gdx.app.log("Game", "Paused (Lifecycle)")
        // If the game is actively playing, enter the visual paused state
        if (isGamePlaying && !isGameOver) {
            isPaused = true
        }
    }

    override fun resume() {
        Gdx.app.log("Game", "Resumed (Lifecycle)")
        // Don't automatically unpause, user must tap resume button
    }

    override fun hide() {
        Gdx.app.log("Game", "Hiding MainScreen")
        // Could potentially save state or pause music here
        isPaused = true // Ensure game logic stops if screen hidden mid-game
    }

    override fun dispose() {
        Gdx.app.log("Game", "Disposing MainScreen assets")
        // Dispose Textures
        character.texture.dispose()
        playButtonTexture.dispose()
        scoresButtonTexture.dispose()
        titleTexture.dispose()
        pauseButtonTexture.dispose()
        homeButtonTexture.dispose()
        replayButtonTexture.dispose()
        scoreBirdSprite.texture.dispose() // Dispose if it used its own texture (fallback)
        backgroundStars.forEach { it.texture.dispose() }

        // Dispose Atlases
        characterSleepingAtlas.dispose()
        enemyAtlases.values.forEach { it.dispose() }

        // Dispose Sounds
        teleportSounds.forEach { it.dispose() }
        beginLowSounds.forEach { it.dispose() }
        endHighSounds.forEach { it.dispose() }
        collisionSound.dispose()

        // Dispose Font related objects
        gameFont.dispose()
        if (::fontGenerator.isInitialized) fontGenerator.dispose() // Check if initialized before disposing


        // Dispose physics world
        world.dispose()

        Gdx.app.log("Dispose", "MainScreen disposed.")
    }
}
