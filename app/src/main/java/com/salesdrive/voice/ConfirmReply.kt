package com.salesdrive.voice

/** "방문 기록할까요?"에 대한 운전자의 답 */
enum class ConfirmReply {
    YES,
    NO,

    /** 예/아니오 대신 방문 내용을 바로 말함 ("A상사 김부장 미팅") */
    CONTENT;

    companion object {
        /**
         * 부정 표현은 앞부분으로, 경유 정차를 뜻하는 말(휴게소·주유 등)과 긍정 표현은 짧은 답 안에서만 찾는다.
         * "어제 A상사 미팅"처럼 긍정 감탄사로 시작하는 방문 내용을 YES로 잘못 보지 않도록, 감탄사는 답 전체가 그 말일 때만 인정한다.
         * "어", "음"처럼 군말·소음으로 인식되기 쉬운 소리는 긍정으로 보지 않는다.
         * 그 밖의 답은 [isClearContent]를 만족할 때만 방문 내용으로 본다. 판단할 수 없으면(군말, 잘못 들은 짧은 말) null.
         */
        fun classify(heard: String): ConfirmReply? {
            val t = normalize(heard)
            if (t.isEmpty() || t.all { it in FILLER_CHARS }) return null
            if (NO_PREFIX.any(t::startsWith)) return NO
            if (t.length <= SHORT) {
                if (WAYPOINT_WORDS.any(t::contains)) return NO
                if (isYesWord(t) || YES_COMMANDS.any(t::contains)) return YES
            }
            return if (isClearContent(heard, t)) CONTENT else null
        }

        /**
         * 예/아니오를 묻는 자리에서는 잘못 들은 짧은 말("기록해" → "이렇게")이 방문 내용으로 저장되면 안 된다.
         * 두 단어 이상이거나 충분히 긴 답만 방문 내용으로 인정한다.
         */
        private fun isClearContent(heard: String, t: String): Boolean =
            t.length >= 2 && (t.length >= CONTENT_MIN_LENGTH || heard.trim().split(WHITESPACE).size >= 2)

        /** 방문 내용으로 쓸 수 없는 답: 군말("어", "음음")이나 맞장구("네")뿐이거나 한 글자 */
        fun isFiller(heard: String): Boolean {
            val t = normalize(heard)
            return t.length < 2 || t.all { it in FILLER_CHARS } || isYesWord(t)
        }

        private fun normalize(heard: String) = heard.replace(PUNCTUATION, "").lowercase()

        /** "네", "네네", "네요", "그래요"처럼 감탄사만 있는 답 */
        private fun isYesWord(t: String): Boolean {
            val core = t.removeSuffix("요")
            return YES_WORDS.any { w -> core.isNotEmpty() && core.chunked(w.length).all { it == w } }
        }

        private val PUNCTUATION = Regex("[\\s.,!?~…]")
        private val WHITESPACE = Regex("\\s+")
        private const val SHORT = 10
        private const val CONTENT_MIN_LENGTH = 5
        private const val FILLER_CHARS = "어음아으에흠엄"

        private val NO_PREFIX = listOf(
            "아니", "아뇨", "아냐", "됐어", "됐습니다", "됐다", "괜찮아", "괜찮습니다",
            "안해", "안할래", "필요없", "건너뛰", "취소", "기록안", "기록하지마",
        )
        private val WAYPOINT_WORDS = listOf("휴게소", "쉼터", "쉬는", "쉬어", "쉬었", "잠깐", "경유", "주유", "충전", "화장실")
        private val YES_WORDS = listOf("응", "네", "예", "넵", "웅", "그래", "좋아", "맞아", "오케이", "ok")
        private val YES_COMMANDS = listOf("기록해", "기록할게", "기록부탁", "해줘", "해주세요", "부탁해", "부탁합니다")
    }
}
