package com.jjinmak.member.app;

import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.Seller;
import com.jjinmak.member.in.dto.RegisterSellerRequestDto;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.SellerRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
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
