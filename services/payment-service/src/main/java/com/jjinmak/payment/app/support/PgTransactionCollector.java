package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.PgPaymentState;
import com.jjinmak.payment.exception.PgApiException;
import com.jjinmak.payment.out.dto.TossTransactionResponse;
import com.jjinmak.payment.out.toss.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jjinmak.payment.exception.PaymentErrorCode.PG_INQUIRY_FAILED;
import static com.jjinmak.payment.out.toss.TossPaymentsClient.NOT_FOUND_PAYMENT;

/**
 * PG 대사에 쓸 토스 쪽 상태를 가져온다. 토스를 호출하므로 DB 트랜잭션 밖에서 불러야 한다.
 *
 *  - collect      : 대상일의 거래 목록을 끝까지 받아 주문번호별 상태로 접는다 (1차 판정용)
 *  - fetchCurrent : 주문번호 1건의 현재 상태를 단건 조회로 받는다 (2차 확정용)
 *
 * 거래 목록은 그날 일어난 승인·취소만 담고 있어, 승인이 다른 날인 결제는 collect 결과가 UNKNOWN 으로 나온다.
 * 그런 건과 1차 판정에서 불일치로 나온 건은 fetchCurrent 로 현재 상태를 받아 다시 대조한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgTransactionCollector {

    // 한 번에 받는 거래 수 (토스 기본값)
    static final int PAGE_SIZE = 100;
    // 페이지 수 상한. 넘으면 조회가 끝나지 않는 것으로 보고 실패시킨다 (PAGE_SIZE × MAX_PAGES = 하루 10만 건)
    static final int MAX_PAGES = 1_000;

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final TossPaymentsClient toss;


     // 대상일의 토스 거래를 주문번호별 상태로 돌려준다. 주문번호 = pgOrderId, 거래가 온 순서 유지
     // 승인·취소가 한 줄도 없는 주문번호(돈이 움직이지 않은 건)는 결과에 넣지 않는다.
    public Map<String, PgPaymentState> collect(LocalDate targetDate) {
        List<TossTransactionResponse> transactions = fetchAll(targetDate);

        Map<String, List<TossTransactionResponse>> byOrderId = new LinkedHashMap<>();
        for (TossTransactionResponse transaction : transactions) {
            if (transaction.orderId() == null) {
                log.warn("[PG 대사] 주문번호가 없는 토스 거래는 건너뜀. transactionKey={}", transaction.transactionKey());
                continue;
            }
            byOrderId.computeIfAbsent(transaction.orderId(), orderId -> new ArrayList<>()).add(transaction);
        }

        Map<String, PgPaymentState> states = new LinkedHashMap<>();
        byOrderId.forEach((orderId, orderTransactions) -> {
            PgPaymentState state = PgPaymentState.fromTransactions(orderId, orderTransactions);
            if (state.kind() != PgPaymentState.Kind.NONE) {
                states.put(orderId, state);
            }
        });

        log.info("[PG 대사] 토스 거래 수집 완료. targetDate={}, 거래 {}건, 주문번호 {}건",
                targetDate, transactions.size(), states.size());
        return states;
    }


    // 주문번호 1건의 현재 상태. 토스에 결제 기록이 없으면 NONE 을 돌려준다.
    // 그 외 토스 오류(통신 실패 등)는 그대로 던짐
    public PgPaymentState fetchCurrent(String pgOrderId) {
        try {
            return PgPaymentState.fromPayment(toss.findByOrderId(pgOrderId));
        } catch (PgApiException e) {
            if (e.isCode(NOT_FOUND_PAYMENT)) {
                return PgPaymentState.none(pgOrderId);
            }
            throw e;
        }
    }

    /**
     * 대상일 00:00:00 ~ 23:59:59 의 거래를 마지막 페이지까지 받는다.
     * 다음 페이지는 방금 받은 마지막 거래의 transactionKey 를 startingAfter 로 넘겨 받는다.
     *
     * transactionKey 로 중복을 걸러낸다.
     * 다음 페이지를 요청했는데 새 거래가 하나도 없으면 실패시킨다.
     */
    private List<TossTransactionResponse> fetchAll(LocalDate targetDate) {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(END_OF_DAY);

        List<TossTransactionResponse> all = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        String startingAfter = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            List<TossTransactionResponse> rows = toss.findTransactions(start, end, startingAfter, PAGE_SIZE);
            if (rows == null || rows.isEmpty()) {
                return all;
            }

            int added = 0;
            for (TossTransactionResponse row : rows) {
                if (row.transactionKey() == null || seenKeys.add(row.transactionKey())) {
                    all.add(row);
                    added++;
                }
            }

            if (rows.size() < PAGE_SIZE) {
                return all;   // 마지막 페이지
            }
            if (added == 0) {
                throw new PgApiException(PG_INQUIRY_FAILED, "PAGINATION_STUCK",
                        "거래 조회의 다음 페이지가 진행되지 않음. targetDate=" + targetDate + ", startingAfter=" + startingAfter);
            }
            startingAfter = rows.get(rows.size() - 1).transactionKey();
        }

        throw new PgApiException(PG_INQUIRY_FAILED, "TOO_MANY_PAGES",
                "거래 조회가 " + MAX_PAGES + " 페이지를 넘음. targetDate=" + targetDate);
    }
}
