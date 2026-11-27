package sample

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class BenchmarkTest {
    @Test
    fun reportsTheProbeCosts() = runTest(timeout = 5.minutes) {
        println("BENCHMARK\n" + runBenchmarks(calls = 500_000, emissions = 250_000))
    }
}
