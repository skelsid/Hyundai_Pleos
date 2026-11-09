package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class SerialQueueTest {

    @Test
    fun test_add_secondWhileFirstRunning_waitsAndKeepsOrder() {
        val started = mutableListOf<String>()
        val finishers = mutableMapOf<String, () -> Unit>()
        val queue = SerialQueue<String> { item, done ->
            started += item
            finishers[item] = done
        }
        queue.add("start")
        queue.add("end")
        // 시작 조회가 끝나기 전에는 종료 조회를 시작하지 않는다
        assertEquals(listOf("start"), started)
        finishers.getValue("start")()
        assertEquals(listOf("start", "end"), started)
    }

    @Test
    fun test_add_synchronousWork_runsAllInOrder() {
        val done = mutableListOf<Int>()
        val queue = SerialQueue<Int> { item, finish ->
            done += item
            finish()
        }
        (1..3).forEach(queue::add)
        assertEquals(listOf(1, 2, 3), done)
    }

    @Test
    fun test_done_calledTwice_doesNotStartNextTwice() {
        val started = mutableListOf<Int>()
        var first: (() -> Unit)? = null
        val queue = SerialQueue<Int> { item, finish ->
            started += item
            if (item == 1) first = finish
        }
        queue.add(1)
        queue.add(2)
        queue.add(3)
        first!!()
        first!!()
        assertEquals(listOf(1, 2), started)
    }
}
