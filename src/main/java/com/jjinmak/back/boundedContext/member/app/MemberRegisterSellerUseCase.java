package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.Seller;
import com.jjinmak.back.boundedContext.member.in.dto.RegisterSellerRequestDto;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.boundedContext.member.out.SellerRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberRegisterSellerUseCase {

    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void registerSeller(Long memberId, RegisterSellerRequestDto request){

        Member member = memberRepository.findById(memberId)
                .orElseThrow(()->new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        member.registerAsSeller();

        Seller seller = Seller.create(memberId, request.bank(), request.accountNumber(), request.accountHolder());

        sellerRepository.save(seller);
    }
}
