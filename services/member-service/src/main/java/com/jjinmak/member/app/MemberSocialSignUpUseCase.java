package com.jjinmak.member.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.member.domain.Address;
import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.SocialAccount;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.exception.MemberErrorCode;
import com.jjinmak.member.in.dto.AddressRequestDto;
import com.jjinmak.member.in.dto.KakaoSignUpRequestDto;
import com.jjinmak.member.out.AddressRepository;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSocialSignUpUseCase {

    private final MemberRepository memberRepository;
    private final AddressRepository addressRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 추가 정보 입력이 끝난 시점에 회원·소셜 계정 연결·기본 배송지를 한 번에 만든다.
     * 미완성 회원을 미리 만들지 않는 이유: MemberCreatedEvent 가 다른 서비스로 퍼져, 중간 이탈한 회원 데이터가 각 서비스에 남기 때문.
     * 이미 가입된 이메일이면 계정을 자동 연동하지 않고 거부한다(이메일만으로 합치면 계정 탈취 위험이 있다).
     */
    @Transactional
    public Member signUp(SignupTicket ticket, KakaoSignUpRequestDto request) {

        if (socialAccountRepository.existsByProviderAndProviderUserId(ticket.provider(), ticket.providerUserId())) {
            throw new BusinessException(AuthErrorCode.ALREADY_LINKED_SOCIAL_ACCOUNT);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        Member member = Member.createSocial(request.email(), request.nickname(), request.username(),
                request.phone(), request.birthDate());

        memberRepository.save(member);

        socialAccountRepository.save(
                SocialAccount.link(member.getId(), ticket.provider(), ticket.providerUserId()));

        AddressRequestDto addressRequest = request.address();

        addressRepository.save(Address.createDefault(
                member.getId(),
                addressRequest.alias(),
                addressRequest.recipient(),
                addressRequest.recipientPhone(),
                addressRequest.zipcode(),
                addressRequest.address(),
                addressRequest.addressDetail()
        ));

        eventPublisher.publishEvent(new MemberCreatedEvent(
                new MemberDto(member.getId(), member.getUuid(), member.getNickname())
        ));

        return member;
    }
}
