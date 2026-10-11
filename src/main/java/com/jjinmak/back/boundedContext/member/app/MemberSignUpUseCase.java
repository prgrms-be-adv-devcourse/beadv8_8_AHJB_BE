package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Address;
import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.exception.MemberErrorCode;
import com.jjinmak.back.boundedContext.member.in.dto.AddressRequestDto;
import com.jjinmak.back.boundedContext.member.in.dto.MemberSignUpRequestDto;
import com.jjinmak.back.boundedContext.member.out.AddressRepository;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberSignUpUseCase {

    private final MemberRepository memberRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void signUp(MemberSignUpRequestDto memberRequest){

        if (memberRepository.existsByEmail(memberRequest.email())) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if(memberRepository.existsByNickname(memberRequest.nickname())){
            throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(memberRequest.password());

        Member member = Member.create(memberRequest.email(), encodedPassword, memberRequest.nickname()
                , memberRequest.username(), memberRequest.phone(), memberRequest.birthDate());

        memberRepository.save(member);

        AddressRequestDto addressRequest = memberRequest.address();

        Address address = Address.createDefault(
                member.getId(),
                addressRequest.alias(),
                addressRequest.recipient(),
                addressRequest.recipientPhone(),
                addressRequest.zipcode(),
                addressRequest.address(),
                addressRequest.addressDetail()
        );

        addressRepository.save(address);

        eventPublisher.publishEvent(new MemberCreatedEvent(
                new MemberDto(member.getId(), member.getUuid(), member.getNickname())
        ));

    }

}
