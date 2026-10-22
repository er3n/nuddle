package app.nuddle.data

import android.net.Uri
import app.nuddle.api.CommandsApi
import app.nuddle.api.PhotosApi
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.example.ExampleDrafts
import app.nuddle.data.example.ExampleFiles
import app.nuddle.data.example.ExampleSync
import app.nuddle.data.example.ExampleUploader
import app.nuddle.data.example.FileExamplePhotos
import app.nuddle.data.photo.GalleryImages
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.SendOutcome
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.aTask
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object NoGallery : GalleryImages {
    override suspend fun read(uri: Uri): ByteArray? = null
}

/** The sender's example photo (NUD-138, docs/domain.md, Example photo): kept, uploaded, fetched and dropped. */
class ExamplePhotoTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var files: ExampleFiles
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    private val path = "tasks/${Cast.TASK_ID}/1.jpg"

    @Before
    fun setUp() {
        server.start()
        files = ExampleFiles(temp.newFolder("examples"))
    }

    @After
    fun tearDown() = server.close()

    private fun uploader(): ExampleUploader {
        val retrofit = server.retrofit()
        return ExampleUploader(
            retrofit.create(PhotosApi::class.java),
            retrofit.create(CommandsApi::class.java),
            OkHttpClient(),
            files,
            testJson,
        )
    }

    private fun sync(): ExampleSync = ExampleSync(server.retrofit().create(PhotosApi::class.java), OkHttpClient(), files, testJson)

    @Test
    fun `attach keeps the picture with a key and schedules its upload`() =
        runTest {
            val scheduled = mutableListOf<String>()
            val photos =
                FileExamplePhotos(NoGallery, files, ExampleDrafts(temp.newFolder("drafts")), { scheduled += it }, IdGenerator(FakeClock()))
            photos.attach(Cast.TASK_ID, jpeg)
            assertTrue(files.pendingKey(Cast.TASK_ID)?.isNotEmpty() == true)
            assertEquals(jpeg.toList(), files.current(Cast.TASK_ID)?.toList())
            assertEquals(listOf(Cast.TASK_ID), scheduled)
        }

    @Test
    fun `a picture of a text written offline waits under its key and is taken out once`() =
        runTest {
            val photos = FileExamplePhotos(NoGallery, files, ExampleDrafts(temp.newFolder("drafts")), {}, IdGenerator(FakeClock()))
            photos.keepWithQueued("write-1", jpeg)
            assertEquals(jpeg.toList(), photos.takeQueued("write-1")?.toList())
            assertNull(photos.takeQueued("write-1"))
        }

    @Test
    fun `upload URL, PUT to Storage, then set_example_photo with the key, and the copy stays for its path`() =
        runTest {
            files.savePending(Cast.TASK_ID, jpeg, "key-1")
            server.enqueue(json(200, """{"path":"$path","url":"${server.url("/storage/upload")}"}"""))
            server.enqueue(MockResponse.Builder().code(200).build())
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":1}"""))

            assertEquals(SendOutcome.SENT, uploader().upload(Cast.TASK_ID))

            assertEquals("/api/v1/photos/example-upload-url", server.takeRequest().url.encodedPath)
            val put = server.takeRequest()
            assertEquals("PUT" to "/storage/upload", put.method to put.url.encodedPath)
            assertEquals(null, put.headers["Authorization"], "no token of ours goes to Storage")
            val set = server.takeRequest()
            assertEquals("/api/v1/commands/set_example_photo", set.url.encodedPath)
            val body = set.body!!.utf8()
            assertTrue(""""idempotencyKey":"key-1"""" in body && """"photoPath":"$path"""" in body, body)
            assertNull(files.pendingKey(Cast.TASK_ID))
            assertEquals(path, files.pathOf(Cast.TASK_ID))
        }

    @Test
    fun `a failed PUT is retried and the picture stays waiting`() =
        runTest {
            files.savePending(Cast.TASK_ID, jpeg, "key-1")
            server.enqueue(json(200, """{"path":"$path","url":"${server.url("/storage/upload")}"}"""))
            server.enqueue(MockResponse.Builder().code(503).build())

            assertEquals(SendOutcome.RETRY, uploader().upload(Cast.TASK_ID))
            assertEquals("key-1", files.pendingKey(Cast.TASK_ID))
        }

    @Test
    fun `a task undone meanwhile drops the upload and the picture`() =
        runTest {
            files.savePending(Cast.TASK_ID, jpeg, "key-1")
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))

            assertEquals(SendOutcome.DROP, uploader().upload(Cast.TASK_ID))
            assertNull(files.current(Cast.TASK_ID))
        }

    @Test
    fun `after a sync the recipient fetches a new picture, keeps it for its path, and does not fetch it again`() =
        runTest {
            server.enqueue(json(200, """{"url":"${server.url("/storage/view")}","examplePhotoPath":"$path"}"""))
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(okio.Buffer().write(jpeg))
                    .build(),
            )
            val task = aTask().copy(examplePhotoPath = path)

            sync().refresh(listOf(task))
            assertEquals("/api/v1/photos/example-view-url", server.takeRequest().url.encodedPath)
            assertEquals("/storage/view", server.takeRequest().url.encodedPath)
            assertEquals(jpeg.toList(), files.current(Cast.TASK_ID)?.toList())
            assertEquals(path, files.pathOf(Cast.TASK_ID))

            sync().refresh(listOf(task))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a task that closed or lost its picture drops the copy, a waiting upload is left alone`() =
        runTest {
            files.save(Cast.TASK_ID, path, jpeg)
            sync().refresh(listOf(aTask().copy(examplePhotoPath = path, status = TaskStatus.DONE.value)))
            assertNull(files.current(Cast.TASK_ID))

            files.save(Cast.TASK_ID, path, jpeg)
            sync().refresh(listOf(aTask().copy(examplePhotoPath = null)))
            assertNull(files.current(Cast.TASK_ID))

            files.savePending(Cast.TASK_ID, jpeg, "key-1")
            sync().refresh(listOf(aTask().copy(examplePhotoPath = null)))
            assertEquals("key-1", files.pendingKey(Cast.TASK_ID))
            assertEquals(0, server.requestCount)
        }
}
