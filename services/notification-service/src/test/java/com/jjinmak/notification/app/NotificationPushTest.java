package com.jjinmak.notification.app;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationDeviceRepository;
import com.jjinmak.notification.out.NotificationRepository;
import com.jjinmak.notification.out.NotificationSettingRepository;
import com.jjinmak.notification.out.push.PushMessage;
import com.jjinmak.notification.out.push.PushResult;
import com.jjinmak.notification.out.push.PushSender;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;

import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// 알림 저장 → 커밋 후 비동기 푸시 흐름을 실제 PostgreSQL과 스프링 이벤트로 확인한다. 발송기만 가짜로 바꾼다.
class NotificationPushTest extends NotificationIntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final Long WINNER_ID = 7L;
    private static final Long SELLER_ID = 9L;
    private static final AuctionWonEvent WON =
            new AuctionWonEvent(12L, 3L, WINNER_ID, SELLER_ID, 50_000L, 3_000L, NOW.minusSeconds(3));

    @MockitoBean PushSender pushSender;

    @Autowired NotificationFacade notificationFacade;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationDeviceRepository notificationDeviceRepository;
    @Autowired NotificationSettingRepository notificationSettingRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        given(pushSender.send(any(), anyList())).willAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(1);
            return tokens.stream().map(PushResult::success).toList();
        });
        기기를_등록한다(WINNER_ID, "token-winner");
        기기를_등록한다(SELLER_ID, "token-seller");
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
        notificationDeviceRepository.deleteAllInBatch();
        notificationSettingRepository.deleteAllInBatch();
    }

    private void 기기를_등록한다(Long memberId, String token) {
        transactionTemplate.executeWithoutResult(status ->
                notificationDeviceRepository.upsert(memberId, token, "WEB", NOW));
    }

    private static PushMessage 받는_사람이(Long receiverId) {
        return argThat(message -> message != null && message.receiverId().equals(receiverId));
    }

    private void 될_때까지_기다린다(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 30 && !condition.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    void 알림이_저장되면_커밋_후_수신자의_기기로_보내고_발송_시각을_기록한다() throws Exception {
        notificationFacade.notifyAuctionWon(WON, NOW);

        verify(pushSender, timeout(3000)).send(받는_사람이(WINNER_ID), argThat(tokens -> tokens.equals(List.of("token-winner"))));
        verify(pushSender, timeout(3000)).send(받는_사람이(SELLER_ID), argThat(tokens -> tokens.equals(List.of("token-seller"))));
        될_때까지_기다린다(() -> notificationRepository.findAll().stream()
                .allMatch(n -> n.getPushedAt() != null));
    }

    @Test
    void 같은_이벤트를_다시_받으면_푸시를_다시_보내지_않는다() {
        notificationFacade.notifyAuctionWon(WON, NOW);
        verify(pushSender, timeout(3000).times(2)).send(any(), anyList());
        clearInvocations(pushSender);

        notificationFacade.notifyAuctionWon(WON, NOW.plusSeconds(10));

        verify(pushSender, after(500).never()).send(any(), anyList());
    }

    @Test
    void 푸시를_끈_묶음의_알림은_보내지_않지만_알림함에는_저장된다() {
        transactionTemplate.executeWithoutResult(status ->
                notificationSettingRepository.upsert(WINNER_ID, "BUY", false, NOW));

        notificationFacade.notifyAuctionWon(WON, NOW);

        verify(pushSender, timeout(3000)).send(받는_사람이(SELLER_ID), anyList());
        verify(pushSender, after(500).never()).send(받는_사람이(WINNER_ID), anyList());
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getType)
                .contains(AUCTION_WON_BUYER);
    }

    @Test
    void 발송이_느려도_알림_저장은_기다리지_않는다() {
        given(pushSender.send(any(), anyList())).willAnswer(invocation -> {
            Thread.sleep(2000);
            return List.of();
        });

        long start = System.currentTimeMillis();
        int saved = notificationFacade.notifyAuctionWon(WON, NOW);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(saved).isEqualTo(2);
        assertThat(elapsed).isLessThan(1000);
        // 한 이벤트의 알림 2건은 푸시 스레드 하나에서 차례로 보낸다(2초 + 2초).
        // 둘 다 이 테스트 안에서 호출될 때까지 기다려야 다음 테스트에 호출이 섞이지 않는다
        verify(pushSender, timeout(5000).times(2)).send(any(), anyList());
    }

    @Test
    void FCM이_무효_토큰이라고_응답하면_그_기기를_삭제하고_다른_기기는_남긴다() throws Exception {
        given(pushSender.send(any(), anyList())).willAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(1);
            return tokens.stream()
                    .map(token -> token.equals("token-winner") ? PushResult.invalidToken(token) : PushResult.success(token))
                    .toList();
        });

        notificationFacade.notifyAuctionWon(WON, NOW);

        될_때까지_기다린다(() -> notificationDeviceRepository.findAll().stream()
                .noneMatch(device -> device.getToken().equals("token-winner")));
        assertThat(notificationDeviceRepository.findAll())
                .extracting(device -> device.getToken())
                .containsExactly("token-seller");
    }

    @Test
    void 무효_토큰이_아닌_실패_응답이면_기기를_지우지_않는다() throws Exception {
        given(pushSender.send(any(), anyList())).willAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(1);
            return tokens.stream().map(PushResult::failed).toList();
        });

        notificationFacade.notifyAuctionWon(WON, NOW);

        verify(pushSender, timeout(3000).times(2)).send(any(), anyList());
        Thread.sleep(300);
        assertThat(notificationDeviceRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.findAll())
                .allMatch(n -> n.getPushedAt() == null);
    }
}
