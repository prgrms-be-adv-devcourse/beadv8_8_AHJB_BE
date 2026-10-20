package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.boundedContext.member.app.MemberReadUseCase;
import com.jjinmak.back.boundedContext.member.app.MemberRegisterSellerUseCase;
import com.jjinmak.back.boundedContext.member.in.dto.MemberSummaryResponseDto;
import com.jjinmak.back.boundedContext.member.in.dto.RegisterSellerRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자", description = "회원 정보, 계정 상태 변경 관련")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberRegisterSellerUseCase memberRegisterSellerUseCase;
    private final MemberReadUseCase memberReadUseCase;


//  todo: 계정 정지, 차단 기능 구현 후 테스트 필요
    @Operation(
            summary = "판매자 등록",
            description = "현재 인증된 사용자 ID와 요청 정보를 통해 판매자 등록을 진행합니다"
    )
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/me/seller")
    public ResponseEntity<Void> registerSeller(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody RegisterSellerRequestDto request){
        memberRegisterSellerUseCase.registerSeller(principal.memberId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "내 정보 요약 조회",
            description = "로그인한 회원의 닉네임, 판매자 여부, 정지 여부를 반환합니다."
    )
    @GetMapping("/me/summary")
    public ResponseEntity<RsData<MemberSummaryResponseDto>> getMySummary(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(new RsData<>(memberReadUseCase.getSummary(principal.memberId())));
    }
}
