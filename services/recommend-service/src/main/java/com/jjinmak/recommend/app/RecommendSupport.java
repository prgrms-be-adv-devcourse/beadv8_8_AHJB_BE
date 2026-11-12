package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.common.exception.CommonErrorCode.*;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendSupport {

    private final RecommendMemberRepository recommendMemberRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;
    private final RecommendProductRepository recommendProductRepository;

    public RecommendMember findMemberById(Long id){
        return recommendMemberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public RecommendAuction findAuctionById(Long id){
        return recommendAuctionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));
    }

    public RecommendProduct findProductById(Long id){
        return recommendProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));
    }
}
