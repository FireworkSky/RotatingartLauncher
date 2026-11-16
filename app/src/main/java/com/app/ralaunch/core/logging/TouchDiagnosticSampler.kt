package com.app.ralaunch.core.logging

/** Counts every event, while limiting detail to 10 samples/second and one idle sample/5 seconds. */
internal class TouchDiagnosticSampler {
    data class Sample(val events: Long, val edges: Long, val omitted: Long)
    private data class Stage(
        var events: Long = 0,
        var edges: Long = 0,
        var omitted: Long = 0,
        var lastDetail: Long? = null
    )
    private val stages = linkedMapOf<String, Stage>()

    fun observe(stage: String, now: Long, edge: Boolean): Sample? {
        val state = stages.getOrPut(stage) { Stage() }
        state.events++
        if (edge) state.edges++
        val elapsed = state.lastDetail?.let { now - it }
        if (elapsed != null && elapsed < if (edge) 100L else 5_000L) {
            state.omitted++
            return null
        }
        state.lastDetail = now
        return Sample(state.events, state.edges, state.omitted).also { state.omitted = 0 }
    }

    fun summary(): String = stages.entries.joinToString { (name, stage) ->
        "$name=${stage.events}/${stage.edges}"
    }
}
