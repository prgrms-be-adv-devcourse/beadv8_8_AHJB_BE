package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 원장 기록을 "대상 id → (거래 유형 → 금액)" 으로 묶어 다루는 도우미.
 * 충전 건 · 홀딩을 원장 기록과 맞춰 보는 검사(ChargeLedgerChecker, HoldingLedgerChecker)가 함께 쓴다.
 */
final class LedgerRecords {

    // 대상 id 목록으로 조회할 때 한 번에 넘기는 개수 (IN 절이 너무 길어지지 않게 나눠서 조회)
    static final int QUERY_CHUNK = 500;

    private LedgerRecords() {
    }

    // 대상 id 별로 유형마다의 금액을 모은다. 같은 대상의 같은 유형은 멱등키 때문에 1건뿐이지만, 여러 건이면 합친다
    static Map<Long, Map<WalletTransactionType, Long>> byReference(List<WalletTransaction> rows) {
        Map<Long, Map<WalletTransactionType, Long>> table = new HashMap<>();
        for (WalletTransaction row : rows) {
            table.computeIfAbsent(row.getReferenceId(), id -> new EnumMap<>(WalletTransactionType.class))
                    .merge(row.getType(), row.getAmount(), Long::sum);
        }
        return table;
    }

    // 사람이 읽을 기록 목록. 예: HOLD_PAY -30000, HOLD_RECEIVE 30000. 기록이 없으면 "없음"
    static String describe(Map<WalletTransactionType, Long> records) {
        if (records.isEmpty()) {
            return "없음";
        }
        return new EnumMap<>(records).entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    static List<List<Long>> chunks(Collection<Long> ids) {
        List<Long> all = new ArrayList<>(ids);
        List<List<Long>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += QUERY_CHUNK) {
            chunks.add(all.subList(from, Math.min(from + QUERY_CHUNK, all.size())));
        }
        return chunks;
    }
}
