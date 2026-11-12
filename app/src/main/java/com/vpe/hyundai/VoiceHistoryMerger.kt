package com.vpe.hyundai

/** Preserve unsynced/local voice turns when fetching account history from Render. */
object VoiceHistoryMerger {
    fun merge(
        local: List<Pair<String, String>>,
        remote: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        if (remote.isEmpty()) return local.takeLast(60)
        val available=remote.groupingBy { it }.eachCount().toMutableMap()
        val merged=remote.toMutableList()
        for (line in local) {
            if (line.second=="안녕! 이 차량만의 대화를 시작해 보자.") continue
            val n=available[line] ?: 0
            if (n>0) available[line]=n-1
            else merged.add(line)
        }
        return merged.takeLast(60)
    }
}
