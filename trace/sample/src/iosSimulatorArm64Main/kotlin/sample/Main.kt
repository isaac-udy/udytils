package sample

import kotlinx.coroutines.runBlocking

fun main() {
    println(runBlocking { runBenchmarks() })
}
