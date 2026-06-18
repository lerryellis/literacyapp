package ayola.literacyapp.game.utils

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns raw exceptions / HTTP codes into friendly, child-and-parent-appropriate messages,
 * while logging the real cause for developers (filter Logcat by tag "LiteracyApp").
 */
object ErrorMessages {
    private const val TAG = "LiteracyApp"

    /** Map a thrown error to a friendly message (and log the technical detail). */
    fun forException(context: String, t: Throwable): String {
        Log.e(TAG, "$context failed: ${t.javaClass.simpleName}: ${t.message}", t)
        return when (t) {
            is UnknownHostException ->
                "No internet connection. Please check your Wi-Fi or mobile data and try again."
            is SocketTimeoutException ->
                "The connection timed out. Please try again."
            is ConnectException ->
                "Couldn't reach the server. Please check your connection and try again."
            is SSLException ->
                "There was a secure-connection problem. Please try again."
            is IOException ->
                "Network problem. Please check your connection and try again."
            else ->
                "Something went wrong. Please try again."
        }
    }

    /** Map an unsuccessful HTTP response to a friendly message (and log the code). */
    fun forHttp(context: String, code: Int): String {
        Log.w(TAG, "$context returned HTTP $code")
        return when (code) {
            in 500..599 -> "Our server is having a problem right now. Please try again soon."
            408, 429 -> "The server is busy. Please wait a moment and try again."
            404 -> "We couldn't find that. Please try again."
            in 400..499 -> "Please check the details you entered and try again."
            else -> "Something went wrong (code $code). Please try again."
        }
    }
}
