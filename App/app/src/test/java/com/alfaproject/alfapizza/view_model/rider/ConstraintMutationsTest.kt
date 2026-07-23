package com.alfaproject.alfapizza.view_model.rider

import com.alfaproject.alfapizza.model.Constraint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstraintMutationsTest {
    @Test
    fun `replace keeps current week and unrelated future constraints`() {
        val current = constraint(day = 1, priority = 1, isNext = false)
        val futureToReplace = constraint(day = 2, priority = 2, isNext = true)
        val otherFuture = constraint(day = 3, priority = 1, isNext = true)
        val replacement = constraint(day = 2, priority = 1, isNext = true)

        val result = replaceFutureConstraint(
            listOf(current, futureToReplace, otherFuture),
            replacement
        )

        assertEquals(listOf(current, otherFuture, replacement), result)
        assertEquals(listOf(otherFuture, replacement), futureConstraints(result))
    }

    @Test
    fun `remove only affects the matching future day`() {
        val current = constraint(day = 2, priority = 1, isNext = false)
        val future = constraint(day = 2, priority = 2, isNext = true)
        val otherFuture = constraint(day = 4, priority = 2, isNext = true)

        assertEquals(
            listOf(current, otherFuture),
            removeFutureConstraint(listOf(current, future, otherFuture), day = 2)
        )
    }

    @Test
    fun `mutation queue does not start a second snapshot before the first completes`() = runBlocking {
        val queue = ConstraintMutationQueue()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = launch {
            queue.run {
                events += "first-start"
                releaseFirst.await()
                events += "first-end"
            }
        }
        yield()
        val second = launch {
            queue.run {
                events += "second-start"
                events += "second-end"
            }
        }
        yield()

        assertEquals(listOf("first-start"), events)
        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(
            listOf("first-start", "first-end", "second-start", "second-end"),
            events
        )
    }

    private fun constraint(day: Int, priority: Int, isNext: Boolean) = Constraint(
        riderCode = 7,
        priority = priority,
        day = day,
        permanent = false,
        isNext = isNext
    )
}
