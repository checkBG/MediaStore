package org.example.project.cloud

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.example.project.cloud.model.RemoveFileRequest
import org.example.project.cloud.network.CloudMailApi
import retrofit2.Retrofit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudMailApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CloudMailApi

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
        }

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(CloudMailApi::class.java)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun removeFileSendsJsonBody() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "email": "user@mail.ru",
                      "body": "/test.jpg",
                      "status": 200,
                      "time": 1794937775296
                    }
                    """.trimIndent()
                )
        )

        val response = api.removeFile(
            body = RemoveFileRequest(
                xEmail = "user@mail.ru",
                home = "/test.jpg"
            )
        )

        assertEquals(200, response.status)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/file/remove", request.path)

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"home\":\"/test.jpg\""))
        assertTrue(body.contains("\"x-email\":\"user@mail.ru\""))
    }
}
