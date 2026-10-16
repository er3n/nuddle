package app.nuddle.testing

import app.nuddle.domain.Diagnostics

/** Keeps what the app would leave in Crashlytics (ADR 0023). */
class RecordingDiagnostics : Diagnostics {
    val lines = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val keys = mutableMapOf<String, String>()
    var personId: String? = null

    override fun log(line: String) {
        lines += line
    }

    override fun record(error: Throwable) {
        errors += error
    }

    override fun setKey(
        key: String,
        value: String,
    ) {
        keys[key] = value
    }

    override fun setPerson(personId: String?) {
        this.personId = personId
    }
}
