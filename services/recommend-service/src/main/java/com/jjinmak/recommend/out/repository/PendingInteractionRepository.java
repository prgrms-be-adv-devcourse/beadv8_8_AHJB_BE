package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.PendingInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingInteractionRepository extends JpaRepository<PendingInteraction, Long> {

    List<PendingInteraction> findAllByProductIdOrderByOccurredAt(Long productId);
}
