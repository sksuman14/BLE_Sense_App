package com.blesense.app.model.game

import com.blesense.app.R

/**
 * Game Modes available in BLE Game
 */
enum class BleGameMode {
    SINGLE_PLAYER,
    MULTIPLAYER,
    BOMB_DEFUSAL
}

/**
 * Status of the 3-minute Superhero Bomb Defusal Mission
 */
enum class BombDefusalStatus {
    IDLE,       // Mission not started
    DEFUSING,   // Active 3-min countdown, searching for device
    DEFUSED,    // WIN: Device found with RSSI >= -40 dBm within 3 minutes
    EXPLODED    // LOSE: 3 minutes elapsed without reaching -40 dBm
}

/**
 * A detected BLE beacon representing a hero
 */
data class HeroBeacon(
    val name: String,
    val address: String,
    var rssi: Int = -99,
    var isFound: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Registry and Constants matching C:\Users\1239\Desktop\BLEGame
 */
object HeroRegistry {
    val TARGET_DEVICE_NAMES = setOf(
        "Scarlet Witch",
        "Black Widow",
        "Captain Marvel",
        "Wasp",
        "Hela",
        "Hulk",
        "Thor",
        "Iron_Man",
        "Spider Man",
        "Captain America"
    )

    val CHARACTER_IMAGES = mapOf(
        "Scarlet Witch" to R.drawable.scarlet_witch,
        "Black Widow" to R.drawable.black_widow,
        "Captain Marvel" to R.drawable.captain_marvel,
        "Wasp" to R.drawable.wasp,
        "Hela" to R.drawable.hela,
        "Hulk" to R.drawable.hulk3,
        "Thor" to R.drawable.thor,
        "Iron_Man" to R.drawable.iron_man1,
        "Spider Man" to R.drawable.spider_man,
        "Captain America" to R.drawable.captain_america
    )

    fun getCharacterImage(name: String?): Int {
        return CHARACTER_IMAGES[name] ?: R.drawable.notfound
    }

    fun getVictoryPopupImage(name: String?): Int {
        return when (name) {
            "Iron_Man" -> R.drawable.ironman
            "Hulk" -> R.drawable.hulk3
            else -> CHARACTER_IMAGES[name] ?: R.drawable.device_found_image
        }
    }
}
