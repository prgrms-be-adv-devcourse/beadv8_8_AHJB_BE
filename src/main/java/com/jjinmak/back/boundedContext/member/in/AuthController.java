package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.boundedContext.member.app.MemberReadUseCase;
import com.jjinmak.back.boundedContext.member.app.MemberSignUpUseCase;
import com.jjinmak.back.boundedContext.member.in.dto.AvailabilityResponseDto;
import com.jjinmak.back.boundedContext.member.in.dto.MemberSignUpRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입, 로그인 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberSignUpUseCase memberSignUpUseCase;
    private final MemberReadUseCase memberReadUseCase;

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
}
