package com.jjinmak.payment.app.usecase;

import com.jjinmak.payment.app.dto.PageDto;
import com.jjinmak.payment.app.dto.WithdrawalDto;
import com.jjinmak.payment.out.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletGetWithdrawalsUseCase {

    private final WithdrawalRepository withdrawalRepository;

    public PageDto<WithdrawalDto> list(Long holderId, int page, int size) {
        return PageDto.of(withdrawalRepository.findByHolderId(holderId, PageDto.latest(page, size))
                .map(WithdrawalDto::from));
    }
}
