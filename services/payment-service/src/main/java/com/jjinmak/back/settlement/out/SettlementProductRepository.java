package com.jjinmak.back.settlement.out;

import com.jjinmak.back.settlement.domain.SettlementProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementProductRepository extends JpaRepository<SettlementProduct, Long> {
}