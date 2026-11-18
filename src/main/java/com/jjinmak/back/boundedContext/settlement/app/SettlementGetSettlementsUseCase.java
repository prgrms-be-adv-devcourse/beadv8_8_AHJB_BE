package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementGetSettlementsUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final SettlementRepository settlementRepository;

    /**
     * 로그인한 판매자의 정산서 목록을 최신순으로 조회한다. status가 없으면 전체를 조회한다.
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @param status - PENDING(정산 예정) / COMPLETED(정산 완료), null이면 전체
     * @param page - 페이지 번호 (0부터)
     * @param size - 페이지 크기 (1~50)
     * @return Page<Settlement> - 정산서 페이지
     */
    public Page<Settlement> getSettlements(Long memberId, SettlementStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id")
        );

        if (status == null) {
            return settlementRepository.findBySellerId(memberId, pageable);
        }
        return settlementRepository.findBySellerIdAndStatus(memberId, status, pageable);
    }
}
