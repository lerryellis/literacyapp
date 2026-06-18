package ayola.literacyapp.game.ui.theme

import androidx.compose.ui.graphics.Color

/** An emoji + accent color for a life-skill, used to color-code story cards & the lesson screen. */
data class SkillVisual(val emoji: String, val color: Color)

private val skillPalette = listOf(
    Color(0xFFFB7A23), // orange
    Color(0xFF12A594), // teal
    Color(0xFF7B4DD8), // grape
    Color(0xFF2E9E5B), // green
    Color(0xFFE0457B), // pink
    Color(0xFF2D7FF0), // blue
    Color(0xFFEAA300), // amber
    Color(0xFF00897B), // deep teal
    Color(0xFFB5562A), // terracotta
)

fun skillVisual(skill: String): SkillVisual {
    val key = skill.trim().lowercase()
    val emoji = when (key) {
        "interpersonal" -> "🤝"
        "self-awareness" -> "🪞"
        "communication" -> "🗣️"
        "teamwork" -> "🧩"
        "critical thinking" -> "🧠"
        "leadership" -> "🌟"
        "time management" -> "⏰"
        "integrity" -> "🛡️"
        "civic responsibility" -> "🌍"
        else -> "📘"
    }
    val color = skillPalette[(key.hashCode() and 0x7fffffff) % skillPalette.size]
    return SkillVisual(emoji, color)
}
