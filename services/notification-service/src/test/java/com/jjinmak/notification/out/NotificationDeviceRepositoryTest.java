package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.NotificationDevice;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeviceRepositoryTest extends NotificationIntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final String TOKEN = "token-a";
    private static final Long ME = 7L;
    private static final Long OTHER = 9L;

    @Autowired NotificationDeviceRepository notificationDeviceRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        notificationDeviceRepository.deleteAllInBatch();
    }

    private void 등록한다(Long memberId, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status ->
                notificationDeviceRepository.upsert(memberId, TOKEN, "WEB", now));
    }

    @Test
    void 같은_토큰을_다른_회원이_등록하면_새로_생기지_않고_주인과_갱신_시각만_바뀐다() {
        등록한다(ME, NOW);

        등록한다(OTHER, NOW.plusHours(1));

        assertThat(notificationDeviceRepository.findAll()).singleElement().satisfies(device -> {
            assertThat(device.getMemberId()).isEqualTo(OTHER);
            assertThat(device.getCreatedAt()).isEqualTo(NOW);
            assertThat(device.getUpdatedAt()).isEqualTo(NOW.plusHours(1));
        });
    }

    @Test
    void 남의_토큰은_해제해도_삭제되지_않는다() {
        등록한다(ME, NOW);

        Integer deleted = transactionTemplate.execute(status ->
                notificationDeviceRepository.deleteByTokenAndMemberId(TOKEN, OTHER));

        assertThat(deleted).isZero();
        assertThat(notificationDeviceRepository.findAll())
                .extracting(NotificationDevice::getMemberId)
                .containsExactly(ME);
    }
}
