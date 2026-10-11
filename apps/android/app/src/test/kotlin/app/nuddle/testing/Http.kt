package app.nuddle.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.db.NuddleDatabase
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val testJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

fun MockWebServer.retrofit(client: OkHttpClient = OkHttpClient()): Retrofit =
    Retrofit
        .Builder()
        .baseUrl(url("/"))
        .client(client)
        .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
        .build()

fun json(
    code: Int,
    body: String,
): MockResponse =
    MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

fun drtError(
    code: Int,
    drt: String,
    name: String,
) = json(code, """{"error":{"code":"$drt","name":"$name","message":"x","requestId":"req-1"}}""")

/** Room in memory on Robolectric (docs/testing.md, Android data). */
fun inMemoryDatabase(): NuddleDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NuddleDatabase::class.java)
        .allowMainThreadQueries()
        .build()
