package com.salesdrive.voice

import com.salesdrive.data.db.Purpose
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * LLM을 쓸 수 없을 때의 규칙 기반 파서.
 * 거래처는 회사 이름 끝말(상사·물산…), 담당자는 "성+직함", 후속 일정은 날짜 표현 주변 말로 찾는다.
 * 완벽하지 않아도 되고, 틀린 값은 정차 중 확인 화면에서 고친다.
 */
object RuleVisitParser : VisitParser {

    override suspend fun parse(utterance: String, destination: String?, today: LocalDate): ParsedVisit? =
        parseNow(utterance, today)

    fun parseNow(utterance: String, today: LocalDate): ParsedVisit? {
        val text = CompanyName.normalize(utterance.trim().replace(Regex("\\s+"), " "))
        if (text.isEmpty()) return null

        val client = findClient(text)
        val contact = findContact(text)
        var rest = text
        client?.let { rest = removeWithJosa(rest, it) }
        contact?.raw?.let { rest = removeWithJosa(rest, it) }

        val segments = rest.split(SEGMENT_SPLIT).map(String::trim).filter(String::isNotEmpty)
        var followUp: String? = null
        var followUpDate: LocalDate? = null
        val summaryParts = mutableListOf<String>()

        for (segment in segments) {
            val date = if (followUpDate == null) DateFinder.find(segment, today) else null
            if (date != null) {
                followUpDate = date.date
                val before = segment.substring(0, date.range.first).trim()
                val after = segment.substring(date.range.last + 1).replace(DATE_TAIL, "").trim()
                if (FOLLOW_UP_WORD.containsMatchIn(after)) {
                    followUp = after
                    if (before.isNotEmpty()) summaryParts += before
                } else {
                    val words = before.split(' ').filter(String::isNotEmpty)
                    val start = words.indices.toList().takeLast(FOLLOW_UP_MAX_WORDS)
                        .firstOrNull { FOLLOW_UP_WORD.containsMatchIn(words[it]) }
                        ?: (words.size - 1).coerceAtLeast(0)
                    followUp = words.drop(start).joinToString(" ").ifEmpty { after.ifEmpty { null } }
                    words.take(start).joinToString(" ").takeIf(String::isNotEmpty)?.let(summaryParts::add)
                }
            } else if (followUp == null && summaryParts.isNotEmpty() && FOLLOW_UP_PROMISE.containsMatchIn(segment)) {
                followUp = segment
            } else {
                summaryParts += segment
            }
        }

        val summary = summaryParts.firstNotNullOfOrNull { clean(it).takeIf(String::isNotEmpty) }
            ?.take(SUMMARY_MAX)?.trim()
        return ParsedVisit(
            client = client,
            contact = contact?.display,
            summary = summary,
            followUp = followUp?.let(::clean)?.takeIf(String::isNotEmpty),
            followUpDate = followUpDate,
            purpose = purposeOf(text, client != null || contact != null),
        )
    }

    // --- 거래처 ---

    private val COMPANY_SUFFIX = Regex(
        "(" + CompanyName.SUFFIXES.joinToString("|") + ")$",
    )
    private val BRANCH = setOf("본사", "지사", "공장", "연구소", "사무소", "지점", "센터", "본점")
    private val JOSA = listOf("에서는", "에서", "에게", "한테", "이랑", "하고", "랑", "에", "의", "와", "과", "은", "는", "이", "가", "을", "를", "도")

    private fun stripJosa(token: String): String {
        val word = token.trimEnd(',', '.', '!', '?')
        if (COMPANY_SUFFIX.containsMatchIn(word) || word in BRANCH) return word
        return JOSA.firstNotNullOfOrNull { j ->
            word.removeSuffix(j).takeIf { it != word && (COMPANY_SUFFIX.containsMatchIn(it) || it in BRANCH) }
        } ?: word
    }

    private fun findClient(text: String): String? {
        Regex("(?:\\(주\\)|㈜|주식회사)\\s*([A-Za-z0-9가-힣]+)").find(text)?.let { return "(주)" + stripJosa(it.groupValues[1]) }
        val tokens = text.split(' ')
        val i = tokens.indexOfFirst { COMPANY_SUFFIX.containsMatchIn(stripJosa(it)) && stripJosa(it).length >= 3 }
        if (i < 0) return null
        val name = stripJosa(tokens[i])
        val branch = tokens.getOrNull(i + 1)?.let(::stripJosa)?.takeIf { it in BRANCH }
        return if (branch != null) "$name $branch" else name
    }

    // --- 담당자 ---

    private class Contact(val raw: String, val display: String)

    private val CONTACT = Regex(
        "(?<![가-힣])([가-힣]{1,3}?)\\s?(본부장|센터장|팀장|부장|차장|과장|대리|주임|사원|실장|이사|상무|전무|사장|대표|매니저|소장|원장|국장)(님)?(?![가-힣&&[^이가은는을를께에과와랑하도의]])",
    )

    private fun findContact(text: String): Contact? {
        val m = CONTACT.find(text) ?: return null
        val name = m.groupValues[1]
        if (name in NOT_A_NAME) return null
        val display = if (name.length == 1) name + m.groupValues[2] else "$name ${m.groupValues[2]}"
        return Contact(m.value, display)
    }

    private val NOT_A_NAME = setOf("우리", "저희", "거기", "그쪽", "담당", "새로", "전", "현")

