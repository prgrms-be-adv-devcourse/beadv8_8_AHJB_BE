package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    /** 내 출금 내역. 정렬은 Pageable 로 */
    Page<Withdrawal> findByHolderId(Long holderId, Pageable pageable);
}
