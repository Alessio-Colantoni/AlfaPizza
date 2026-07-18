package com.alfaproject.alfapizza.view_model.rider

import com.alfaproject.alfapizza.model.Constraint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ConstraintMutationQueue {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}

internal fun replaceFutureConstraint(
    constraints: List<Constraint>,
    replacement: Constraint
): List<Constraint> = constraints
    .filterNot { it.isNext && it.day == replacement.day }
    .plus(replacement)

internal fun removeFutureConstraint(
    constraints: List<Constraint>,
    day: Int
): List<Constraint> = constraints.filterNot { it.isNext && it.day == day }

internal fun futureConstraints(constraints: List<Constraint>): List<Constraint> =
    constraints.filter { it.isNext }
