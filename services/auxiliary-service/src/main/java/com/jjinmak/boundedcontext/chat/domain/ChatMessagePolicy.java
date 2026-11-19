package com.jjinmak.boundedcontext.chat.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 외부 거래 유도 메시지 차단 규칙. Spring 의존 없는 순수 규칙이라 단위 테스트로 오탐을 관리한다.
 * 전송 시점에 동기로 돌아야 하므로 정규식·키워드만 쓴다 (LLM·임베딩은 지연·비용·설명 불가로 제외).
 *
 * 숫자 검사는 원문으로, 키워드 검사는 공백·구두점을 뺀 정규화본으로 한다 ("카 톡", "계.좌" 같은 우회 대응).
 * 하이픈 없는 긴 숫자(운송장 12~13자리)는 막지 않는다.
 */
public final class ChatMessagePolicy {

    /** 010-1234-5678, 01012345678, 010.1234.5678, +82-10-1234-5678 */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+82[-\\s]?0?|0)1[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");

    /** 110-123-456789 처럼 하이픈으로 나뉜 숫자 묶음 (총 10자리 이상일 때만 계좌로 본다) */
    private static final Pattern HYPHENATED_DIGITS = Pattern.compile("(?<!\\d)\\d{2,6}(?:-\\d{2,8}){1,3}(?!\\d)");
    private static final int ACCOUNT_MIN_DIGITS = 10;

    /** 은행 이름 + 6자리 이상 숫자. "하나", "우리", "우체국"처럼 일상어·택배사와 겹치는 이름은 "은행"을 붙인 형태만 */
    private static final Pattern BANK_NAME = Pattern.compile(
            "은행|국민은행|KB국민|신한|농협|NH|기업은행|IBK|하나은행|우리은행|카카오뱅크|카뱅|토스뱅크|토뱅|케이뱅크|K뱅크|새마을|신협|수협|"
                    + "대구은행|iM뱅크|부산은행|경남은행|광주은행|전북은행|제주은행|SC제일|씨티",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");

    private static final List<String> EXTERNAL_CHANNEL_KEYWORDS = List.of(
            "카톡", "카카오톡", "카카오아이디", "카카오id", "오픈채팅", "오픈톡", "open.kakao", "openkakao",
            "인스타", "instagram", "텔레그램", "telegram", "t.me/", "디스코드", "디코",
            "라인아이디", "라인으로", "문자로", "문자주", "문자줘", "전화주", "전화줘", "전화로",
            "번호주", "번호줘", "번호알려", "연락처", "핸드폰번호", "휴대폰번호", "폰번");

    private static final List<String> CASH_TRADE_KEYWORDS = List.of(
            "계좌", "송금", "이체", "현금", "직거래", "입금", "안전결제말고", "안전결제빼고", "안전결제없이", "안전결제안하",
            "수수료아끼", "수수료빼", "수수료없이");

    /** 키워드 비교용 정규화: 공백·구두점·기호 제거 + 소문자 */
    private static final Pattern NOISE = Pattern.compile("[\\s.\\-_*~·,!?]+");

    private ChatMessagePolicy() {
    }

    /**
     * 메시지를 검사한다.
     *
     * @param content - 보내려는 메시지 원문
     * @return 차단 이유. 보내도 되면 empty
     */
    public static Optional<ChatBlockReason> check(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        if (PHONE.matcher(content).find()) {
            return Optional.of(ChatBlockReason.PHONE_NUMBER);
        }
        if (looksLikeAccount(content)) {
            return Optional.of(ChatBlockReason.BANK_ACCOUNT);
        }

        String normalized = NOISE.matcher(content).replaceAll("").toLowerCase();
        if (containsAny(normalized, EXTERNAL_CHANNEL_KEYWORDS)) {
            return Optional.of(ChatBlockReason.EXTERNAL_CHANNEL);
        }
        if (containsAny(normalized, CASH_TRADE_KEYWORDS)) {
            return Optional.of(ChatBlockReason.CASH_TRADE);
        }
        return Optional.empty();
    }

    private static boolean looksLikeAccount(String content) {
        Matcher hyphenated = HYPHENATED_DIGITS.matcher(content);
        while (hyphenated.find()) {
            long digits = hyphenated.group().chars().filter(Character::isDigit).count();
            if (digits >= ACCOUNT_MIN_DIGITS) {
                return true;
            }
        }
        return BANK_NAME.matcher(content).find() && LONG_DIGITS.matcher(content).find();
    }

    private static boolean containsAny(String normalized, List<String> keywords) {
        return keywords.stream().anyMatch(normalized::contains);
    }
}