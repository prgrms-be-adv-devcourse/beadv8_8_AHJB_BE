package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.boundedContext.member.app.AuthFacade;
import com.jjinmak.back.boundedContext.member.app.AuthResult;
import com.jjinmak.back.boundedContext.member.app.MemberLogoutUseCase;
import com.jjinmak.back.boundedContext.member.app.MemberReadUseCase;
import com.jjinmak.back.boundedContext.member.app.MemberSignUpUseCase;
import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.in.dto.*;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입, 로그인 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberSignUpUseCase memberSignUpUseCase;
    private final MemberReadUseCase memberReadUseCase;
    private final MemberLogoutUseCase memberLogoutUseCase;
    private final AuthFacade authFacade;
    private final RefreshTokenCookieManager refreshTokenCookieManager;
    private final OriginValidator originValidator;

    @Operation(
            summary = "회원가입",
            description = "이메일 기반으로 회원가입을 진행합니다. 가입 시 입력한 배송지는 기본 배송지로 지정됩니다."
    )
    @PostMapping("/signup")
    public ResponseEntity<Void> signUp(@Valid @RequestBody MemberSignUpRequestDto request) {
        memberSignUpUseCase.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "로그인",
            description = "등록한 이메일, 비밀번호로 로그인합니다. 리프레시 토큰은 HttpOnly 쿠키로 전달됩니다."
    )
    @PostMapping("/login")
    public ResponseEntity<RsData<TokenResponseDto>> login(@Valid @RequestBody LoginRequestDto request){
        AuthResult result = authFacade.login(request.email(), request.password());
        return tokenResponse(result);
    }

    @Operation(
            summary = "이메일 중복 확인",
            description = "입력한 이메일의 사용 가능 여부를 반환합니다. 사용중인 이메일이면 false를 반환합니다."
    )
    @GetMapping("/email-availability")
    public ResponseEntity<RsData<AvailabilityResponseDto>> checkEmail(
            @Parameter(description = "확인할 이메일", example = "user@example.com")
            @RequestParam @Email @NotBlank String email
    ) {
        boolean available = memberReadUseCase.isEmailAvailable(email);

        return ResponseEntity.ok(new RsData<>(new AvailabilityResponseDto(available)));
    }

    @Operation(
            summary = "닉네임 중복 확인",
            description = "입력한 닉네임의 사용 가능 여부를 반환합니다. 사용중인 닉네임이면 false를 반환합니다."
    )
    @GetMapping("/nickname-availability")
    public ResponseEntity<RsData<AvailabilityResponseDto>> checkNickname(
            @Parameter(description = "확인할 닉네임", example = "TestUser123")
            @RequestParam @NotBlank @Size(max = 50) String nickname
    ) {
        boolean available = memberReadUseCase.isNicknameAvailable(nickname);

        return ResponseEntity.ok(new RsData<>(new AvailabilityResponseDto(available)));
    }

    @Operation(
            summary = "로그아웃",
            description = "쿠키의 리프레시 토큰을 무효화하고 쿠키를 삭제합니다."
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request){
        originValidator.validate(request);

        refreshTokenCookieManager.resolve(request)
                .ifPresent(memberLogoutUseCase::revokeRefreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.expire().toString())
                .build();
    }

    @Operation(
            summary = "토큰 재발급",
            description = "쿠키의 리프레시 토큰으로 액세스 토큰과 리프레시 토큰을 재발급합니다."
    )
    @PostMapping("/reissue")
    public ResponseEntity<RsData<TokenResponseDto>> reissue(HttpServletRequest request){
        originValidator.validate(request);

        String refreshToken = refreshTokenCookieManager.resolve(request)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        AuthResult result = authFacade.reissue(refreshToken);
        return tokenResponse(result);
    }

    private ResponseEntity<RsData<TokenResponseDto>> tokenResponse(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString())
                .body(new RsData<>(new TokenResponseDto(result.accessToken(), result.suspended())));
    }
}