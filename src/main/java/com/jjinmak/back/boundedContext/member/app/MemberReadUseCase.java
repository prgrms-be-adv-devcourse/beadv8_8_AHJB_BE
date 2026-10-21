package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.AccountStatus;
import com.jjinmak.back.boundedContext.member.domain.Address;
import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.MemberSanction;
import com.jjinmak.back.boundedContext.member.exception.MemberErrorCode;
import com.jjinmak.back.boundedContext.member.in.dto.AccountStatusResponse;
import com.jjinmak.back.boundedContext.member.in.dto.MemberDetailResponseDto;
import com.jjinmak.back.boundedContext.member.in.dto.MemberSummaryResponseDto;
import com.jjinmak.back.boundedContext.member.in.dto.SellerInfoResponseDto;
import com.jjinmak.back.boundedContext.member.in.dto.WarningResponse;
import com.jjinmak.back.boundedContext.member.out.AddressRepository;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.boundedContext.member.out.MemberSanctionRepository;
import com.jjinmak.back.boundedContext.member.out.MemberWarningRepository;
import com.jjinmak.back.boundedContext.member.out.SellerRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberReadUseCase {

    private static final int VISIBLE_ACCOUNT_DIGITS = 4;

    private final MemberRepository memberRepository;
    private final AddressRepository addressRepository;
    private final SellerRepository sellerRepository;
    private final MemberSanctionRepository sanctionRepository;
    private final MemberWarningRepository warningRepository;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname){
        return !memberRepository.existsByNickname(nickname);
    }

    @Transactional(readOnly = true)
    public MemberSummaryResponseDto getSummary(Long memberId) {
        Member member = findMember(memberId);

        return new MemberSummaryResponseDto(member.getNickname(), member.isSeller(), member.isSuspended());
    }

    @Transactional(readOnly = true)
    public MemberDetailResponseDto getMemberDetail(Long memberId){
        Member member = findMember(memberId);

        Address address = addressRepository.findAddressByMemberIdAndIsDefaultIsTrue(memberId)
                .orElseThrow(()->new BusinessException(MemberErrorCode.DEFAULT_ADDRESS_NOT_FOUND));


        return new MemberDetailResponseDto(
                member.getNickname(),
                member.getEmail(),
                member.getUsername(),
                member.getPhone(),
                member.getBirthDate(),
                address.getAlias (),
                address.getAddress()+" "+address.getAddressDetail(),
                member.getCreatedAt().toLocalDate()
        );
    }

    @Transactional(readOnly = true)
    public SellerInfoResponseDto getSellerInfo(Long memberId){
        return sellerRepository.findByMemberId(memberId)
                .map(seller -> new SellerInfoResponseDto(
                        seller.getBank(),
                        maskAccountNumber(seller.getAccountNumber()),
                        seller.getAccountHolder(),
                        seller.getCreatedAt().toLocalDate()
                ))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AccountStatusResponse getAccountStatus(Long memberId){
        LocalDateTime now = LocalDateTime.now();
        Member member = findMember(memberId);

        List<WarningResponse> warnings = warningRepository.findAllByMemberIdOrderByOccurredAtDesc(memberId)
                .stream()
                .map(warning -> new WarningResponse(
                        warning.getId(),
                        warning.getReason(),
                        warning.getSourceId(),
                        warning.getOccurredAt()
                ))
                .toList();

        AccountStatus status = member.getStatus();
        LocalDateTime suspensionExpiresAt = null;

        if (member.isSuspended()) {
            MemberSanction sanction = sanctionRepository.findActiveByMemberId(memberId).orElse(null);

            // 기간이 지났지만 아직 해제 처리되지 않은 정지는 해제된 것으로 응답
            // 실제 해제는 로그인·재발급 시점에 반영
            if (sanction != null && sanction.isExpired(now)) {
                status = AccountStatus.ACTIVE;
            } else if (sanction != null) {
                suspensionExpiresAt = sanction.getExpiredAt();
            }
        }

        return new AccountStatusResponse(
                toStatusValue(status),
                warnings.size(),
                suspensionExpiresAt,
                warnings
        );
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));
    }

    private String toStatusValue(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case SUSPENDED -> "SUSPENDED";
            case BANNED -> "BANNED";
        };
    }

    private String maskAccountNumber(String number){
        int maskedLength = number.length() - VISIBLE_ACCOUNT_DIGITS;
        return "*".repeat(maskedLength) + number.substring(maskedLength);
    }
}