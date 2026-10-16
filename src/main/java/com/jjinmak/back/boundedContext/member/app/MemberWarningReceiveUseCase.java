package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.*;
import com.jjinmak.back.boundedContext.member.exception.MemberErrorCode;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.boundedContext.member.out.MemberSanctionRepository;
import com.jjinmak.back.boundedContext.member.out.MemberWarningRepository;
import com.jjinmak.back.boundedContext.member.out.RefreshTokenRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import com.jjinmak.back.shared.member.dto.MemberSanctionDto;
import com.jjinmak.back.shared.member.dto.MemberWarningDto;
import com.jjinmak.back.shared.member.event.MemberSanctionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberWarningReceiveUseCase {

    private final MemberWarningRepository memberWarningRepository;
    private final MemberSanctionRepository memberSanctionRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void receiveWarning(MemberWarningDto warningDto) {

        // 동일한 원인 사건으로 이미 경고가 기록된 경우 중복 처리하지 않는다
        if (memberWarningRepository.existsBySourceContextAndSourceId(
                warningDto.sourceContext(), warningDto.sourceId())) {
            return;
        }

        memberWarningRepository.save(MemberWarning.from(warningDto));
        int warningCount = memberWarningRepository.countByMemberId(warningDto.memberId());

        Member member = memberRepository.findById(warningDto.memberId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        // 새 제재가 기존 미해제 제재를 대체하기 때문에 회원당 미해제 제재가 하나만 유지되도록 정리한다
        memberSanctionRepository.findAllByMemberIdAndReleasedAtIsNull(warningDto.memberId())
                .forEach(MemberSanction::release);

        MemberSanction sanction = MemberSanction.sanctionFrom(warningDto, warningCount);

        if (sanction.getSanctionType() == SanctionType.SUSPENDED) {
            member.suspend();
        }
        if (sanction.getSanctionType() == SanctionType.BAN) {
            member.ban();
        }

        memberSanctionRepository.save(sanction);

        List<RefreshToken> tokenList = refreshTokenRepository.findAllByMemberIdAndRevokedFalse(member.getId());
        tokenList.forEach(RefreshToken::revoke);

        eventPublisher.publishEvent(new MemberSanctionEvent(
                new MemberSanctionDto(sanction.getMemberId())
        ));
    }
}