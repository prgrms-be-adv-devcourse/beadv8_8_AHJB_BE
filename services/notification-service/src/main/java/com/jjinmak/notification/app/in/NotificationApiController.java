package com.jjinmak.notification.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.notification.app.NotificationFacade;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.dto.NotificationListResponseDto;
import com.jjinmak.notification.dto.ReadAllResponseDto;
import com.jjinmak.notification.dto.UnreadCountResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "알림", description = "알림함 조회, 안 읽은 수, 읽음 처리 API")
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
}
