package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {
}
