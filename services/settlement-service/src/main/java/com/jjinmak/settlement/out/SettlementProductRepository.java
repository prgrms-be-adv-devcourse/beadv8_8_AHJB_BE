package com.jjinmak.settlement.out;

import com.jjinmak.settlement.domain.SettlementProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementProductRepository extends JpaRepository<SettlementProduct, Long> {
}