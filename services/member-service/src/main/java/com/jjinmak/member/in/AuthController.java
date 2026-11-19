package com.jjinmak.member.in;

import com.jjinmak.member.app.*;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.in.dto.*;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.rsData.RsData;
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

    @Operation(
            summary = "카카오 로그인",
            description = """
                    프론트가 카카오에서 받은 인가 코드로 로그인합니다.
                    - 이미 가입된 카카오 계정: status=LOGIN, 액세스 토큰 반환, 리프레시 토큰은 HttpOnly 쿠키로 전달
                    - 처음 로그인한 카카오 계정: status=SIGNUP_REQUIRED, 가입 대기 토큰(10분), 인증된 카카오 이메일, 카카오 닉네임(없으면 각각 null) 반환 → 추가 정보 입력 후 가입 API 호출
                    - 인증된 카카오 이메일이 이미 이메일 회원으로 가입되어 있으면 AUTH113(409)
                    """
    )
    @PostMapping("/oauth/kakao")
    public ResponseEntity<RsData<SocialLoginResponseDto>> kakaoLogin(@Valid @RequestBody KakaoLoginRequestDto request) {
        SocialLoginResult result = authFacade.loginWithKakao(request.code());

        if (result.isSignupRequired()) {
            return ResponseEntity.ok(new RsData<>(SocialLoginResponseDto.signupRequired(result.signupToken(), result.email(), result.nickname())));
        }

        AuthResult auth = result.authResult();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(auth.refreshToken()).toString())
                .body(new RsData<>(SocialLoginResponseDto.loggedIn(auth.accessToken(), auth.suspended())));
    }

    @Operation(
            summary = "카카오 회원가입",
            description = "가입 대기 토큰과 추가 정보로 가입을 완료하고 바로 로그인합니다. 토큰에 인증된 카카오 이메일이 있으면 요청의 email 은 무시하고 그 이메일로 가입합니다. 이미 사용 중인 이메일이면 가입이 거부됩니다(계정 자동 연동 없음)."
    )
    @PostMapping("/oauth/kakao/signup")
    public ResponseEntity<RsData<TokenResponseDto>> kakaoSignUp(@Valid @RequestBody KakaoSignUpRequestDto request) {
        AuthResult result = authFacade.signUpWithKakao(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString())
                .body(new RsData<>(new TokenResponseDto(result.accessToken(), result.suspended())));
    }

    private ResponseEntity<RsData<TokenResponseDto>> tokenResponse(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString())
                .body(new RsData<>(new TokenResponseDto(result.accessToken(), result.suspended())));
    }
}