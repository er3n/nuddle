package app.nuddle.data

/** Work that follows a completed sign-in and every launch while signed in: register the device, then sync. */
fun interface SessionStarter {
    suspend fun start()
}
