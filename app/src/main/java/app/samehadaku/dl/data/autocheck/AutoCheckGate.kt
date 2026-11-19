package app.samehadaku.dl.data.autocheck

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide guard so the periodic worker and the one-off "check now" worker
 * never hit the site at the same time.
 *
 * WorkManager treats the periodic and one-time unique works as separate chains,
 * so the operating system is free to run both. When that happened, the log showed
 * two simultaneous "Detect episodes" waves and the followers were checked twice.
 * The second worker now exits immediately; the run that is already in progress
 * updates the timestamp and posts the notifications.
 */
internal object AutoCheckGate {

    private val running = AtomicBoolean(false)

    fun tryEnter(): Boolean = running.compareAndSet(false, true)

    fun exit() {
        running.set(false)
    }
}
