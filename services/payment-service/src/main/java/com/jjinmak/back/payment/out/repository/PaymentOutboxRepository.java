package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, Long> {

    List<PaymentOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
