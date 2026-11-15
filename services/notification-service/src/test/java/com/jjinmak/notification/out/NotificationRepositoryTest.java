package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.domain.NotificationType;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.AUCTION;
import static com.jjinmak.notification.domain.NotificationTargetType.CART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// 알림 중복 저장(ON CONFLICT DO NOTHING)이 실제 PostgreSQL에서 예외 없이 동작하는지 확인한다.
// PostgreSQL은 한 문장이 실패하면 트랜잭션 전체가 중단되므로, "중복 예외를 잡고 넘어가기"로는
// 같은 트랜잭션의 다른 알림을 저장할 수 없다. 이 테스트가 그 반대(예외 없이 계속 저장)를 보장한다.
class NotificationRepositoryTest extends NotificationIntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    private static final Long AUCTION_ID = 12L;
    private static final Long WINNER_ID = 7L;
    private static final Long SELLER_ID = 9L;

    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    private Notification 낙찰자_알림() {
        return Notification.create(WINNER_ID, NotificationType.AUCTION_WON_BUYER,
                "낙찰되었습니다", "24시간 안에 결제해 주세요.",
                CART, AUCTION_ID, "auction-" + AUCTION_ID, NOW.minusSeconds(3));
    }

    private Notification 판매자_알림() {
        return Notification.create(SELLER_ID, NotificationType.AUCTION_WON_SELLER,
                "상품이 낙찰되었습니다", "구매자의 결제를 기다리고 있어요.",
                AUCTION, AUCTION_ID, "auction-" + AUCTION_ID, NOW.minusSeconds(3));
    }

    // 리스너 한 번의 처리처럼 여러 알림을 한 트랜잭션에서 저장하고, 새로 저장됐는지(id를 받았는지)를 돌려준다
    private List<Boolean> 한_트랜잭션에서_저장한다(Notification... notifications) {
        return transactionTemplate.execute(status -> Arrays.stream(notifications)
                .map(n -> notificationRepository.insertIgnoringDuplicate(n, NOW).isPresent())
                .toList());
    }

    @Test
    void 처음_저장하면_1건이_저장되고_묶음과_시각이_채워진다() {
        List<Boolean> result = 한_트랜잭션에서_저장한다(낙찰자_알림());

        assertThat(result).containsExactly(true);
        Notification saved = notificationRepository.findAll().getFirst();
        assertThat(saved.getReceiverId()).isEqualTo(WINNER_ID);
        assertThat(saved.getCategory()).isEqualTo(NotificationCategory.BUY);
        assertThat(saved.getDedupeKey()).isEqualTo("AUCTION_WON_BUYER:auction-12:member-7");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    void 새로_저장하면_저장된_알림의_id를_돌려준다() {
        Long id = transactionTemplate.execute(status ->
                notificationRepository.insertIgnoringDuplicate(낙찰자_알림(), NOW).orElseThrow());

        assertThat(notificationRepository.findById(id)).isPresent();
    }

    @Test
    void 같은_알림을_두_번_저장해도_예외_없이_1건만_남는다() {
        한_트랜잭션에서_저장한다(낙찰자_알림());

        assertThatCode(() -> {
            List<Boolean> second = 한_트랜잭션에서_저장한다(낙찰자_알림());
            assertThat(second).containsExactly(false);
        }).doesNotThrowAnyException();

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_이벤트를_다시_받으면_한_트랜잭션_안에서_중복은_건너뛰고_빠진_알림만_저장한다() {
        // 첫 수신 때 낙찰자 알림만 저장되고 판매자 알림은 빠진 상황
        한_트랜잭션에서_저장한다(낙찰자_알림());

        // 재수신: 낙찰 이벤트 1건 → 알림 2건을 한 트랜잭션에서 다시 저장
        List<Boolean> result = 한_트랜잭션에서_저장한다(낙찰자_알림(), 판매자_알림());

        assertThat(result).containsExactly(false, true);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(WINNER_ID, SELLER_ID);
    }

    @Test
    void 같은_사건이라도_받는_사람이_다르면_각각_저장된다() {
        List<Boolean> result = 한_트랜잭션에서_저장한다(낙찰자_알림(), 판매자_알림());

        assertThat(result).containsExactly(true, true);
        assertThat(notificationRepository.count()).isEqualTo(2);
    }
}
