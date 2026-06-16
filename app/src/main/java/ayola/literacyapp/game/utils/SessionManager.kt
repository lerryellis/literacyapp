package ayola.literacyapp.game.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * Lightweight persistence for the current student and the device identity.
 * Backed by SharedPreferences; uses ANDROID_ID as the stable device_id.
 */
class SessionManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveStudentId(studentId: Int) {
        prefs.edit().putInt(KEY_STUDENT_ID, studentId).apply()
    }

    /** Returns the saved student id, or null if none has been stored yet. */
    fun getStudentId(): Int? =
        if (prefs.contains(KEY_STUDENT_ID)) prefs.getInt(KEY_STUDENT_ID, -1) else null

    fun clearStudentId() {
        prefs.edit().remove(KEY_STUDENT_ID).apply()
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)

    companion object {
        private const val PREFS_NAME = "literacy_app_prefs"
        private const val KEY_STUDENT_ID = "student_id"
    }
}
