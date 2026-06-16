package ayola.literacyapp.game.models

import com.google.gson.annotations.SerializedName

/**
 * Matches POST api/students/ payload.
 * `id` is null when creating; the backend returns the full object with an id
 * (HTTP 201 for a new child, HTTP 200 for a returning one).
 */
data class Student(
    val id: Int? = null,
    val name: String,
    @SerializedName("device_id") val deviceId: String,
    val school: String,
    @SerializedName("age_group") val ageGroup: String
)
