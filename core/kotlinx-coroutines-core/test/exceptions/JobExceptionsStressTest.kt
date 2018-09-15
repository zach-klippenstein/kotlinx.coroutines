/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.experimental.exceptions

import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Test
import java.io.*
import java.util.concurrent.*
import kotlin.test.*

class JobExceptionsStressTest : TestBase() {

    private val executor: ThreadPoolDispatcher by lazy { newFixedThreadPoolContext(5, "JobExceptionsStressTest") }

    @After
    fun tearDown() {
        executor.close()
    }

    @Test
    fun testMultipleChildrenThrows() {
        /*
         * Root parent: launched job
         * Owner: launch 3 children, every of it throws an exception, and then call delay()
         * Result: one of the exceptions with the rest two as suppressed
         */
        repeat(1000 * stressTestMultiplier) {
            val exception = runBlock(executor) {
                val barrier = CyclicBarrier(4)
                val job = launch(NonCancellable) {
                    launch {
                        barrier.await()
                        throw TestException1()
                    }
                    launch {
                        barrier.await()
                        throw TestException2()
                    }
                    launch {
                        barrier.await()
                        throw TestException3()
                    }
                    delay(1000) // to avoid OutOfMemory errors....
                }
                barrier.await()
                job.join()
            }
            val classes = mutableSetOf(
                TestException1::class,
                TestException2::class,
                TestException3::class
            )
            val suppressedExceptions = exception.suppressed().toSet()
            assertTrue(classes.remove(exception::class),
                "Failed to remove ${exception::class} from $suppressedExceptions"
            )
            for (throwable in suppressedExceptions.toSet()) { // defensive copy
                assertTrue(classes.remove(throwable::class),
                    "Failed to remove ${throwable::class} from $suppressedExceptions")
            }
            assertTrue(classes.isEmpty(), "Expected all exception to be present, but following exceptions are missing: $classes")
        }
    }

    private class TestException1 : Exception()
    private class TestException2 : Exception()
    private class TestException3 : Exception()
}