    private fun removeWithJosa(text: String, word: String): String =
        text.replace(Regex(Regex.escape(word) + "(" + JOSA.joinToString("|") + ")?(?![가-힣])"), " ")
            .replace(Regex("\\s+"), " ").trim()

    // --- 요약·후속 ---

    private val SEGMENT_SPLIT = Regex("[,.!?]|\\s그리고\\s|\\s그리고$")
    private val DATE_TAIL = Regex("^(까지는|까지|에는|에|중으로|중에|쯤)")
    private val FOLLOW_UP_WORD = Regex("견적|회신|연락|전화|발송|보내|제출|방문|미팅|회의|전달|검토|계약|납품|샘플|자료|답변|확인|재방문|통화")
    private val FOLLOW_UP_PROMISE = Regex("기로|예정|드려야|해야|하자고")
    private const val FOLLOW_UP_MAX_WORDS = 3
    private const val SUMMARY_MAX = 20

    private val LEADING = Regex("^(만나서|만나고|와서|가서|오늘은|오늘|방금|그래서|일단)\\s+")
    private val TRAILING = Regex(
        "\\s*(하고\\s?왔어요|하고\\s?왔어|하고\\s?옴|했습니다|했어요|했어|했음|했다|함|하였음|진행|완료|있었어요|있었음|입니다|이에요|예요|요)$",
    )

    private fun clean(s: String): String {
        var t = s.trim()
        t = t.replace(Regex("하기로.*$"), "").replace(Regex("기로.*$"), "기")
            .replace(Regex("\\s*(할|하실|드릴)?\\s*예정.*$"), "")
        repeat(2) { t = t.replace(LEADING, "").replace(TRAILING, "").trim() }
        return t
    }

    // --- 목적 ---

    private val COMMUTE = Regex("출근|퇴근|출퇴근|집으로|귀가")
    private val PERSONAL = Regex("개인|사적|볼일|장보|쇼핑|가족")
    private val BUSINESS = Regex("미팅|회의|납품|견적|계약|영업|상담|시연|방문|거래처|고객|발주|수주|교육|설치|점검|출장")

    private fun purposeOf(text: String, hasCounterpart: Boolean): Purpose? = when {
        COMMUTE.containsMatchIn(text) -> Purpose.COMMUTE
        PERSONAL.containsMatchIn(text) -> Purpose.PERSONAL
        hasCounterpart || BUSINESS.containsMatchIn(text) -> Purpose.BUSINESS
        else -> null
    }
}

/** "다음 주 화요일", "11월 20일", "내일" 같은 날짜 표현을 찾아 실제 날짜로 바꾼다 */
internal object DateFinder {
    data class Match(val date: LocalDate, val range: IntRange)

    private val WEEKDAY = mapOf('월' to 1, '화' to 2, '수' to 3, '목' to 4, '금' to 5, '토' to 6, '일' to 7)

    private val MONTH_DAY = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    private val WEEK_DAY = Regex("(이번|다음|담|다다음)\\s*주\\s*([월화수목금토일])(?:요일)?")
    private val IN_DAYS = Regex("(\\d{1,2})\\s*일\\s*(?:후|뒤)")
    private val RELATIVE = Regex("(?<![가-힣])(오늘|내일|모레|글피)")
    private val BARE_WEEKDAY = Regex("(?<![가-힣])([월화수목금토일])요일")
    private val BARE_DAY = Regex("(?<![\\d월])(\\d{1,2})\\s*일(?=\\s|까지|에|$)")

    /** [includeToday]가 false면 "오늘"은 날짜로 보지 않는다. 방문 내용의 "오늘 미팅 했어요"는 후속 일정이 아니다 */
    fun find(text: String, today: LocalDate, includeToday: Boolean = false): Match? {
        MONTH_DAY.find(text)?.let { m ->
            val date = runCatching { LocalDate.of(today.year, m.int(1), m.int(2)) }.getOrNull() ?: return@let
            return Match(if (date.isBefore(today)) date.plusYears(1) else date, m.range)
        }
        WEEK_DAY.find(text)?.let { m ->
            val weeks = when (m.groupValues[1]) { "이번" -> 0L; "다다음" -> 2L; else -> 1L }
            val dow = DayOfWeek.of(WEEKDAY.getValue(m.groupValues[2][0]))
            return Match(today.with(dow).plusWeeks(weeks), m.range)
        }
        IN_DAYS.find(text)?.let { m -> return Match(today.plusDays(m.int(1).toLong()), m.range) }
        RELATIVE.find(text)?.let { m ->
            val days = listOf("오늘", "내일", "모레", "글피").indexOf(m.groupValues[1]).toLong()
            if (days > 0 || includeToday) return Match(today.plusDays(days), m.range)
        }
        BARE_WEEKDAY.find(text)?.let { m ->
            val dow = DayOfWeek.of(WEEKDAY.getValue(m.groupValues[1][0]))
            return Match(today.with(TemporalAdjusters.next(dow)), m.range)
        }
        BARE_DAY.find(text)?.let { m ->
            val date = runCatching { today.withDayOfMonth(m.int(1)) }.getOrNull() ?: return@let
            return Match(if (date.isBefore(today)) date.plusMonths(1) else date, m.range)
        }
        return null
    }

    private fun MatchResult.int(group: Int) = groupValues[group].toInt()
}
