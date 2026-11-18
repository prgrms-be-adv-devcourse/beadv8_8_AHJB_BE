package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.boundedContext.payment.out.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 내 출금 내역 조회. 최신순, 한 페이지 최대 50건 */
@Service
@RequiredArgsConstructor
public class WalletGetWithdrawalsUseCase {

    private final WithdrawalRepository withdrawalRepository;

    public PageDto<WithdrawalDto> list(Long holderId, int page, int size) {
        return PageDto.of(withdrawalRepository.findByHolderId(holderId, PageDto.latest(page, size))
                .map(WithdrawalDto::from));
    }
}
