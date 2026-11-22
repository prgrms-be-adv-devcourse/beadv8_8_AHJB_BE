package com.jjinmak.back.order.out;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderOutboxRepositoryTest {

    @Autowired
    OrderOutboxRepository orderOutboxRepository;

    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("발행 시각이 없는 아웃박스만 id 순서로 조회한다")
    void findUnpublished_returnsOnlyUnpublishedInIdOrder() {
        OrderOutbox first = orderOutboxRepository.save(new OrderOutbox("order.send-product", "1", "{}"));
        OrderOutbox published = new OrderOutbox("order.send-product", "2", "{}");
        published.published(LocalDateTime.now());
        orderOutboxRepository.save(published);
        OrderOutbox third = orderOutboxRepository.save(new OrderOutbox("order.send-product", "3", "{}"));

        assertThat(orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(OrderOutbox::getId)
                .containsExactly(first.getId(), third.getId());
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스는 한 번에 id가 작은 순서로 100건까지만 조회한다")
    void findUnpublished_returnsAtMost100() {
        List<Long> savedIds = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> orderOutboxRepository.save(new OrderOutbox("order.send-product", String.valueOf(i), "{}")).getId())
                .toList();

        assertThat(orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(OrderOutbox::getId)
                .containsExactlyElementsOf(savedIds.subList(0, 100));
    }

    @Test
    @DisplayName("지정한 id의 아웃박스만 같은 시각으로 발행 표시하고, 수정 시각도 그 시각으로 바꾼다")
    void markPublished_updatesOnlyGivenIds() {
        OrderOutbox first = orderOutboxRepository.save(new OrderOutbox("order.send-product", "1", "{}"));
        OrderOutbox second = orderOutboxRepository.save(new OrderOutbox("order.send-product", "2", "{}"));
        OrderOutbox third = orderOutboxRepository.save(new OrderOutbox("order.send-product", "3", "{}"));
        LocalDateTime publishedAt = LocalDateTime.of(2026, 11, 22, 10, 0);
        em.flush();

        orderOutboxRepository.markPublished(List.of(first.getId(), third.getId()), publishedAt);
        em.clear();

        assertThat(orderOutboxRepository.findById(first.getId())).get()
                .satisfies(outbox -> {
                    assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
                    assertThat(outbox.getUpdatedAt()).isEqualTo(publishedAt);
                });
        assertThat(orderOutboxRepository.findById(third.getId())).get()
                .satisfies(outbox -> {
                    assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
                    assertThat(outbox.getUpdatedAt()).isEqualTo(publishedAt);
                });
        assertThat(orderOutboxRepository.findById(second.getId())).get()
                .satisfies(outbox -> assertThat(outbox.getPublishedAt()).isNull());
    }
}
