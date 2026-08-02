package eu.kanade.tachiyomi.extension

enum class InstallStep {
    Idle,
    Pending,
    Downloading,
    Queued,
    Installing,
    Installed,
    Error,
    ;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}
