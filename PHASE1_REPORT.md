# Phase 1 Implementation Report — Networking Foundation

**Status:** Complete & building (`./gradlew assembleDebug` -> BUILD SUCCESSFUL, 16 MB debug APK produced)
**Package:** `ayola.literacyapp.game` | min SDK 23 / target 36 | Pure Kotlin + Jetpack Compose
**Backend target:** local Django at `http://10.0.2.2:8001/` (emulator -> host loopback)

---

## 1. What changed

| File | Type | Purpose |
|---|---|---|
| `app/build.gradle.kts` | modified | Added Retrofit, Gson converter, OkHttp logging, navigation-compose |
| `app/src/main/AndroidManifest.xml` | modified | `INTERNET` permission + `usesCleartextTraffic="true"` |
| `api/LiteracyApiService.kt` | new | Retrofit interface — 3 endpoints |
| `api/RetrofitClient.kt` | new | Singleton client, base URL `10.0.2.2:8001`, logging interceptor |
| `models/Student.kt` | new | Student payload/response model |
| `models/Story.kt` | new | Story response model |
| `models/SessionRequest.kt` | new | Session POST payload |
| `utils/SessionManager.kt` | new | SharedPreferences + ANDROID_ID |

All 6 source files are currently untracked in git; the two modified files are not yet committed. Nothing has been committed/pushed yet.

---

## 2. Dependencies added (`app/build.gradle.kts`)

```kotlin
// Networking (Phase 1)
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")
```

Note: the project uses a Gradle version catalog (`gradle/libs.versions.toml`), but these were added as
direct version strings to match the prompt exactly. They can be migrated into the catalog later.

---

## 3. Manifest changes (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:allowBackup="true"
    android:usesCleartextTraffic="true"
    ... >
```

`usesCleartextTraffic="true"` is required so Android 9+ (API 28+) does not block plain HTTP to the
local Django server.

---

## 4. Networking layer

### `api/LiteracyApiService.kt`

```kotlin
package ayola.literacyapp.game.api

import ayola.literacyapp.game.models.SessionRequest
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.models.Student
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LiteracyApiService {

    // Returns 201 for a new child and 200 for a returning one; both carry the
    // full Student object with an id. Response<Student> lets callers read .code().
    @POST("api/students/")
    suspend fun createStudent(@Body student: Student): Response<Student>

    @GET("api/stories/")
    suspend fun getStories(
        @Query("age_group") ageGroup: String? = null
    ): Response<List<Story>>

    @POST("api/sessions/")
    suspend fun createSession(@Body session: SessionRequest): Response<SessionRequest>
}
```

### `api/RetrofitClient.kt`

```kotlin
package ayola.literacyapp.game.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "http://10.0.2.2:8001/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: LiteracyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LiteracyApiService::class.java)
    }
}
```

---

## 5. Data models

```kotlin
// models/Student.kt
data class Student(
    val id: Int? = null,                                   // null on create; populated in response
    val name: String,
    @SerializedName("device_id") val deviceId: String,
    val school: String,
    @SerializedName("age_group") val ageGroup: String
)

// models/Story.kt   (ASSUMPTION — confirm against the serializer)
data class Story(
    val id: Int,
    val title: String,
    val content: String,
    @SerializedName("age_group") val ageGroup: String
)

// models/SessionRequest.kt
data class SessionRequest(
    val student: Int,                                      // FK id
    val story: Int,                                        // FK id
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("accuracy_percent") val accuracyPercent: Double,
    @SerializedName("difficult_words") val difficultWords: List<String>
)
```

---

## 6. Storage (`utils/SessionManager.kt`)

```kotlin
package ayola.literacyapp.game.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

class SessionManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveStudentId(studentId: Int) {
        prefs.edit().putInt(KEY_STUDENT_ID, studentId).apply()
    }

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
```

---

## 7. Endpoint -> backend mapping (review carefully)

| HTTP | Path | Request body | Returns | Notes |
|---|---|---|---|---|
| POST | `api/students/` | `name, device_id, school, age_group` | `Student` (with id) | 200 returning / 201 new, via `.code()` |
| GET | `api/stories/` | optional `?age_group=` | `List<Story>` | query omitted when null |
| POST | `api/sessions/` | `student, story, duration_seconds, accuracy_percent, difficult_words` | echo | `student`/`story` are integer FKs |

JSON keys are snake_case via `@SerializedName`, mapped to Kotlin camelCase.

---

## 8. Open questions / assumptions to confirm before Phase 2

1. **`Story` shape is guessed** — used `id, title, content, age_group`. If the Django serializer returns
   different/more fields (e.g. `body` vs `content`, `difficulty`, `image_url`), Gson leaves them null.
   Action: share the serializer.
2. **`difficult_words: List<String>`** — confirm the backend expects a JSON array (not a comma-joined
   string or list of objects).

---

## 9. Decisions made (flag if you disagree)

- Dependencies added as direct version strings (not the version catalog) to match the prompt.
- `navigation-compose` pinned to 2.7.7 (prompt gave no version).
- `Response<T>` wrappers instead of raw `T` so status codes / errors are handled — needed for 200-vs-201.
- No DI (Hilt). `RetrofitClient` is a plain object singleton; `SessionManager` takes a Context.

---

## 10. Recommended next steps

1. Verify the API contract (share serializers) to lock `Story` and `difficult_words`.
2. Commit Phase 1 to the private repo. Suggested message: `Phase 1: networking layer, models, session storage`.
3. Phase 2: add a thin Repository wrapping `RetrofitClient` + `SessionManager`, then the first Compose
   screen (student onboarding calling `createStudent`), so the UI never touches Retrofit directly.
