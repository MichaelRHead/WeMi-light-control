package org.suppressingfire.wemi

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.junit.Assert.assertTrue

/**
 * Shared hermetic Jetpack Compose + Robolectric + loopback `FakeUpnpServer` test helpers.
 */

/**
 * Pumps the Compose / Robolectric main looper while allowing real wall-clock time (`20ms` slices)
 * for background `Dispatchers.IO` coroutines and loopback TCP/UDP socket threads (`FakeUpnpServer`)
 * to complete round-trips and post continuations back to `Dispatchers.Main`.
 */
internal fun ComposeTestRule.awaitLoopbackIo(
    timeoutMs: Long = 5_000L,
    condition: () -> Boolean
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        if (condition()) {
            waitForIdle()
            return
        }
        Thread.sleep(20)
    }
    waitForIdle()
    assertTrue("Timed out waiting ${timeoutMs}ms for loopback IO condition", condition())
}

/**
 * Matches any semantics node whose `TestTag` begins with [prefix] (for example,
 * `"brightness_slider_"` when device database IDs are assigned dynamically during adoption).
 */
internal fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("TestTag starts with '$prefix'") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
