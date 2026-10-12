package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.PhotosApi
import app.nuddle.data.photo.ApiPhotoLoader
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.photo.PhotoSender
import app.nuddle.data.photo.PhotoUploader
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.SendOutcome
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.drtError
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A taken photo going out (docs/ux/screens.md 4d, 6e; docs/ux/platform.md: upload through WorkManager with retries). */
class PhotoUploadTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var files: PhotoFiles
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    @Before
    fun setUp() {
        server.start()
        files = PhotoFiles(temp.newFolder("photos"))
    }

    @After
    fun tearDown() = server.close()

    private fun uploader(): PhotoUploader {
        val retrofit = server.retrofit()
        return PhotoUploader(
            retrofit.create(PhotosApi::class.java),
            retrofit.create(CommandsApi::class.java),
            OkHttpClient(),
            files,
            testJson,
        )
    }

    private fun uploadUrl() = json(200, """{"path":"tasks/${Cast.TASK_ID}/1.jpg","url":"${server.url("/storage/upload")}"}""")

    @Test
    fun `upload URL, PUT to Storage, then photo_sent with the key, and the kept file goes`() =
        runTest {
            files.save(Cast.TASK_ID, jpeg)
            server.enqueue(uploadUrl())
            server.enqueue(MockResponse.Builder().code(200).build())
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"AWAITING_APPROVAL","planVersion":2}"""))

            assertEquals(SendOutcome.SENT, uploader().upload(Cast.TASK_ID, "key-1"))

            assertEquals("/api/v1/photos/upload-url", server.takeRequest().url.encodedPath)
            val put = server.takeRequest()
            assertEquals("PUT" to "/storage/upload", put.method to put.url.encodedPath)
            assertEquals(jpeg.toList(), put.body!!.toByteArray().toList())
            assertEquals(null, put.headers["Authorization"], "no token of ours goes to Storage")
            val sent = server.takeRequest()
            assertEquals("/api/v1/commands/photo_sent", sent.url.encodedPath)
            val body = sent.body!!.utf8()
            assertTrue(""""idempotencyKey":"key-1"""" in body && """"photoPath":"tasks/${Cast.TASK_ID}/1.jpg"""" in body, body)
            assertFalse(files.of(Cast.TASK_ID).exists())
        }

    @Test
    fun `a failed PUT is retried and the photo stays on the phone`() =
        runTest {
            files.save(Cast.TASK_ID, jpeg)
            server.enqueue(uploadUrl())
            server.enqueue(MockResponse.Builder().code(503).build())

            assertEquals(SendOutcome.RETRY, uploader().upload(Cast.TASK_ID, "key-1"))
            assertTrue(files.of(Cast.TASK_ID).exists())
        }

    @Test
    fun `a task that closed meanwhile is given up and its photo deleted`() =
        runTest {
            files.save(Cast.TASK_ID, jpeg)
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))

            assertEquals(SendOutcome.DROP, uploader().upload(Cast.TASK_ID, "key-1"))
            assertFalse(files.of(Cast.TASK_ID).exists())
        }

    @Test
    fun `Gönder keeps the photo, stops the pokes at once and schedules the upload`() =
        runTest {
            val closed = mutableListOf<String>()
            val scheduled = mutableListOf<String>()
            val closer =
                object : TaskCloser {
                    override suspend fun done(
                        taskId: String,
                        cameraUnavailable: Boolean,
                    ) = Unit

                    override suspend fun photoTaken(taskId: String) {
                        closed += taskId
                    }

                    override suspend fun cancel(
                        taskId: String,
                        note: String?,
                    ) = Unit
                }
            PhotoSender(files, closer, { taskId, _ -> scheduled += taskId }, IdGenerator(FakeClock())).send(Cast.TASK_ID, jpeg)

            assertEquals(jpeg.toList(), files.of(Cast.TASK_ID).readBytes().toList())
            assertEquals(listOf(Cast.TASK_ID), closed)
            assertEquals(listOf(Cast.TASK_ID), scheduled)
        }

    private fun loader() = ApiPhotoLoader(server.retrofit().create(PhotosApi::class.java), OkHttpClient(), files, testJson)

    @Test
    fun `a photo still on the phone is shown from there, without a call`() =
        runTest {
            files.save(Cast.TASK_ID, jpeg)
            assertEquals(jpeg.toList(), loader().load(Cast.TASK_ID)?.toList())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `an uploaded photo comes through its short-lived view URL, and none when there is nothing to show`() =
        runTest {
            server.enqueue(json(200, """{"url":"${server.url("/storage/view")}","sentAt":"2026-10-12T08:24:00+03:00"}"""))
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(okio.Buffer().write(jpeg))
                    .build(),
            )
            assertEquals(jpeg.toList(), loader().load(Cast.TASK_ID)?.toList())
            assertEquals("/api/v1/photos/view-url", server.takeRequest().url.encodedPath)

            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            assertEquals(null, loader().load(Cast.TASK_ID))
        }
}
