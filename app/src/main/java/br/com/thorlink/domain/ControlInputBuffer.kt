package br.com.thorlink.domain

/**
 * Axes use the newest position, but button press/release edges must survive a slow ACK or gesture.
 * Used at BOTH ends: a quick tap must not disappear between two 20 ms samples or 32 ms gestures.
 * The queue is bounded and expires after 250 ms, so a lag cannot replay old actions later.
 */
class ControlInputBuffer(private val capacity: Int = 32, private val maxAgeMs: Long = 250) {
    private data class Edge(val buttons: Int, val at: Long)
    private val edges = ArrayDeque<Edge>()
    private var latest = ControlState()

    @Synchronized fun offer(state: ControlState, now: Long): Boolean {
        if (state.buttons != latest.buttons) {
            if (edges.size >= capacity) { reset(); return false }
            edges.addLast(Edge(state.buttons, now))
        }
        latest = state
        return true
    }

    @Synchronized fun next(now: Long): ControlState {
        if (edges.firstOrNull()?.let { now - it.at > maxAgeMs } == true) {
            reset()
            return ControlState()
        }
        return latest.copy(buttons = edges.removeFirstOrNull()?.buttons ?: latest.buttons)
    }

    @Synchronized fun reset() { edges.clear(); latest = ControlState() }
}
