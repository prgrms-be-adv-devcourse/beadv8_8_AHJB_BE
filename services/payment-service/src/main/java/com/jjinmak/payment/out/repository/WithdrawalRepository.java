package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    Page<Withdrawal> findByHolderId(Long holderId, Pageable pageable);
}
