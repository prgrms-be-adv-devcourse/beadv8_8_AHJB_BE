package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.exception.PgApiException;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.dto.TossTransactionResponse;
import com.jjinmak.back.payment.out.toss.TossPaymentsClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_INQUIRY_FAILED;

/**
 * 테스트용 토스 클라이언트. HTTP 를 부르지 않고, 미리 넣어 둔 거래 목록과 결제를 돌려준다.
 *
 *  - approved / canceled / transaction : 거래 목록(GET /v1/transactions)에 줄을 추가한다. 넣은 순서가 곧 응답 순서다
 *  - payment                           : 단건 조회(GET /v1/payments/orders/{orderId}) 결과를 정한다. 넣지 않은 주문번호는 NOT_FOUND_PAYMENT
 *
 * 어떤 요청이 들어왔는지(다음 페이지 커서, 단건 조회한 주문번호)를 기록해 두므로 호출 횟수와 순서를 검증할 수 있다.
 */
public class FakeTossPaymentsClient extends TossPaymentsClient {

    // 다음 페이지를 요청했을 때(startingAfter) 토스가 답하는 방식
    public enum CursorMode {
        AFTER,      // 커서 다음 줄부터 (정상)
        INCLUSIVE,  // 커서 줄을 다시 포함해서
        IGNORED     // 커서를 무시하고 항상 첫 페이지
    }

    private final List<TossTransactionResponse> transactions = new ArrayList<>();
    private final Map<String, TossPaymentResponse> payments = new HashMap<>();
    private final List<String> requestedCursors = new ArrayList<>();
    private final List<String> lookedUpOrderIds = new ArrayList<>();

    private CursorMode cursorMode = CursorMode.AFTER;
    private RuntimeException transactionFailure;
    private RuntimeException paymentFailure;
    private LocalDateTime lastStart;
    private LocalDateTime lastEnd;
    private int lastLimit;

    public FakeTossPaymentsClient() {
        super(null);   // RestClient 를 쓰는 메서드는 모두 재정의하므로 필요 없다
    }

    // 승인 거래 1줄
    public FakeTossPaymentsClient approved(String orderId, long amount) {
        return transaction(orderId, "DONE", amount);
    }

    // 취소 거래 1줄 (토스는 취소 금액도 양수로 준다)
    public FakeTossPaymentsClient canceled(String orderId, long amount) {
        return transaction(orderId, "CANCELED", amount);
    }

    public FakeTossPaymentsClient transaction(String orderId, String status, long amount) {
        String transactionKey = "tx-" + (transactions.size() + 1);
        transactions.add(new TossTransactionResponse(transactionKey, "pk-" + orderId, orderId, status, amount,
                "2026-10-21T15:00:00+09:00"));
        return this;
    }

    // 단건 조회 결과
    public FakeTossPaymentsClient payment(String orderId, String status, long totalAmount, long balanceAmount) {
        payments.put(orderId, new TossPaymentResponse("pk-" + orderId, orderId, "찐막 예치금 충전", status,
                totalAmount, balanceAmount, "간편결제", null, null, null, null, null));
        return this;
    }

    public void cursorMode(CursorMode cursorMode) {
        this.cursorMode = cursorMode;
    }

    // 거래 목록 조회를 실패시킨다. null 을 넘기면 다시 정상 응답
    public void failTransactions(RuntimeException failure) {
        this.transactionFailure = failure;
    }

    // 단건 조회를 실패시킨다. null 을 넘기면 다시 정상 응답
    public void failPayments(RuntimeException failure) {
        this.paymentFailure = failure;
    }

    @Override
    public List<TossTransactionResponse> findTransactions(LocalDateTime start, LocalDateTime end,
                                                          String startingAfter, int limit) {
        requestedCursors.add(startingAfter);
        lastStart = start;
        lastEnd = end;
        lastLimit = limit;
        if (transactionFailure != null) {
            throw transactionFailure;
        }

        int from = 0;
        if (startingAfter != null && cursorMode != CursorMode.IGNORED) {
            for (int i = 0; i < transactions.size(); i++) {
                if (startingAfter.equals(transactions.get(i).transactionKey())) {
                    from = cursorMode == CursorMode.AFTER ? i + 1 : i;
                    break;
                }
            }
        }
        return new ArrayList<>(transactions.subList(from, Math.min(transactions.size(), from + limit)));
    }

    @Override
    public TossPaymentResponse findByOrderId(String pgOrderId) {
        lookedUpOrderIds.add(pgOrderId);
        if (paymentFailure != null) {
            throw paymentFailure;
        }
        TossPaymentResponse payment = payments.get(pgOrderId);
        if (payment == null) {
            throw new PgApiException(PG_INQUIRY_FAILED, NOT_FOUND_PAYMENT, "존재하지 않는 결제 입니다.");
        }
        return payment;
    }

    // 거래 목록 조회 때마다 넘어온 startingAfter (첫 페이지는 null)
    public List<String> requestedCursors() {
        return requestedCursors;
    }

    // 단건 조회한 주문번호 (조회한 순서대로)
    public List<String> lookedUpOrderIds() {
        return lookedUpOrderIds;
    }

    public LocalDateTime lastStart() {
        return lastStart;
    }

    public LocalDateTime lastEnd() {
        return lastEnd;
    }

    public int lastLimit() {
        return lastLimit;
    }
}
