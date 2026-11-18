package com.jjinmak.back.payment.in.scheduler;

import com.jjinmak.back.payment.app.dto.HoldDto;
import com.jjinmak.back.payment.app.facade.WalletFacade;
import com.jjinmak.back.payment.app.support.HoldingCreator;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.ReconciliationMismatch;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.MismatchStatus;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.MismatchType;
import com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.in.scheduler.ReconciliationScheduler;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationMismatchRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationRunRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.CHARGE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.DEPOSIT_REMAINING_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.ESCROW_HOLDING_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.HOLDING_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.LEDGER_GROUP_UNBALANCED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.FAILED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MISMATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원장 대사 1회의 전체 흐름을 실제 PostgreSQL 에서 확인한다: 스케줄러 → 파사드 → 유스케이스 → 검사 6개 → 실행 기록.
 * 검사가 쓰는 집계 쿼리가 실제 DB 에서 도는 곳은 여기뿐이다. (단위 테스트는 저장소를 가짜로 바꿔서 돈다)
 *
 * 데이터는 운영 코드로 만든다. 돈은 LedgerRecorder · HoldingCreator · WalletFacade 로만 움직여서 원장과 잔액이 함께 바뀌게 하고,
 * 불일치는 그 뒤에 SQL 로 한쪽만 바꿔서 만든다.
 *
 * 테스트마다 테이블을 비우고 시스템 지갑(ESCROW, FEE)만 다시 만든다.
 * 대사가 전체 지갑과 전체 홀딩을 보기 때문에, 다른 테스트의 데이터가 남아 있으면 결과가 달라진다.
 *
 * 기본 시나리오 (scenario)
 *   구매자(1) : 100,000 + 50,000 충전
 *     주문 101  낙찰 30,000 + 배송 3,000 = 33,000  결제                       → HELD
 *     주문 102  낙찰 20,000                        결제 → 구매 확정 → 정산    → RELEASED (정산 18,000, 수수료 2,000)
 *     주문 103  낙찰 10,000 + 배송 2,500 = 12,500  결제 → 환불                → REFUNDED
 *     주문 104  낙찰  5,000                        결제 → 구매 확정           → CONFIRMED (환불받은 돈으로 결제)
 *   다른 회원(3) : 20,000 충전 후 취소, 7,000 충전 후 전액 출금
 *
 *   결과  구매자 예치금 92,000 (미사용 충전 금액 34,500 + 50,000, 나머지 7,500 은 환불받은 돈) / 판매자(2) 정산금 18,000
 *         ESCROW 38,000 (= 주문 101 의 33,000 + 주문 104 의 5,000) / FEE 2,000
 */
class ReconciliationLedgerIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long OTHER_ID = 3L;

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired
    ReconciliationScheduler reconciliationScheduler;
    @Autowired WalletFacade walletFacade;
    @Autowired HoldingCreator holdingCreator;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired ReconciliationRunRepository runRepository;
    @Autowired ReconciliationMismatchRepository mismatchRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    // 대사 대상일. 테스트 데이터가 지금 만들어지므로 오늘이다
    private LocalDate today;

    private Long escrowWalletId;
    private Long buyerWalletId;
    private Long sellerWalletId;
    private Long firstChargeId;     // 구매자 100,000 충전. 주문 101 · 102 · 103 결제에 65,500 이 쓰여 미사용 금액 34,500
    private Long secondChargeId;    // 구매자 50,000 충전. 전부 미사용

    @BeforeEach
    void setUp() {
        resetDatabase();
        today = LocalDate.now();
    }

    @AfterEach
    void tearDown() {
        resetDatabase();     // 같은 DB 를 쓰는 다른 테스트가 앱을 막 띄운 상태에서 시작하게 한다
    }

    // 모든 테이블을 비우고 시스템 지갑만 다시 만든다 (앱을 막 띄운 상태와 같다)
    private void resetDatabase() {
        escrowWalletId = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("truncate table " + TABLES + " restart identity cascade").executeUpdate();
            walletRepository.save(Wallet.createSystemWallet(WalletType.FEE));
            return walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW)).getId();
        });
    }

    // ===== 데이터 만들기 (운영 코드로 돈을 움직인다) =====

    private Long createMemberWithWallet(long memberId) {
        return transactionTemplate.execute(status -> {
            WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            return walletRepository.save(Wallet.createUserWallet(member)).getId();
        });
    }

    // 충전. 승인 유스케이스가 토스 승인 뒤에 하는 일과 같다: 원장 CHARGE 기록 + 충전 건 CHARGED
    private Long charge(long memberId, long amount) {
        return transactionTemplate.execute(status -> {
            Wallet wallet = walletRepository.findByHolderId(memberId).orElseThrow();
            Charge charge = chargeRepository.save(Charge.requestManual(wallet, memberId, amount));
            ledgerRecorder.deposit(wallet, CHARGE, amount, PAYMENT, charge.getId());
            LocalDateTime now = LocalDateTime.now();
            charge.markCharged("payment-key-" + charge.getId(), now, now);
            return charge.getId();
        });
    }

    // 충전 취소. 취소 유스케이스가 토스 취소 앞뒤로 하는 일과 같다: 원장 CHARGE_CANCEL 기록 + 충전 건 CANCELED
    private void cancelCharge(long memberId, Long chargeId) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(memberId).orElseThrow();
            Charge charge = chargeRepository.findById(chargeId).orElseThrow();
            ledgerRecorder.withdraw(wallet, CHARGE_CANCEL, charge.getAmount(), PAYMENT, chargeId);
            charge.markCanceled(LocalDateTime.now());
        });
    }

    // 구매자가 판매자의 주문 1건을 예치금으로 결제 (홀딩 생성)
    private void pay(long orderId, long winningPrice, long deliveryFee) {
        HoldDto dto = new HoldDto(9_000L + orderId, BUYER_ID, winningPrice + deliveryFee,
                List.of(new HoldDto.Item(orderId, SELLER_ID, winningPrice, deliveryFee)));
        transactionTemplate.executeWithoutResult(status -> holdingCreator.hold(dto));
    }

    // 클래스 주석의 기본 시나리오
    private void scenario() {
        buyerWalletId = createMemberWithWallet(BUYER_ID);
        sellerWalletId = createMemberWithWallet(SELLER_ID);
        createMemberWithWallet(OTHER_ID);

        firstChargeId = charge(BUYER_ID, 100_000);
        secondChargeId = charge(BUYER_ID, 50_000);

        pay(101L, 30_000, 3_000);

        pay(102L, 20_000, 0);
        walletFacade.confirmHolding(102L);
        walletFacade.releaseHolding(102L);

        pay(103L, 10_000, 2_500);
        walletFacade.refund(7_001L, 103L, 12_500);

        pay(104L, 5_000, 0);
        walletFacade.confirmHolding(104L);

        Long canceledChargeId = charge(OTHER_ID, 20_000);
        cancelCharge(OTHER_ID, canceledChargeId);
        charge(OTHER_ID, 7_000);
        walletFacade.withdrawAll(OTHER_ID);
    }

    // 포인트 적립. 플랫폼이 주는 돈이라 원장에 한 줄만 남는다
    private void earnPoint(long memberId, long orderId, long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(memberId).orElseThrow();
            ledgerRecorder.depositPoint(wallet, POINT_EARN, amount, ORDER, orderId);
        });
    }

    // 주문 1건을 전액 포인트로 결제했다가 그대로 환불받는다 (구매자 포인트 → ESCROW → 구매자 포인트). 끝나면 ESCROW 잔액은 원래대로다
    private void payWithPointThenRefund(long memberId, long orderId, long amount) {
        HoldDto dto = new HoldDto(9_000L + orderId, memberId, amount,
                List.of(new HoldDto.Item(orderId, SELLER_ID, amount, 0)), new PaymentShare(amount, 0, 0));
        transactionTemplate.executeWithoutResult(status -> holdingCreator.hold(dto));
        walletFacade.refund(7_000L + orderId, orderId, amount);
    }

    private Wallet walletOf(long memberId) {
        return walletRepository.findByHolderId(memberId).orElseThrow();
    }

    // 원장을 거치지 않고 DB 값만 바꾼다. 파라미터는 SQL 의 ?1, ?2 ... 자리에 들어간다
    private void tamper(String sql, Object... params) {
        transactionTemplate.executeWithoutResult(status -> {
            Query query = entityManager.createNativeQuery(sql);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }
            query.executeUpdate();
        });
    }

    // ===== 결과 읽기 =====

    private ReconciliationRun reconcile(LocalDate targetDate) {
        reconciliationScheduler.reconcileLedger(targetDate);
        return runRepository.findByTypeAndTargetDate(LEDGER, targetDate).orElseThrow();
    }

    private List<ReconciliationMismatch> mismatchesOf(ReconciliationRun run) {
        return mismatchRepository.findByRun(run);
    }

    private ReconciliationMismatch onlyMismatchOf(ReconciliationRun run, MismatchType type) {
        List<ReconciliationMismatch> found = mismatchesOf(run).stream()
                .filter(mismatch -> mismatch.getType() == type)
                .toList();
        assertThat(found).as("%s 불일치", type).hasSize(1);
        return found.get(0);
    }

    // 스케줄러는 예외를 삼키고 FAILED 로 기록하므로, 기대와 다르면 실패 사유를 함께 보여 준다
    private static void assertStatus(ReconciliationRun run, ReconciliationRunStatus expected) {
        assertThat(run.getStatus()).as("failureReason=%s", run.getFailureReason()).isEqualTo(expected);
    }

    // ===== 일치 =====

    @Test
    @DisplayName("거래가 하나도 없어도 대사는 일치로 끝난다")
    void emptyLedgerMatches() {
        ReconciliationRun run = reconcile(today);

        assertStatus(run, MATCHED);
        assertThat(run.getMismatchCount()).isZero();
        assertThat(run.getCheckedCount()).isEqualTo(3);      // ESCROW 1 + 지갑 2개(ESCROW, FEE)
        assertThat(mismatchesOf(run)).isEmpty();
    }

    @Test
    @DisplayName("충전 · 결제 · 정산 · 환불 · 충전 취소 · 출금이 정상으로 끝난 날은 검사 6개가 모두 일치")
    void normalFlowMatches() {
        scenario();

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MATCHED);
        assertThat(run.getMismatchCount()).isZero();
        assertThat(mismatchesOf(run)).isEmpty();
        assertThat(run.getAttempt()).isEqualTo(1);
        // ESCROW 1 + 미사용 충전 금액이 있는 지갑 1 + 전체 지갑 5 + 거래 그룹 6(결제 4, 정산 1, 환불 1) + 충전 건 4 + 주문 4
        assertThat(run.getCheckedCount()).isEqualTo(21);
    }

    @Test
    @DisplayName("일치로 끝난 날은 다시 돌려도 검사하지 않는다")
    void matchedRunIsNotRepeated() {
        scenario();
        reconcile(today);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MATCHED);
        assertThat(run.getAttempt()).isEqualTo(1);
    }

    // ===== 포인트 =====

    @Test
    @DisplayName("포인트 적립 · 포인트 결제 · 포인트 환불이 있어도 검사는 모두 일치하고, 전액 출금은 포인트를 건드리지 않는다")
    void pointFlowMatches() {
        scenario();
        earnPoint(BUYER_ID, 102L, 1_000);
        payWithPointThenRefund(BUYER_ID, 201L, 600);

        charge(OTHER_ID, 3_000);
        earnPoint(OTHER_ID, 202L, 500);
        walletFacade.withdrawAll(OTHER_ID);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MATCHED);
        assertThat(mismatchesOf(run)).isEmpty();

        Wallet buyer = walletOf(BUYER_ID);
        assertThat(buyer.getPointBalance()).isEqualTo(1_000);            // 600 을 썼다가 포인트로 돌려받았다
        assertThat(buyer.getBalance()).isEqualTo(92_000);                // 예치금은 기본 시나리오 그대로

        Wallet other = walletOf(OTHER_ID);
        assertThat(other.getBalance()).isZero();                         // 예치금만 출금됐다
        assertThat(other.getPointBalance()).isEqualTo(500);

        assertThat(walletRepository.findById(escrowWalletId).orElseThrow().getBalance()).isEqualTo(38_000);
    }

    @Test
    @DisplayName("포인트 잔액만 바뀌면 포인트 주머니가 원장 합계와 어긋난다")
    void pointBalanceChangedWithoutLedger() {
        scenario();
        earnPoint(BUYER_ID, 102L, 1_000);
        tamper("update wallet set point_balance = point_balance + 300 where id = ?1", buyerWalletId);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).hasSize(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(run, BALANCE_LEDGER_MISMATCH);
        assertThat(mismatch.getTargetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(mismatch.getTargetKey()).isEqualTo(buyerWalletId + ":POINT");
        assertThat(mismatch.getExpectedAmount()).isEqualTo(1_000L);      // 원장 합계
        assertThat(mismatch.getActualAmount()).isEqualTo(1_300L);        // 지갑 포인트
    }

    @Test
    @DisplayName("포인트 결제의 한쪽 기록이 없으면 그룹 합이 0 이 아니고, ESCROW 원장 합계와 그 주문의 홀딩 기록도 어긋난다")
    void oneSideOfPointTransferMissing() {
        scenario();
        earnPoint(BUYER_ID, 102L, 1_000);
        payWithPointThenRefund(BUYER_ID, 201L, 600);
        tamper("delete from wallet_transactions where type = 'POINT_HOLD_RECEIVE' and reference_type = 'ORDER' and reference_id = ?1",
                201L);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(LEDGER_GROUP_UNBALANCED, BALANCE_LEDGER_MISMATCH, HOLDING_LEDGER_MISMATCH);

        ReconciliationMismatch holding = onlyMismatchOf(run, HOLDING_LEDGER_MISMATCH);
        assertThat(holding.getTargetKey()).isEqualTo("201");              // 포인트로 결제한 주문

        ReconciliationMismatch group = onlyMismatchOf(run, LEDGER_GROUP_UNBALANCED);
        assertThat(group.getExpectedAmount()).isEqualTo(0L);
        assertThat(group.getActualAmount()).isEqualTo(-600L);            // 구매자 포인트에서 나간 기록만 남았다

        ReconciliationMismatch balance = onlyMismatchOf(run, BALANCE_LEDGER_MISMATCH);
        assertThat(balance.getTargetKey()).isEqualTo(escrowWalletId + ":DEPOSIT");
        assertThat(balance.getExpectedAmount()).isEqualTo(37_400L);      // ESCROW 원장 합계 (38,000 - 600)
        assertThat(balance.getActualAmount()).isEqualTo(38_000L);
    }

    // ===== 검사별 불일치 =====

    @Test
    @DisplayName("지갑 잔액만 바뀌면 그 주머니가 원장 합계와 어긋난다 (예치금 · 정산금 따로)")
    void balanceChangedWithoutLedger() {
        scenario();
        tamper("update wallet set balance = balance + 1000, settlement_balance = settlement_balance + 500 where id = ?1",
                sellerWalletId);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(run.getMismatchCount()).isEqualTo(2);
        List<ReconciliationMismatch> mismatches = mismatchesOf(run);
        assertThat(mismatches).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(BALANCE_LEDGER_MISMATCH, BALANCE_LEDGER_MISMATCH);

        ReconciliationMismatch deposit = mismatches.stream()
                .filter(mismatch -> mismatch.getTargetKey().equals(sellerWalletId + ":DEPOSIT")).findFirst().orElseThrow();
        assertThat(deposit.getTargetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(deposit.getExpectedAmount()).isEqualTo(0L);           // 원장 합계: 판매자는 예치금 거래가 없다
        assertThat(deposit.getActualAmount()).isEqualTo(1_000L);         // 지갑 잔액
        assertThat(deposit.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(deposit.getPgOrderId()).isNull();

        ReconciliationMismatch settlement = mismatches.stream()
                .filter(mismatch -> mismatch.getTargetKey().equals(sellerWalletId + ":SETTLEMENT")).findFirst().orElseThrow();
        assertThat(settlement.getExpectedAmount()).isEqualTo(18_000L);   // 원장 합계: 주문 102 의 정산액
        assertThat(settlement.getActualAmount()).isEqualTo(18_500L);
    }

    @Test
    @DisplayName("ESCROW 잔액만 바뀌면 홀딩 합과도, 원장 합계와도 어긋난다")
    void escrowBalanceChangedWithoutLedger() {
        scenario();
        tamper("update wallet set balance = balance - 1000 where id = ?1", escrowWalletId);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(ESCROW_HOLDING_MISMATCH, BALANCE_LEDGER_MISMATCH);

        ReconciliationMismatch escrow = onlyMismatchOf(run, ESCROW_HOLDING_MISMATCH);
        assertThat(escrow.getTargetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(escrow.getTargetKey()).isEqualTo(String.valueOf(escrowWalletId));
        assertThat(escrow.getExpectedAmount()).isEqualTo(38_000L);       // HELD(주문 101) + CONFIRMED(주문 104) 홀딩 합
        assertThat(escrow.getActualAmount()).isEqualTo(37_000L);         // ESCROW 잔액

        assertThat(onlyMismatchOf(run, BALANCE_LEDGER_MISMATCH).getTargetKey()).isEqualTo(escrowWalletId + ":DEPOSIT");
    }

    @Test
    @DisplayName("충전 건의 미사용 금액이 줄지 않았으면 예치금보다 미사용 금액 합이 커진다")
    void remainingAmountNotConsumed() {
        scenario();
        tamper("update charge set remaining_amount = amount where id = ?1", firstChargeId);    // 34,500 → 100,000

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).hasSize(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(run, DEPOSIT_REMAINING_MISMATCH);
        assertThat(mismatch.getTargetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(mismatch.getTargetKey()).isEqualTo(String.valueOf(buyerWalletId));
        assertThat(mismatch.getExpectedAmount()).isEqualTo(150_000L);    // 미사용 충전 금액 합
        assertThat(mismatch.getActualAmount()).isEqualTo(92_000L);       // 구매자 예치금
    }

    @Test
    @DisplayName("거래의 한쪽 기록이 없으면 그룹 합이 0 이 아니고, 같은 원인으로 ESCROW 원장 합계와 그 주문의 홀딩도 어긋난다")
    void oneSideOfTransferMissing() {
        scenario();
        tamper("delete from wallet_transactions where type = 'HOLD_RECEIVE' and reference_type = 'ORDER' and reference_id = ?1",
                101L);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(LEDGER_GROUP_UNBALANCED, BALANCE_LEDGER_MISMATCH, HOLDING_LEDGER_MISMATCH);

        ReconciliationMismatch group = onlyMismatchOf(run, LEDGER_GROUP_UNBALANCED);
        assertThat(group.getTargetType()).isEqualTo(MismatchTargetType.LEDGER_GROUP);
        assertThat(group.getExpectedAmount()).isEqualTo(0L);
        assertThat(group.getActualAmount()).isEqualTo(-33_000L);         // 구매자에게서 나간 기록만 남았다

        ReconciliationMismatch balance = onlyMismatchOf(run, BALANCE_LEDGER_MISMATCH);
        assertThat(balance.getTargetKey()).isEqualTo(escrowWalletId + ":DEPOSIT");
        assertThat(balance.getExpectedAmount()).isEqualTo(5_000L);       // ESCROW 원장 합계 (38,000 - 33,000)
        assertThat(balance.getActualAmount()).isEqualTo(38_000L);

        ReconciliationMismatch holding = onlyMismatchOf(run, HOLDING_LEDGER_MISMATCH);
        assertThat(holding.getTargetKey()).isEqualTo("101");
    }

    @Test
    @DisplayName("충전 건 상태만 CANCELED 로 바뀌고 원장에 취소 기록이 없으면 충전 건과 원장이 어긋난다")
    void chargeStatusChangedWithoutLedger() {
        scenario();
        tamper("update charge set status = 'CANCELED', remaining_amount = 0 where id = ?1", secondChargeId);

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).hasSize(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(run, CHARGE_LEDGER_MISMATCH);
        assertThat(mismatch.getTargetType()).isEqualTo(MismatchTargetType.CHARGE);
        assertThat(mismatch.getTargetKey()).isEqualTo(String.valueOf(secondChargeId));
        assertThat(mismatch.getExpectedState()).isEqualTo("CHARGE");     // 원장에는 충전 기록만 있다
        assertThat(mismatch.getExpectedAmount()).isEqualTo(50_000L);
        assertThat(mismatch.getActualState()).isEqualTo("CANCELED");
        assertThat(mismatch.getActualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("정산된 홀딩의 수수료가 원장에 기록된 금액과 다르면 홀딩과 원장이 어긋난다")
    void holdingFeeDiffersFromLedger() {
        scenario();
        tamper("update holding set fee_amount = 1000 where order_id = ?1", 102L);                // 원장에는 수수료 2,000

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MISMATCHED);
        assertThat(mismatchesOf(run)).hasSize(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(run, HOLDING_LEDGER_MISMATCH);
        assertThat(mismatch.getTargetType()).isEqualTo(MismatchTargetType.HOLDING);
        assertThat(mismatch.getTargetKey()).isEqualTo("102");            // 주문 id
        assertThat(mismatch.getExpectedState()).isEqualTo("RELEASED");
        assertThat(mismatch.getActualState()).isEqualTo("RELEASED");
        assertThat(mismatch.getDetail()).contains("SETTLEMENT_PAY -18000", "FEE_PAY -2000");
    }

    // ===== 대상일 =====

    @Test
    @DisplayName("날짜로 대상을 고르는 검사는 대상일에 바뀐 것만 본다: 오늘 어긋난 충전 건은 어제 대사에 잡히지 않는다")
    void dateScopedChecksLookOnlyAtTargetDate() {
        scenario();
        tamper("update charge set status = 'CANCELED', remaining_amount = 0 where id = ?1", secondChargeId);

        ReconciliationRun yesterdayRun = reconcile(today.minusDays(1));

        assertStatus(yesterdayRun, MATCHED);
        // 지금 상태를 보는 검사만 대상이 있다: ESCROW 1 + 미사용 충전 금액이 있는 지갑 1 + 전체 지갑 5
        assertThat(yesterdayRun.getCheckedCount()).isEqualTo(7);

        ReconciliationRun todayRun = reconcile(today);

        assertStatus(todayRun, MISMATCHED);
        assertThat(todayRun.getId()).isNotEqualTo(yesterdayRun.getId());
        assertThat(onlyMismatchOf(todayRun, CHARGE_LEDGER_MISMATCH).getTargetKey()).isEqualTo(String.valueOf(secondChargeId));
    }

    // ===== 재실행 =====

    @Test
    @DisplayName("불일치가 그대로면 재실행해도 같은 행이 OPEN 으로 남고 새로 쌓이지 않는다")
    void rerunKeepsOpenMismatch() {
        scenario();
        tamper("update charge set remaining_amount = amount where id = ?1", firstChargeId);
        ReconciliationRun first = reconcile(today);
        Long mismatchId = onlyMismatchOf(first, DEPOSIT_REMAINING_MISMATCH).getId();

        ReconciliationRun second = reconcile(today);

        assertStatus(second, MISMATCHED);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getAttempt()).isEqualTo(2);
        assertThat(second.getMismatchCount()).isEqualTo(1);
        assertThat(mismatchesOf(second)).hasSize(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(second, DEPOSIT_REMAINING_MISMATCH);
        assertThat(mismatch.getId()).isEqualTo(mismatchId);
        assertThat(mismatch.getStatus()).isEqualTo(MismatchStatus.OPEN);
    }

    @Test
    @DisplayName("원인을 고친 뒤 재실행하면 불일치는 자동으로 해결 처리되고 실행은 일치로 끝난다")
    void rerunResolvesFixedMismatch() {
        scenario();
        tamper("update charge set remaining_amount = amount where id = ?1", firstChargeId);
        reconcile(today);
        tamper("update charge set remaining_amount = 34500 where id = ?1", firstChargeId);       // 원래 값으로

        ReconciliationRun run = reconcile(today);

        assertStatus(run, MATCHED);
        assertThat(run.getAttempt()).isEqualTo(2);
        assertThat(run.getMismatchCount()).isZero();
        ReconciliationMismatch mismatch = onlyMismatchOf(run, DEPOSIT_REMAINING_MISMATCH);
        assertThat(mismatch.getStatus()).isEqualTo(MismatchStatus.RESOLVED);
        assertThat(mismatch.getResolvedBy()).isEqualTo(ReconciliationMismatch.RESOLVED_BY_SYSTEM);
    }

    @Test
    @DisplayName("운영자가 해결했다고 닫았는데 재실행에서 다시 발견되면 OPEN 으로 되돌리고 이전 처리 이력을 남긴다")
    void rerunReopensResolvedMismatch() {
        scenario();
        tamper("update charge set remaining_amount = amount where id = ?1", firstChargeId);
        ReconciliationRun first = reconcile(today);
        Long mismatchId = onlyMismatchOf(first, DEPOSIT_REMAINING_MISMATCH).getId();
        transactionTemplate.executeWithoutResult(status -> mismatchRepository.findById(mismatchId).orElseThrow()
                .resolve(LocalDateTime.now(), "admin-1", "미사용 금액 보정 완료"));

        ReconciliationRun second = reconcile(today);

        assertStatus(second, MISMATCHED);
        assertThat(second.getMismatchCount()).isEqualTo(1);
        ReconciliationMismatch mismatch = onlyMismatchOf(second, DEPOSIT_REMAINING_MISMATCH);
        assertThat(mismatch.getId()).isEqualTo(mismatchId);
        assertThat(mismatch.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(mismatch.getResolvedBy()).isNull();
        assertThat(mismatch.getDetail()).contains("이전 처리", "admin-1", "미사용 금액 보정 완료");
    }

    // ===== 실패 =====

    @Test
    @DisplayName("검사를 끝내지 못하면 불일치가 아니라 FAILED 로 기록하고, 원인이 사라진 뒤 재실행하면 이어서 끝낸다")
    void failedRunIsRecordedAndCanBeRerun() {
        tamper("delete from wallet where id = ?1", escrowWalletId);      // ESCROW 지갑이 없어 검사할 수 없다

        ReconciliationRun failed = reconcile(today);

        assertThat(failed.getStatus()).isEqualTo(FAILED);
        assertThat(failed.getFailureReason()).startsWith("BusinessException");
        assertThat(mismatchesOf(failed)).isEmpty();

        transactionTemplate.executeWithoutResult(
                status -> walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW)));

        ReconciliationRun rerun = reconcile(today);

        assertStatus(rerun, MATCHED);
        assertThat(rerun.getId()).isEqualTo(failed.getId());
        assertThat(rerun.getAttempt()).isEqualTo(2);
        assertThat(rerun.getFailureReason()).isNull();
    }
}
