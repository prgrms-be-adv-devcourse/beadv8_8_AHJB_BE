package com.jjinmak.boundedcontext.delivery.out.repository;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:deliveryoutboxrepository;MODE=PostgreSQL")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeliveryOutboxRepositoryTest {

    @Autowired
    DeliveryOutboxRepository deliveryOutboxRepository;

    @Test
    @DisplayName("발행 시각이 없는 아웃박스만 id 순서로 조회한다")
    void findUnpublished_returnsOnlyUnpublishedInIdOrder() {
        DeliveryOutbox first = deliveryOutboxRepository.save(new DeliveryOutbox("delivery.created", "1", "{}"));
        DeliveryOutbox published = new DeliveryOutbox("delivery.created", "2", "{}");
        published.published(LocalDateTime.now());
        deliveryOutboxRepository.save(published);
        DeliveryOutbox third = deliveryOutboxRepository.save(new DeliveryOutbox("delivery.status-updated", "3", "{}"));

        assertThat(deliveryOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(DeliveryOutbox::getId)
                .containsExactly(first.getId(), third.getId());
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스는 한 번에 id가 작은 순서로 100건까지만 조회한다")
    void findUnpublished_returnsAtMost100() {
        List<Long> savedIds = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> deliveryOutboxRepository.save(new DeliveryOutbox("delivery.created", String.valueOf(i), "{}")).getId())
                .toList();

        assertThat(deliveryOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(DeliveryOutbox::getId)
                .containsExactlyElementsOf(savedIds.subList(0, 100));
    }
}
