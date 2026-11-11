package com.jjinmak.notification.in;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationTargetType;
import com.jjinmak.notification.domain.NotificationType;
import com.jjinmak.notification.out.NotificationRepository;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationApiControllerTest extends NotificationIntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    private static final Long ME = 7L;
    private static final Long OTHER = 9L;

    @Autowired MockMvc mockMvc;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    private void 알림을_저장한다(Long receiverId, NotificationType type, long auctionId) {
        Notification notification = Notification.create(receiverId, type, "제목", "본문",
                NotificationTargetType.AUCTION, auctionId, "auction-" + auctionId, NOW);
        transactionTemplate.executeWithoutResult(status -> notificationRepository.insertIgnoringDuplicate(notification, NOW));
    }

    private List<Long> 내_알림_id_최신순() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getReceiverId().equals(ME))
                .map(Notification::getId)
                .sorted((a, b) -> Long.compare(b, a))
                .toList();
    }

    private Long 남의_알림_id() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getReceiverId().equals(OTHER))
                .findFirst().orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder 로그인(MockHttpServletRequestBuilder request, Long memberId) {
        return request.header("X-Member-Id", memberId).header("X-Member-Roles", "ROLE_BUYER");
    }

    @Test
    void 로그인_헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록은_내_알림만_최신순으로_보여준다() throws Exception {
        알림을_저장한다(ME, AUCTION_WON_BUYER, 1);
        알림을_저장한다(ME, AUCTION_WON_BUYER, 2);
        알림을_저장한다(OTHER, AUCTION_WON_SELLER, 2);
        List<Long> myIds = 내_알림_id_최신순();

        mockMvc.perform(로그인(get("/api/v1/notifications"), ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[*].notificationId")
                        .value(contains(myIds.get(0).intValue(), myIds.get(1).intValue())))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 커서로_다음_페이지를_이어서_조회한다() throws Exception {
        IntStream.rangeClosed(1, 3).forEach(i -> 알림을_저장한다(ME, AUCTION_WON_BUYER, i));
        List<Long> myIds = 내_알림_id_최신순();

        mockMvc.perform(로그인(get("/api/v1/notifications").param("size", "2"), ME))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(myIds.get(1).intValue()));

        mockMvc.perform(로그인(get("/api/v1/notifications")
                        .param("size", "2").param("cursor", String.valueOf(myIds.get(1))), ME))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(myIds.get(2).intValue()))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void size는_최대_50건으로_제한한다() throws Exception {
        IntStream.rangeClosed(1, 51).forEach(i -> 알림을_저장한다(ME, AUCTION_WON_BUYER, i));

        mockMvc.perform(로그인(get("/api/v1/notifications").param("size", "100"), ME))
                .andExpect(jsonPath("$.data.content.length()").value(50))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 묶음을_지정하면_그_묶음만_보여준다() throws Exception {
        알림을_저장한다(ME, AUCTION_WON_BUYER, 1);
        알림을_저장한다(ME, AUCTION_WON_SELLER, 2);

        mockMvc.perform(로그인(get("/api/v1/notifications").param("category", "SELL"), ME))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[*].category").value(everyItem(is("SELL"))));
    }

    @Test
    void 안_읽은_알림_수는_내_알림만_센다() throws Exception {
        알림을_저장한다(ME, AUCTION_WON_BUYER, 1);
        알림을_저장한다(ME, AUCTION_WON_BUYER, 2);
        알림을_저장한다(OTHER, AUCTION_WON_SELLER, 1);

        mockMvc.perform(로그인(get("/api/v1/notifications/unread-count"), ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void 읽음_처리하면_읽음이_되고_다시_요청해도_성공한다() throws Exception {
        알림을_저장한다(ME, AUCTION_WON_BUYER, 1);
        Long id = 내_알림_id_최신순().getFirst();

        mockMvc.perform(로그인(patch("/api/v1/notifications/{id}/read", id), ME))
                .andExpect(status().isOk());
        mockMvc.perform(로그인(patch("/api/v1/notifications/{id}/read", id), ME))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findById(id).orElseThrow().isRead()).isTrue();
        mockMvc.perform(로그인(get("/api/v1/notifications/unread-count"), ME))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 남의_알림은_없는_알림과_같이_404() throws Exception {
        알림을_저장한다(OTHER, AUCTION_WON_SELLER, 1);
        Long othersId = 남의_알림_id();

        mockMvc.perform(로그인(patch("/api/v1/notifications/{id}/read", othersId), ME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION001"));
        mockMvc.perform(로그인(patch("/api/v1/notifications/{id}/read", 999_999L), ME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION001"));

        assertThat(notificationRepository.findById(othersId).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 전체_읽음은_내_안_읽은_알림만_읽음_처리한다() throws Exception {
        알림을_저장한다(ME, AUCTION_WON_BUYER, 1);
        알림을_저장한다(ME, AUCTION_WON_BUYER, 2);
        알림을_저장한다(OTHER, AUCTION_WON_SELLER, 1);

        mockMvc.perform(로그인(patch("/api/v1/notifications/read-all"), ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readCount").value(2));

        mockMvc.perform(로그인(get("/api/v1/notifications/unread-count"), ME))
                .andExpect(jsonPath("$.data.count").value(0));
        mockMvc.perform(로그인(get("/api/v1/notifications/unread-count"), OTHER))
                .andExpect(jsonPath("$.data.count").value(1));
    }
}
