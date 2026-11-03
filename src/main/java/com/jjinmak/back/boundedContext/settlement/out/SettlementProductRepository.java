package com.jjinmak.back.boundedContext.settlement.out;

import com.jjinmak.back.boundedContext.settlement.domain.SettlementProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementProductRepository extends JpaRepository<SettlementProduct, Long> {
}