package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    /** 내 출금 내역. 정렬은 Pageable 로 */
    Page<Withdrawal> findByHolderId(Long holderId, Pageable pageable);
}
