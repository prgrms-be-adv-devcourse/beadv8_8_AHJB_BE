package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WalletFacade.charge() 검증.
 *
 * 결제 컨텍스트에서 ChargeDto 가 넘어왔을 때
 *  - 성공: USER 지갑 잔액이 오르고 원장(WalletTransaction)에 CHARGE 한 줄이 남는다
 *  - 실패: 금액이 잘못되었거나 지갑을 찾을 수 없으면 BusinessException 이 나고, 잔액·원장 모두 변하지 않는다
 *
 * 이벤트 흐름(MemberCreatedEvent → 지갑 생성)은 WalletCreateOnMemberCreatedTest 에서 이미 검증하므로
 * 여기서는 WalletMember / Wallet 을 리포지토리로 직접 만들어 둔다.
 * facade 메서드가 @Transactional 이라 테스트 메서드에는 @Transactional 을 붙이지 않고 실제 커밋 결과를 조회한다.
 */
@SpringBootTest
class WalletChargeTest {

    @Autowired
    private WalletFacade walletFacade;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletMemberRepository walletMemberRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    private Long memberId;
    private Long walletId;

    @BeforeEach
    void setUp() {
        // 다른 테스트/초기 데이터와 겹치지 않도록 큰 범위에서 임의 id 를 고른다
        memberId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        walletId = walletRepository.save(Wallet.createUserWallet(member)).getId();
    }

    @AfterEach
    void tearDown() {
        walletTransactionRepository.deleteAll(transactionsOfWallet());
        walletRepository.deleteById(walletId);
        walletMemberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("정상적인 ChargeDto 가 들어오면 지갑 잔액이 오르고 원장에 CHARGE 한 줄이 기록된다")
    void charge_success_updatesBalanceAndRecordsTransaction() {
        // given
        long pgAmount = 10_000L;
        Long paymentId = 777L;
        ChargeDto dto = new ChargeDto(memberId, pgAmount, paymentId);

        // when
        walletFacade.charge(dto);

        // then - 지갑
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(pgAmount);

        // then - 원장
        List<WalletTransaction> transactions = transactionsOfWallet();
        assertThat(transactions).hasSize(1);

        WalletTransaction tx = transactions.get(0);
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.CHARGE);
        assertThat(tx.getAmount()).isEqualTo(pgAmount);          // 입금이므로 +
        assertThat(tx.getBalanceAfter()).isEqualTo(pgAmount);    // 충전 직후 잔액 스냅샷
        assertThat(tx.getReferenceType()).isEqualTo(ReferenceType.PAYMENT);
        assertThat(tx.getReferenceId()).isEqualTo(paymentId);
        assertThat(tx.getIdempotencyKey()).isEqualTo("PAYMENT:" + paymentId + ":CHARGE");
        assertThat(tx.getTransactionGroupId()).isNotBlank();
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("pgAmount 가 음수이면 INVALID_AMOUNT 로 실패하고 잔액과 원장은 변하지 않는다")
    void charge_negativeAmount_fails() {
        // given
        ChargeDto dto = new ChargeDto(memberId, -5_000L, 777L);

        // when & then
        assertThatThrownBy(() -> walletFacade.charge(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(INVALID_AMOUNT);

        assertNothingChanged();
    }

    @Test
    @DisplayName("holderId 에 해당하는 지갑이 없으면 WALLET_NOT_FOUND 로 실패하고 원장은 기록되지 않는다")
    void charge_unknownHolder_fails() {
        // given
        Long unknownHolderId = memberId + 1; // setUp 에서 만든 회원과 다른 id
        ChargeDto dto = new ChargeDto(unknownHolderId, 10_000L, 777L);

        // when & then
        assertThatThrownBy(() -> walletFacade.charge(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(WALLET_NOT_FOUND);

        assertNothingChanged();
    }

    private void assertNothingChanged() {
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isZero();
        assertThat(transactionsOfWallet()).isEmpty();
    }

    private List<WalletTransaction> transactionsOfWallet() {
        // 아직 wallet 기준 조회 메서드가 없어 findAll 후 필터링한다. (LAZY 프록시여도 getId() 는 초기화 없이 읽힌다)
        return walletTransactionRepository.findAll().stream()
                .filter(tx -> walletId.equals(tx.getWallet().getId()))
                .toList();
    }
}
