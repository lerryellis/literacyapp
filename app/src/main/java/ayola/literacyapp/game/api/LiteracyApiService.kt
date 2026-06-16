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

    /**
     * Create (or fetch a returning) student.
     * Returns 201 for a new child and 200 for a returning one — both carry the
     * full Student object with an id. We return Response<Student> so callers can
     * inspect the status code if they need to distinguish the two cases.
     */
    @POST("api/students/")
    suspend fun createStudent(@Body student: Student): Response<Student>

    /** List stories, optionally filtered by age group. */
    @GET("api/stories/")
    suspend fun getStories(
        @Query("age_group") ageGroup: String? = null
    ): Response<List<Story>>

    /** Record a completed reading session. */
    @POST("api/sessions/")
    suspend fun createSession(@Body session: SessionRequest): Response<SessionRequest>
}
