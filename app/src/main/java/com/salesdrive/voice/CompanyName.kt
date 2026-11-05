package com.salesdrive.voice

/**
 * 음성 인식은 영문 이니셜을 한글 소리로 적는다("A기업" → "에이 기업", "C물산" → "시물산").
 * 회사 이름 끝말 바로 앞에 알파벳 이름이 올 때만 영문으로 되돌린다.
 *
 * 흔한 한국어와 겹치는 소리는 바꾸지 않는다: 이("이 기업"), 오(성씨), 아이, 유("유전자").
 */
internal object CompanyName {
    /** 회사 이름 끝말. 긴 말이 먼저 맞도록 정렬해서 쓴다 */
    val SUFFIXES = listOf(
        "상사", "물산", "전자", "산업", "기업", "건설", "제약", "화학", "통상", "무역", "은행", "증권", "보험", "병원",
        "유통", "식품", "테크", "솔루션", "시스템", "시스템즈", "그룹", "코리아", "컴퍼니", "정밀", "공업", "모터스",
        "약품", "에너지", "엔지니어링", "홀딩스", "주식회사",
    )

    private val LETTERS = mapOf(
        "에이치" to "H", "에이" to "A", "비" to "B", "시" to "C", "씨" to "C", "디" to "D", "에프" to "F", "지" to "G",
        "제이" to "J", "케이" to "K", "엘" to "L", "엠" to "M", "엔" to "N", "피" to "P", "큐" to "Q", "알" to "R",
        "에스" to "S", "티" to "T", "브이" to "V", "더블유" to "W", "엑스" to "X", "와이" to "Y", "제트" to "Z",
    )

    private val suffixPattern = SUFFIXES.sortedByDescending(String::length).joinToString("|")
    private val letterPattern = LETTERS.keys.sortedByDescending(String::length).joinToString("|")

    /** 끝말 뒤에는 조사만 붙을 수 있다. "유통기한"처럼 더 긴 낱말의 일부는 회사 이름이 아니다 */
    private const val AFTER_SUFFIX = "(?=(?:에서는|에서|에게|한테|이랑|하고|랑|에|의|와|과|은|는|이|가|을|를|도)?(?![가-힣]))"

    private val SPOKEN = Regex("(?<![A-Za-z0-9가-힣])((?:$letterPattern)+)\\s?($suffixPattern)$AFTER_SUFFIX")
    private val LATIN = Regex("(?<![A-Za-z0-9가-힣])([A-Za-z]{1,4})\\s?($suffixPattern)$AFTER_SUFFIX")
    private val ONE_LETTER = Regex(letterPattern)

    /** 문장 안의 "에이 기업", "시물산", "b전자"를 "A기업", "C물산", "B전자"로 바꾼다 */
    fun normalize(text: String): String {
        val spelled = SPOKEN.replace(text) { m ->
            ONE_LETTER.findAll(m.groupValues[1]).joinToString("") { LETTERS.getValue(it.value) } + m.groupValues[2]
        }
        return LATIN.replace(spelled) { m -> m.groupValues[1].uppercase() + m.groupValues[2] }
    }
}
