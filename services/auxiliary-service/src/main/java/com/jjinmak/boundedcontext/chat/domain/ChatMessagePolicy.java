package com.jjinmak.boundedcontext.chat.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 외부 거래 유도 메시지 차단 규칙. Spring 의존 없는 순수 규칙.
 * 전송 시점에 동기로 돌아야 하므로 정규식·키워드만 쓴다 (LLM·임베딩은 지연·비용·설명 불가로 제외).
 *
 * 검사는 공백·구두점·기호를 뺀 정규화본으로 한다 ("카 톡", "1364 3562", "010-3675-3148" 모두 같은 모양이 된다).
 * 운송장 번호도 8자리가 넘어 막히지만, 운송장은 채팅이 아니라 주문의 송장 등록으로 넣는다.
 */
public final class ChatMessagePolicy {

    /** 010-3675-3148, 01036753148, +82-10-3675-3148 (정규화 후라 구분자 없음) */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?82)?0?1[016789]\\d{7,8}(?!\\d)");

    /** 연속 8자리 이상 숫자 = 계좌·전화로 본다. 금액(150000)은 6자리라 통과 */
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");

    /** 은행·송금 앱 이름. 숫자가 없어도 막는다 ("국민" → 다음 메시지에 번호 보내는 수법) */
    private static final List<String> BANK_KEYWORDS = List.of(
            "은행", "국민", "kb", "신한", "농협", "nh", "기업", "ibk", "하나", "우리은행", "우리계좌", "카카오뱅크", "카뱅",
            "토스", "케이뱅크", "k뱅크", "새마을", "신협", "수협", "우체국계좌", "대구은행", "im뱅크", "부산은행", "경남은행",
            "광주은행", "전북은행", "제주은행", "sc제일", "씨티", "카카오페이", "네이버페이", "페이로", "페이머니");

    private static final List<String> EXTERNAL_CHANNEL_KEYWORDS = List.of(
            "카톡", "카카오톡", "카카오아이디", "카카오id", "카카오로", "오픈채팅", "오픈톡", "open.kakao", "openkakao",
            "인스타", "instagram", "insta", "디엠", "dm주", "텔레그램", "telegram", "t.me", "텔레", "디스코드", "디코",
            "라인아이디", "라인으로", "라인주", "문자로", "문자주", "문자줘", "전화주", "전화줘", "전화로", "전화번호",
            "번호주", "번호줘", "번호알려", "연락처", "핸드폰", "휴대폰", "폰번");

    private static final List<String> CASH_TRADE_KEYWORDS = List.of(
            "계좌", "송금", "이체", "현금", "직거래", "입금", "안전결제말고", "안전결제빼고", "안전결제없이", "안전결제안하",
            "수수료아끼", "수수료빼", "수수료없이", "따로거래", "밖에서거래", "직접거래");

    /** 정규화: 공백·구두점·기호 제거 + 소문자 */
    private static final Pattern NOISE = Pattern.compile("[\\s.\\-_*~·,!?/()\\[\\]{}:;'\"]+");

    /** 정규화 후 이 길이 이하면 "쪼개 보낸 조각"으로 보고 앞뒤를 이어 붙여 검사한다 */
    private static final int FRAGMENT_MAX_LENGTH = 3;

    private ChatMessagePolicy() {
    }

    /**
     * 메시지 한 건을 검사한다.
     *
     * @param content - 보내려는 메시지 원문
     * @return 차단 이유. 보내도 되면 empty
     */
    public static Optional<ChatBlockReason> check(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(content);

        if (PHONE.matcher(normalized).find()) {
            return Optional.of(ChatBlockReason.PHONE_NUMBER);
        }
        if (LONG_DIGITS.matcher(normalized).find() || containsAny(normalized, BANK_KEYWORDS)) {
            return Optional.of(ChatBlockReason.BANK_ACCOUNT);
        }
        if (containsAny(normalized, EXTERNAL_CHANNEL_KEYWORDS)) {
            return Optional.of(ChatBlockReason.EXTERNAL_CHANNEL);
        }
        if (containsAny(normalized, CASH_TRADE_KEYWORDS)) {
            return Optional.of(ChatBlockReason.CASH_TRADE);
        }
        return Optional.empty();
    }

    /**
     * 새 메시지 + 같은 사람이 직전에 연속으로 보낸 짧은 조각들을 이어 붙여 검사한다.
     * "국" "민" "은" "행" "1" "3" "4" 처럼 한 글자씩 나눠 보내는 수법을 잡는다.
     * 긴 일반 메시지가 나오면 거기서 끊으므로 정상 대화는 영향이 없다.
     *
     * @param content - 새 메시지 원문
     * @param recentOwnMessages - 같은 방에서 보낸 사람의 최근 메시지, 최신순
     * @return 차단 이유. 보내도 되면 empty
     */
    public static Optional<ChatBlockReason> checkWithRecent(String content, List<String> recentOwnMessages) {
        Optional<ChatBlockReason> single = check(content);
        if (single.isPresent()) {
            return single;
        }

        StringBuilder joined = new StringBuilder();
        for (String previous : recentOwnMessages) {          // 최신 → 과거 순
            String normalized = previous == null ? "" : normalize(previous);
            if (normalized.isEmpty() || normalized.length() > FRAGMENT_MAX_LENGTH) {
                break;                                        // 긴 메시지를 만나면 조각 묶음 끝
            }
            joined.insert(0, normalized);                     // 과거 조각을 앞에 붙여 원래 순서로
        }
        if (joined.isEmpty()) {
            return Optional.empty();
        }
        return check(joined + content);
    }

    private static String normalize(String content) {
        return NOISE.matcher(content).replaceAll("").toLowerCase();
    }

    private static boolean containsAny(String normalized, List<String> keywords) {
        return keywords.stream().anyMatch(normalized::contains);
    }
}