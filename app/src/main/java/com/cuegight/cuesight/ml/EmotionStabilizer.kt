package com.cuegight.cuesight.ml

class EmotionStabilizer(
    private val windowSize: Int = 5,
    private val minConsensus: Int = 3,
    private val staleAfterMs: Long = 3_000L
) {
    private val history = mutableMapOf<Int, ArrayDeque<String>>()
    private val lastUpdated = mutableMapOf<Int, Long>()

    fun update(trackingId: Int?, emotion: String, now: Long = System.currentTimeMillis()): String? {
        val id = trackingId ?: DEFAULT_TRACKING_ID
        val deque = history.getOrPut(id) { ArrayDeque() }
        if (deque.size >= windowSize) {
            deque.removeFirst()
        }
        deque.addLast(emotion)
        lastUpdated[id] = now
        prune(now)
        val counts = deque.groupingBy { it }.eachCount()
        val top = counts.maxByOrNull { it.value } ?: return null
        return if (top.value >= minConsensus) top.key else null
    }

    private fun prune(now: Long) {
        val iterator = lastUpdated.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > staleAfterMs) {
                history.remove(entry.key)
                iterator.remove()
            }
        }
    }

    companion object {
        private const val DEFAULT_TRACKING_ID = -1
    }
}
