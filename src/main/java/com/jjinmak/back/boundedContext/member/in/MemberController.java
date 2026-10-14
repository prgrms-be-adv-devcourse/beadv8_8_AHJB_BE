package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.boundedContext.member.app.MemberRegisterSellerUseCase;
import com.jjinmak.back.boundedContext.member.in.dto.RegisterSellerRequestDto;
import com.jjinmak.back.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "회원 정보, 계정 상태 변경 관련")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberRegisterSellerUseCase memberRegisterSellerUseCase;


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
}
