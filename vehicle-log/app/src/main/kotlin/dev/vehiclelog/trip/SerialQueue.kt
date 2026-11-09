package dev.vehiclelog.trip

/**
 * 비동기 작업을 넣은 순서대로 한 번에 하나씩 돌린다. 작업은 끝나면 done() 을 한 번 부른다.
 * 스레드 안전하지 않다 — 한 스레드(메인)에서만 쓴다.
 */
class SerialQueue<T>(private val work: (item: T, done: () -> Unit) -> Unit) {
    private val pending = ArrayDeque<T>()
    private var running = false

    fun add(item: T) {
        pending.addLast(item)
        next()
    }

    private fun next() {
        if (running) return
        val item = pending.removeFirstOrNull() ?: return
        running = true
        var finished = false
        work(item) {
            // 같은 작업이 done 을 두 번 불러도 다음 작업을 두 번 시작하지 않는다
            if (!finished) {
                finished = true
                running = false
                next()
            }
        }
    }
}
