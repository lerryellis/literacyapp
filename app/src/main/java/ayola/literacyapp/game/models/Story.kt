package ayola.literacyapp.game.models

import com.google.gson.annotations.SerializedName

data class Story(
    val id: Int,
    val title: String,
    val content: String,
    @SerializedName("age_group") val ageGroup: String,
    @SerializedName("life_skill") val lifeSkill: String,
    @SerializedName("life_skill_lesson") val lifeSkillLesson: String
)
