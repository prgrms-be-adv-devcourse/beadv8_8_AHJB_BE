package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.in.WalletDataInit;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberCreatedEvent → WalletEventListener → WalletMember 복제 → WalletMemberCreatedEvent → Wallet 생성
 * 흐름이 끝까지 이어지는지 확인한다.
 *
 * 리스너가 전부 AFTER_COMMIT 이라서 테스트 메서드에 @Transactional 을 붙이면 커밋이 안 돼 이벤트가 전달되지 않는다.
 * 그래서 TransactionTemplate 으로 실제 커밋되는 트랜잭션 안에서 이벤트를 발행하고, 뒷정리는 @AfterEach 에서 직접 한다.
 */
@SpringBootTest
class WalletCreateOnMemberCreatedTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletMemberRepository walletMemberRepository;

    @Autowired
    private WalletDataInit walletDataInit;

    private Long memberId;

    @AfterEach
    void tearDown() {
        if (memberId == null) return;
        walletRepository.findByHolderId(memberId).ifPresent(walletRepository::delete);
        walletMemberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 WalletMember 가 복제되고 USER 지갑이 잔액 0으로 생성된다")
    void memberCreatedEvent_createsUserWallet() {
        // given
        MemberDto member = randomMember();

        // when
        publishInCommittedTransaction(new MemberCreatedEvent(member));

        // then
        WalletMember walletMember = walletMemberRepository.findById(memberId).orElseThrow();
        assertThat(walletMember.getUuid()).isEqualTo(member.uuid());

        Wallet wallet = walletRepository.findByHolderId(memberId).orElseThrow();
        assertThat(wallet.getType()).isEqualTo(WalletType.USER);
        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getHolder().getId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("같은 회원의 생성 이벤트가 두 번 와도 지갑은 하나만 만들어진다")
    void duplicateMemberCreatedEvent_createsOnlyOneWallet() {
        // given
        MemberDto member = randomMember();

        // when
        publishInCommittedTransaction(new MemberCreatedEvent(member));
        publishInCommittedTransaction(new MemberCreatedEvent(member));

        // then
        List<Wallet> wallets = walletRepository.findAll().stream()
                .filter(w -> w.getHolder() != null && memberId.equals(w.getHolder().getId()))
                .toList();
        assertThat(wallets).hasSize(1);
    }

    @Test
    @DisplayName("이벤트를 발행한 트랜잭션이 롤백되면 지갑은 생성되지 않는다")
    void rolledBackTransaction_doesNotCreateWallet() {
        // given
        MemberDto member = randomMember();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new MemberCreatedEvent(member));
            status.setRollbackOnly();
        });

        // then
        assertThat(walletMemberRepository.findById(memberId)).isEmpty();
        assertThat(walletRepository.findByHolderId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("앱이 뜨면 ESCROW, FEE 시스템 지갑이 각각 하나씩, 소유자 없이 잔액 0으로 생성되어 있다")
    void systemWallets_areCreatedOnStartup() {
        for (WalletType type : List.of(WalletType.ESCROW, WalletType.FEE)) {
            Wallet systemWallet = walletRepository.findByTypeAndHolderIsNull(type).orElseThrow();
            assertThat(systemWallet.getType()).isEqualTo(type);
            assertThat(systemWallet.getHolder()).isNull();
            assertThat(systemWallet.getBalance()).isZero();

            long count = walletRepository.findAll().stream()
                    .filter(w -> w.getType() == type)
                    .count();
            assertThat(count).as("%s 시스템 지갑은 정확히 하나여야 한다", type).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("시스템 지갑 초기화는 멱등이라 다시 실행해도 지갑이 늘어나지 않는다")
    void walletDataInit_isIdempotent() throws Exception {
        long before = walletRepository.findAll().stream()
                .filter(w -> w.getHolder() == null)
                .count();

        walletDataInit.run(new DefaultApplicationArguments());

        long after = walletRepository.findAll().stream()
                .filter(w -> w.getHolder() == null)
                .count();
        assertThat(after).isEqualTo(before).isEqualTo(2);
    }

    private MemberDto randomMember() {
        // 다른 테스트/초기 데이터와 겹치지 않도록 큰 범위에서 임의 id 를 고른다
        memberId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        return new MemberDto(memberId, UUID.randomUUID(), "wallet-tester");
    }

    private void publishInCommittedTransaction(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
