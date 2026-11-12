package com.jjinmak.notification.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.notification.app.NotificationFacade;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.dto.DeviceRegisterRequestDto;
import com.jjinmak.notification.dto.NotificationListResponseDto;
import com.jjinmak.notification.dto.NotificationSettingsResponseDto;
import com.jjinmak.notification.dto.NotificationSettingsUpdateRequestDto;
import com.jjinmak.notification.dto.ReadAllResponseDto;
import com.jjinmak.notification.dto.UnreadCountResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "알림", description = "알림함 조회, 안 읽은 수, 읽음 처리, 푸시 기기 등록, 수신 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationApiController {

    private final NotificationFacade notificationFacade;

    @Operation(summary = "내 알림 목록", description = "최신순 커서 조회. category가 없으면 전체, size 기본 20·최대 50")
    @GetMapping
    public ResponseEntity<RsData<NotificationListResponseDto>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        NotificationListResponseDto response = notificationFacade.list(principal.memberId(), category, cursor, size);
        return ResponseEntity.ok(new RsData<>(true, "알림 목록 조회에 성공했습니다.", response));
    }

    @Operation(summary = "안 읽은 알림 수")
    @GetMapping("/unread-count")
    public ResponseEntity<RsData<UnreadCountResponseDto>> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        long count = notificationFacade.unreadCount(principal.memberId());
        return ResponseEntity.ok(new RsData<>(true, "안 읽은 알림 수 조회에 성공했습니다.", new UnreadCountResponseDto(count)));
    }

    @Operation(summary = "알림 읽음", description = "본인 알림만 가능. 이미 읽은 알림도 성공으로 응답")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<RsData<Void>> read(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long notificationId
    ) {
        notificationFacade.read(principal.memberId(), notificationId, LocalDateTime.now());
        return ResponseEntity.ok(new RsData<>(true, "알림을 읽음 처리했습니다.", null));
    }

    @Operation(summary = "전체 읽음")
    @PatchMapping("/read-all")
    public ResponseEntity<RsData<ReadAllResponseDto>> readAll(@AuthenticationPrincipal AuthPrincipal principal) {
        int readCount = notificationFacade.readAll(principal.memberId(), LocalDateTime.now());
        return ResponseEntity.ok(new RsData<>(true, "모든 알림을 읽음 처리했습니다.", new ReadAllResponseDto(readCount)));
    }

    @Operation(summary = "푸시 기기 등록", description = "브라우저 알림 허용 후 받은 FCM 토큰을 등록. 로그인할 때마다 호출. 같은 토큰이 있으면 주인과 갱신 시각을 바꾼다")
    @PostMapping("/devices")
    public ResponseEntity<RsData<Void>> registerDevice(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody DeviceRegisterRequestDto request
    ) {
        notificationFacade.registerDevice(principal.memberId(), request.token(), LocalDateTime.now());
        return ResponseEntity.ok(new RsData<>(true, "푸시 기기를 등록했습니다.", null));
    }

    @Operation(summary = "푸시 기기 해제", description = "로그아웃할 때 호출. 본인 토큰만 삭제되며, 없는 토큰이어도 성공으로 응답")
    @DeleteMapping("/devices/{token}")
    public ResponseEntity<RsData<Void>> unregisterDevice(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String token
    ) {
        notificationFacade.unregisterDevice(principal.memberId(), token);
        return ResponseEntity.ok(new RsData<>(true, "푸시 기기를 해제했습니다.", null));
    }

    @Operation(summary = "수신 설정 조회", description = "묶음(BID, BUY, SELL, INTEREST)별 푸시 켬/끔. 설정한 적 없는 묶음은 켜짐. 계정 알림은 항상 켜짐이라 목록에 없음")
    @GetMapping("/settings")
    public ResponseEntity<RsData<NotificationSettingsResponseDto>> getSettings(@AuthenticationPrincipal AuthPrincipal principal) {
        NotificationSettingsResponseDto response = notificationFacade.getSettings(principal.memberId());
        return ResponseEntity.ok(new RsData<>(true, "수신 설정 조회에 성공했습니다.", response));
    }

    @Operation(summary = "수신 설정 변경", description = "바꿀 묶음만 보내도 된다. 응답은 변경 후 전체 설정. 끄면 푸시·사이트 안 알림이 오지 않고 알림함에만 남는다. ACCOUNT는 400(NOTIFICATION002)")
    @PutMapping("/settings")
    public ResponseEntity<RsData<NotificationSettingsResponseDto>> updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody NotificationSettingsUpdateRequestDto request
    ) {
        NotificationSettingsResponseDto response =
                notificationFacade.updateSettings(principal.memberId(), request.settings(), LocalDateTime.now());
        return ResponseEntity.ok(new RsData<>(true, "수신 설정을 변경했습니다.", response));
    }
}