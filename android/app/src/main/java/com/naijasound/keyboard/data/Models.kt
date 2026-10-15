package com.naijasound.keyboard.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Clip(
    val id: String,
    val slug: String = "",
    val title: String,
    val category: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("mp4_url") val mp4Url: String,
    @SerialName("mp3_url") val mp3Url: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration_sec") val durationSec: Double = 0.0,
    @SerialName("play_count") val playCount: Long = 0,
    @SerialName("send_count") val sendCount: Long = 0,
    val pinned: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Category(
    val id: Int = 0,
    val name: String,
    val icon: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** Used until the real list has been loaded once, so the app never shows an empty menu. */
val DefaultCategories = listOf(
    Category(1, "Laughing", "😂", 1),
    Category(2, "Vex", "😤", 2),
    Category(3, "Shock", "😱", 3),
    Category(4, "Leave me alone", "🙅", 4),
    Category(5, "Praise", "🙌", 5),
    Category(6, "Broke", "💸", 6),
    Category(7, "Love", "❤️", 7),
    Category(8, "Sarcasm", "🙄", 8),
)
