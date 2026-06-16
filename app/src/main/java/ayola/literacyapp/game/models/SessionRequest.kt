package ayola.literacyapp.game.models

import com.google.gson.annotations.SerializedName

/**
 * Matches POST api/sessions/ payload.
 * `student` and `story` are their respective ids (foreign keys).
 */
data class SessionRequest(
    val student: Int,
    val story: Int,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("accuracy_percent") val accuracyPercent: Double,
    @SerializedName("difficult_words") val difficultWords: List<String>
)
