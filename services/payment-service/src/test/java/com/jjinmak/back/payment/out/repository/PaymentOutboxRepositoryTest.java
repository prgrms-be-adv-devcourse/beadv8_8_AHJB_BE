package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentOutboxRepositoryTest {

    @Autowired
    PaymentOutboxRepository paymentOutboxRepository;

    @Test
    @DisplayName("발행 시각이 없는 아웃박스만 id 순서로 조회한다")
    void findUnpublished_returnsOnlyUnpublishedInIdOrder() {
        PaymentOutbox first = paymentOutboxRepository.save(new PaymentOutbox("payment.payment-paid", "1", "{}"));
        PaymentOutbox published = new PaymentOutbox("payment.payment-paid", "2", "{}");
        published.published(LocalDateTime.now());
        paymentOutboxRepository.save(published);
        PaymentOutbox third = paymentOutboxRepository.save(new PaymentOutbox("payment.payment-paid", "3", "{}"));

        assertThat(paymentOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(PaymentOutbox::getId)
                .containsExactly(first.getId(), third.getId());
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스는 한 번에 id가 작은 순서로 100건까지만 조회한다")
    void findUnpublished_returnsAtMost100() {
        List<Long> savedIds = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> paymentOutboxRepository.save(new PaymentOutbox("payment.payment-paid", String.valueOf(i), "{}")).getId())
                .toList();

        assertThat(paymentOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(PaymentOutbox::getId)
                .containsExactlyElementsOf(savedIds.subList(0, 100));
    }
}
