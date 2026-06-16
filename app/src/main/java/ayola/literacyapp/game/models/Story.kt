package ayola.literacyapp.game.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Story(
    val id: Int,
    val title: String,
    val content: String,
    @SerializedName("age_group") val ageGroup: String,
    @SerializedName("life_skill") val lifeSkill: String,
    @SerializedName("life_skill_lesson") val lifeSkillLesson: String
)

/**
 * The backend sends `content` as a stringified JSON array of sentences, e.g.
 * `"[\"Kofi had a box...\", \"His friend Ama...\"]"`. Parse it into real sentences.
 * Falls back to treating the raw content as a single sentence if it isn't valid JSON.
 */
fun Story.getSentences(): List<String> =
    try {
        Gson().fromJson(content, Array<String>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) {
        if (content.isBlank()) emptyList() else listOf(content)
    }
