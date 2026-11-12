package com.vpe.hyundai

import org.junit.Assert.*
import org.junit.Test

class VoiceHistoryMergerTest {
    @Test fun keepsDistinctSpeechFromBothSidesAndAvoidsDuplicateCloudCopy() {
        val voice=listOf("나" to "배고파?", "VPE" to "충전하고 싶어!")
        assertEquals(voice, VoiceHistoryMerger.merge(voice,voice))
    }
    @Test fun keepsUnsyncedSpeechWhenAccountHistoryRefreshes() {
        val cloud=listOf("나" to "안녕", "VPE" to "반가워")
        val local=listOf("나" to "안녕", "VPE" to "반가워",
            "나" to "지금 기분 어때?", "VPE" to "조금 추워!")
        val merged=VoiceHistoryMerger.merge(local,cloud)
        assertEquals(4,merged.size)
        assertEquals(local,merged)
    }
    @Test fun repeatedIdenticalWordsCountAsDistinctTurns() {
        val cloud=listOf("나" to "응")
        val local=listOf("나" to "응", "나" to "응")
        assertEquals(2,VoiceHistoryMerger.merge(local,cloud).size)
    }
}
