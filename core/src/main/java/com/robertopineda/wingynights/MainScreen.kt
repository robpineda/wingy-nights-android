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
import com.badlogic.gdx.scenes.scene2d.Stage // Potentially needed for UI later
import com.badlogic.gdx.scenes.scene2d.ui.Skin // Potentially needed for UI later
import com.badlogic.gdx.utils.viewport.ScreenViewport // Potentially needed for UI later
import com.badlogic.gdx.utils.Array as GdxArray
import kotlin.collections.Map // Use Kotlin's Map for atlases
import kotlin.collections.listOf // Use Kotlin's listOf
import kotlin.collections.forEach // Use Kotlin's forEach
import kotlin.collections.set // Use Kotlin's set extension

class MainScreen(private val game: WingyNightsGame) : Screen {
    private val camera = OrthographicCamera()
    private val world = World(Vector2(0f, 0f), true) // Physics world
    private var character: Sprite
    private lateinit var characterBody: Body
    private val enemies = GdxArray<Body>() // Store enemy bodies, sprites accessed via userData
    private var characterSleepingAtlas: TextureAtlas
    private var characterAnimation: Animation<TextureRegion>? = null // Nullable to handle empty frames
    private var stateTime = 0f
    private lateinit var backgroundStars: GdxArray<Sprite>

    // Sounds
    private lateinit var teleportSounds: Array<Sound>
    private lateinit var beginLowSounds: Array<Sound>
    private lateinit var endHighSounds: Array<Sound>
    private lateinit var collisionSound: Sound

    // Menu Assets
    private lateinit var playButtonTexture: Texture
    private lateinit var scoresButtonTexture: Texture
    private lateinit var titleTexture: Texture
    private lateinit var playButton: Sprite
    private lateinit var scoresButton: Sprite
    private lateinit var title: Sprite

    // Enemy Assets
    private lateinit var enemyAtlases: Map<String, TextureAtlas>

    // Font for Score
    private lateinit var font: BitmapFont

    // Game State
    private var isGamePlaying = false
    private var score = 0 // Changed counterEnemies to score for clarity
    private var spawnTimer = 0f
    private val separationBetweenLines = Gdx.graphics.height / 5f
    private val toBetweenRows = separationBetweenLines / 2f // Center offset within a row
    private var isGameOver = false


    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        characterSleepingAtlas = TextureAtlas(Gdx.files.internal("Atlases/CharacterSleeping.atlas"))
        character = Sprite(Texture(Gdx.files.internal("Characters/Character.png"))) // Keep initial texture until animation loads
        character.setPosition(Gdx.graphics.width / 12f, separationBetweenLines * 2 + toBetweenRows - character.height / 2f) // Initial position centered in row 2

