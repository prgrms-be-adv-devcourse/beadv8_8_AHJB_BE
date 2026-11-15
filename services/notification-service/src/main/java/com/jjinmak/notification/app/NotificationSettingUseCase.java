package com.jjinmak.notification.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.domain.NotificationSetting;
import com.jjinmak.notification.dto.NotificationSettingItemDto;
import com.jjinmak.notification.dto.NotificationSettingsResponseDto;
import com.jjinmak.notification.dto.NotificationSettingsUpdateRequestDto;
import com.jjinmak.notification.out.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jjinmak.notification.exception.NotificationErrorCode.PUSH_SETTING_NOT_ALLOWED;

@Service
@RequiredArgsConstructor
public class NotificationSettingUseCase {

    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationSettingsResponseDto get(Long memberId) {
        Map<NotificationCategory, Boolean> saved = notificationSettingRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(NotificationSetting::getCategory, NotificationSetting::isPushEnabled));

        List<NotificationSettingItemDto> settings = NotificationCategory.pushConfigurables().stream()
                .map(category -> new NotificationSettingItemDto(category, saved.getOrDefault(category, true)))
                .toList();
        return new NotificationSettingsResponseDto(settings);
    }

    public NotificationSettingsResponseDto update(Long memberId,
                                                  List<NotificationSettingsUpdateRequestDto.Item> items,
                                                  LocalDateTime now) {
        boolean hasNotConfigurable = items.stream()
                .anyMatch(item -> !item.category().isPushConfigurable());
        if (hasNotConfigurable) {
            throw new BusinessException(PUSH_SETTING_NOT_ALLOWED);
        }

        for (NotificationSettingsUpdateRequestDto.Item item : items) {
            notificationSettingRepository.upsert(memberId, item.category().name(), item.pushEnabled(), now);
        }
        return get(memberId);
    }
}