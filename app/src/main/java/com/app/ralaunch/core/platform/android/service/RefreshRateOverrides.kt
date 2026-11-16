package com.app.ralaunch.core.platform.android.service

import kotlinx.serialization.Serializable

@Serializable
internal data class RefreshRateOverride(
    val namespace: String,
    val key: String,
    val original: String?,
    val applied: String
)

/** Write-ahead journal: never change a system value unless its original is saved first. */
internal class RefreshRateOverrides(
    val entries: MutableList<RefreshRateOverride>,
    private val read: (String, String) -> String?,
    private val write: (String, String, String?) -> Boolean,
    private val save: () -> Unit
) {
    fun apply(namespace: String, key: String, target: String) {
        val current = read(namespace, key)
        if (sameValue(current, target)) return
        entries.removeAll { it.namespace == namespace && it.key == key }
        entries.add(RefreshRateOverride(namespace, key, current, target))
        save()
        check(write(namespace, key, target)) { "System rejected $namespace.$key" }
        check(sameValue(read(namespace, key), target)) { "System did not retain $namespace.$key" }
    }

    fun restore(): List<String> {
        val failures = mutableListOf<String>()
        entries.toList().forEach { entry ->
            try {
                // A later change made by the user/system takes precedence over our backup.
                if (sameValue(read(entry.namespace, entry.key), entry.applied)) {
                    check(write(entry.namespace, entry.key, entry.original)) { "Restore rejected" }
                    check(sameValue(read(entry.namespace, entry.key), entry.original)) { "Restore not retained" }
                }
                entries.remove(entry)
                save()
            } catch (e: Exception) {
                failures.add("${entry.namespace}.${entry.key}: ${e.message}")
            }
        }
        return failures
    }

    private fun sameValue(first: String?, second: String?): Boolean =
        first == second || (first?.toFloatOrNull()?.let { it == second?.toFloatOrNull() } == true)
}