        setupAssets()
        setupBackground()
        setupMenuSprites() // Initialize menu sprites after textures are loaded
    }

    private fun setupAssets() {
        // Character animation
        val frames = GdxArray<TextureRegion>()
        // Ensure regions are sorted if atlas order matters, though usually alphabetical
        characterSleepingAtlas.regions.forEach { frames.add(it) }

        if (frames.size > 0) {
            characterAnimation = Animation(0.07f, frames, Animation.PlayMode.LOOP)
            Gdx.app.log("MainScreen", "Character animation initialized with ${frames.size} frames")
            // Set initial frame
            character.setRegion(characterAnimation?.getKeyFrame(0f))
            character.setSize(character.regionWidth.toFloat(), character.regionHeight.toFloat()) // Adjust sprite size to animation frame
        } else {
            Gdx.app.error("MainScreen", "Character animation has no frames!")
            // Keep the single texture loaded in init if animation fails
        }
        character.setOriginCenter() // Set origin for potential rotation/scaling


        // Sounds
        teleportSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsTeleport/SoundTeleport${it}.mp3")) }
        beginLowSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsBeginLow/SoundBeginLow${it}.mp3")) }
        endHighSounds = Array(5) { Gdx.audio.newSound(Gdx.files.internal("Sounds/SoundsEndHigh/SoundEndHigh${it}.mp3")) }
        collisionSound = Gdx.audio.newSound(Gdx.files.internal("Sounds/OtherSounds/SoundCollision.mp3"))

        // Menu Textures
        playButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonPlay.png"))
        scoresButtonTexture = Texture(Gdx.files.internal("Buttons/ButtonHome.png")) // Assuming ButtonHome is for scores screen access from main menu
        titleTexture = Texture(Gdx.files.internal("Symbols/Title.png"))

        // Enemy Atlases
        val enemyTypes = listOf("EnemyBlue", "EnemyGreen", "EnemyRed", "EnemyOrange", "EnemyPurple")
        enemyAtlases = enemyTypes.associateWith { TextureAtlas(Gdx.files.internal("Atlases/$it.atlas")) }

        // Font
        font = BitmapFont() // Use default libGDX font for now
        font.color = com.badlogic.gdx.graphics.Color.WHITE
        font.data.setScale(2f) // Make font larger
    }

    private fun setupMenuSprites() {
        playButton = Sprite(playButtonTexture)
        playButton.setPosition(Gdx.graphics.width / 2f - playButton.width / 2, separationBetweenLines * 2f - playButton.height / 2f) // Centered vertically in its rough area

        scoresButton = Sprite(scoresButtonTexture)
        // Position scores button bottom-right, adjust as needed for original look
        scoresButton.setPosition(Gdx.graphics.width - scoresButton.width * 1.5f, scoresButton.height * 0.5f)

        title = Sprite(titleTexture)
        title.setPosition(Gdx.graphics.width / 2f - title.width / 2, separationBetweenLines * 3f + toBetweenRows)
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
            // Assuming textures are screen height, center them vertically
            star.setPosition(Gdx.graphics.width * index.toFloat(), 0f)
            // star.setOriginCenter() // Origin not needed if not rotating/scaling
            backgroundStars.add(star)
        }
    }

    override fun show() {
        // Initialize physics for character only once when the screen is shown
        if (!::characterBody.isInitialized) { // Prevent re-initialization on resume
            val bodyDef = BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                // Set initial physics position based on sprite's current position
                position.set(
                    (character.x + character.width / 2) / WingyNightsGame.PPM,
                    (character.y + character.height / 2) / WingyNightsGame.PPM
                )
                fixedRotation = true // Prevent character rotation due to physics
            }
            characterBody = world.createBody(bodyDef)
            val shape = CircleShape().apply { radius = character.width / 2 / WingyNightsGame.PPM }
            val fixtureDef = FixtureDef().apply {
                this.shape = shape
                density = 1f
                isSensor = false // Character should collide physically
            }
            characterBody.createFixture(fixtureDef).userData = "character"
            shape.dispose()
            characterBody.userData = character // Link body back to sprite if needed


            // Contact listener for collisions
            world.setContactListener(object : ContactListener {
                override fun beginContact(contact: Contact) {
                    val fixtureA = contact.fixtureA
                    val fixtureB = contact.fixtureB
                    Gdx.app.log("Collision", "Contact between ${fixtureA.userData} and ${fixtureB.userData}")

                    // Check if collision involves the character and an enemy
                    val isCharA = fixtureA.userData == "character"
                    val isCharB = fixtureB.userData == "character"
                    val isEnemyA = fixtureA.userData == "enemy"
                    val isEnemyB = fixtureB.userData == "enemy"

                    if ((isCharA && isEnemyB) || (isCharB && isEnemyA)) {
                        if (!isGameOver) {
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
        // Reset game state if returning to this screen
        isGamePlaying = false
        isGameOver = false
        score = 0
        // Clear any existing enemies from previous game instances
        enemies.forEach { world.destroyBody(it) }
        enemies.clear()
        // Reset character position
        val initialX = Gdx.graphics.width / 12f
        val initialY = separationBetweenLines * 2 + toBetweenRows - character.height / 2f
        character.setPosition(initialX, initialY)
        characterBody.setTransform((initialX + character.width/2f) / WingyNightsGame.PPM, (initialY + character.height/2f) / WingyNightsGame.PPM, 0f)
        characterBody.linearVelocity = Vector2(0f, 0f) // Ensure character starts stationary

    }

    override fun render(delta: Float) {
        // Clear screen
        Gdx.gl.glClearColor(24f / 255f, 55f / 255f, 78f / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Update camera
        camera.update()
        game.batch.projectionMatrix = camera.combined

        // Step the physics world
        world.step(1f / 60f, 6, 2) // Use a fixed time step

        // Update character animation state time
        stateTime += delta
        characterAnimation?.let { animation ->
            val currentFrame = animation.getKeyFrame(stateTime)
            character.setRegion(currentFrame)
            // Ensure sprite size matches frame size if frames differ
            character.setSize(currentFrame.regionWidth.toFloat(), currentFrame.regionHeight.toFloat())
        }

        // Sync character sprite position with physics body
        val charBodyPos = characterBody.position
        character.setPosition(
            charBodyPos.x * WingyNightsGame.PPM - character.width / 2,
            charBodyPos.y * WingyNightsGame.PPM - character.height / 2
        )

        // Sync enemy sprites with their physics bodies
        enemies.forEach { enemyBody ->
            val enemySprite = enemyBody.userData as? Sprite
            enemySprite?.let { sprite ->
                val enemyBodyPos = enemyBody.position
                sprite.setPosition(
                    enemyBodyPos.x * WingyNightsGame.PPM - sprite.width / 2,
                    enemyBodyPos.y * WingyNightsGame.PPM - sprite.height / 2
                )
                // Optional: Update enemy animation if they have one
                // val enemyAtlas = enemyAtlases[...] // Get atlas based on type stored somewhere
                // val enemyAnim = Animation(...)
                // sprite.setRegion(enemyAnim.getKeyFrame(stateTime))
            }
        }


        game.batch.begin()

        // Draw background stars
        backgroundStars.forEach { star ->
            // Adjust speed calculation: pixels per second
            star.x -= (0.8f / 4f * 60f) * delta // Speed independent of frame rate
            if (star.x <= -star.width) { // Use star width for wrapping
                star.x += star.width * backgroundStars.size // Wrap around correctly
            }
            star.draw(game.batch)
        }

        // Draw character
        character.draw(game.batch)

        // Draw enemies
        enemies.forEach { enemyBody ->
            (enemyBody.userData as? Sprite)?.draw(game.batch)
        }


        if (!isGamePlaying) {
            drawMenu()
        } else if (!isGameOver) {
            // Draw score only when playing
            font.draw(game.batch, "Score: $score", 20f, Gdx.graphics.height - 20f)
        }

        game.batch.end()

        // Handle Input and Updates based on state
        if (isGameOver) {
            // Optionally add a delay or game over screen elements here
            // For now, directly go to ScoresScreen
            // game.setScreen(ScoresScreen(game, score)) // Handled in endGame()
        } else if (isGamePlaying) {
            updateGameplay(delta)
        } else {
            handleMenuInput() // Handle menu input only when menu is showing
        }
    }

    private fun drawMenu() {
        // Sprites are already positioned in setupMenuSprites
        title.draw(game.batch)
        playButton.draw(game.batch)
        scoresButton.draw(game.batch)
    }

    private fun handleMenuInput() {
        if (Gdx.input.justTouched()) { // Use justTouched to detect a tap
            val touchX = Gdx.input.x.toFloat()
            // LibGDX Y coordinate is 0 at top, screen Y is 0 at bottom
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()

            // Use sprite bounding rectangles for hit detection
            if (playButton.boundingRectangle.contains(touchX, touchY)) {
                Gdx.app.log("Input", "Play button touched")
                isGamePlaying = true
                // Reset score and potentially other game state needed for a new game
                score = 0
                isGameOver = false
                // Clear existing enemies before starting
                enemies.forEach { world.destroyBody(it) }
                enemies.clear()
                //spawnEnemies() // Initial burst of enemies (optional)
                spawnTimer = 1f // Add initial delay before first spawn
            } else if (scoresButton.boundingRectangle.contains(touchX, touchY)) {
                Gdx.app.log("Input", "Scores button touched")
                // Pass the current high score (needs to be loaded) instead of the last game's score
                game.setScreen(ScoresScreen(game, 0)) // Placeholder score for now
            }
        }
    }


    private fun updateGameplay(delta: Float) {
        // Touch controls for character movement
        if (Gdx.input.justTouched()) {
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat() // Convert touch Y to screen Y
            // Determine row based on touch position
            val row = (touchY / separationBetweenLines).toInt().coerceIn(0, 4)
            teleportToRow(row)
        }

        // Update and remove enemies
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemyBody = iterator.next()
            val enemySprite = enemyBody.userData as? Sprite

            // Check if enemy is off-screen (use physics position)
            val bodyX = enemyBody.position.x * WingyNightsGame.PPM
            val spriteWidth = enemySprite?.width ?: 0f // Get width from sprite

            if (bodyX + spriteWidth / 2 < 0) { // Check if right edge is off-screen left
                // Safely remove body and sprite reference
                world.destroyBody(enemyBody)
                iterator.remove() // Remove from GdxArray

                score++ // Increment score when enemy is successfully evaded
                Gdx.app.log("Gameplay", "Enemy evaded. Score: $score")

                // Play sound cue for successful evasion (optional)
                if (MathUtils.random(0, 2) == 1) { // Random chance for sound
                    endHighSounds[MathUtils.random(0, 4)].play()
                }
            }
        }

        // Spawn new enemies periodically
        spawnTimer -= delta
        if (spawnTimer <= 0f) {
            spawnEnemy()
            // Reset timer with some randomness (adjust values for difficulty)
            spawnTimer = MathUtils.random(0.5f, 1.5f) // Example: spawn every 0.5 to 1.5 seconds
            Gdx.app.log("Gameplay", "Spawning enemy. Next spawn in $spawnTimer s")
        }
    }

    private fun teleportToRow(row: Int) {
        // Calculate target Y position in world units, centered vertically in the row
        val targetY = (separationBetweenLines * row + toBetweenRows) / WingyNightsGame.PPM
        // Keep current X, change Y. Body position is center.
        characterBody.setTransform(characterBody.position.x, targetY, 0f)
        // Stop any residual vertical velocity from previous interactions (if any)
        characterBody.linearVelocity = Vector2(characterBody.linearVelocity.x, 0f)

        // Play sound corresponding to the target row
        teleportSounds[row.coerceIn(0, teleportSounds.size - 1)].play() // Ensure row index is valid
        Gdx.app.log("Gameplay", "Teleported to row $row")
    }


    private fun spawnEnemy() {
        val row = MathUtils.random(0, 4)
        val enemyType = when (MathUtils.random(0, 4)) {
            0 -> "EnemyBlue"
            1 -> "EnemyGreen"
            2 -> "EnemyRed"
            3 -> "EnemyOrange"
            else -> "EnemyPurple"
        }
        val atlas = enemyAtlases[enemyType]
        if (atlas == null) {
            Gdx.app.error("Spawn", "Atlas not found for type $enemyType")
            return
        }

        // Use the first region found in the atlas for the enemy sprite
        val enemyRegion = atlas.regions.firstOrNull()
        if (enemyRegion == null) {
            Gdx.app.error("Spawn", "No regions found in atlas for type $enemyType")
            return
        }

        val enemy = Sprite(enemyRegion)
        // Calculate spawn position (right edge of screen, centered in the row)
        val spawnX = Gdx.graphics.width.toFloat() + enemy.width / 2f // Spawn just off-screen right
        val spawnY = separationBetweenLines * row + toBetweenRows // Center Y in the row

        enemy.setPosition(spawnX, spawnY - enemy.height/2f) // Set sprite initial position for size reference
        enemy.setSize(enemyRegion.regionWidth.toFloat(), enemyRegion.regionHeight.toFloat())
        enemy.setOriginCenter()


        // Create physics body for the enemy
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody // Use Kinematic to control velocity directly
            position.set(
                (spawnX + enemy.width / 2f) / WingyNightsGame.PPM, // Initial physics pos (center)
                (spawnY) / WingyNightsGame.PPM
            )
        }
        val enemyBody = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = enemy.width / 2 / WingyNightsGame.PPM } // Adjust radius based on sprite
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            isSensor = false // Make enemies solid for collision detection
        }
        enemyBody.createFixture(fixtureDef).userData = "enemy" // Tag fixture for collision identification
        shape.dispose()

        enemyBody.userData = enemy // Link body to its sprite

        // Set constant velocity moving left (adjust speed as needed)
        val enemySpeed = -200f / WingyNightsGame.PPM // Speed in world units per second
        enemyBody.linearVelocity = Vector2(enemySpeed, 0f)


        enemies.add(enemyBody) // Add the BODY to the list

        // Play spawn sound (optional)
        if (MathUtils.random(0, 2) == 2) { // Random chance
            beginLowSounds[row.coerceIn(0, beginLowSounds.size - 1)].play()
        }
        Gdx.app.log("Spawn", "Spawned $enemyType at row $row")
    }


    // This function seems redundant if spawnEnemy handles timed spawning.
    // Kept for now if an initial burst is desired when game starts.
    // private fun spawnEnemies() {
    //    repeat(5) { spawnEnemy() } // Initial spawn for each row (potential overlap?)
    // }

    private fun endGame() {
        if (!isGameOver) { // Prevent multiple calls
            Gdx.app.log("Game", "Ending Game. Final Score: $score")
            isGamePlaying = false
            isGameOver = true
            // Stop character/enemy movement?
            characterBody.linearVelocity = Vector2.Zero
            enemies.forEach { it.linearVelocity = Vector2.Zero }

            // TODO: Add delay or Game Over display before changing screen
            // For now, immediate transition:
            // Need to load high score before going to scores screen
            game.setScreen(ScoresScreen(game, score)) // Pass the final score
        }
    }


    override fun resize(width: Int, height: Int) {
        // Update camera to new dimensions, maintain aspect ratio
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        // If using Stage/Viewport for UI, update it here
        // viewport.update(width, height, true)
    }

    override fun pause() {
        // Handle game pausing (e.g., stop sounds, timers)
        Gdx.app.log("Game", "Paused")
    }

    override fun resume() {
        // Handle game resuming (e.g., restart sounds, timers)
        Gdx.app.log("Game", "Resumed")
        // Assets might need reloading if context was lost, handled by LibGDX usually
    }

    override fun hide() {
        // Called when this screen is no longer the current screen
        Gdx.app.log("Game", "Hiding MainScreen")
        // Consider stopping sounds here if they shouldn't persist
    }

    override fun dispose() {
        Gdx.app.log("Game", "Disposing MainScreen assets")
        // Dispose all Disposable resources
        characterSleepingAtlas.dispose()
        character.texture.dispose() // Dispose base texture if it wasn't from atlas
        teleportSounds.forEach { it.dispose() }
        beginLowSounds.forEach { it.dispose() }
        endHighSounds.forEach { it.dispose() }
        collisionSound.dispose()
        backgroundStars.forEach { it.texture.dispose() } // Dispose background textures
        playButtonTexture.dispose()
        scoresButtonTexture.dispose()
        titleTexture.dispose()
        enemyAtlases.values.forEach { it.dispose() } // Dispose all enemy atlases
        font.dispose()

        // Dispose physics world
        world.dispose()
        // If using Stage/Skin for UI, dispose them here
        // stage.dispose()
        // skin.dispose()
    }
}
