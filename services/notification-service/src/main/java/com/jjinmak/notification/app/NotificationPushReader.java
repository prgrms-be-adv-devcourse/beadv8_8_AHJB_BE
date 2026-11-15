package com.jjinmak.notification.app;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.domain.NotificationDevice;
import com.jjinmak.notification.out.NotificationDeviceRepository;
import com.jjinmak.notification.out.NotificationRepository;
import com.jjinmak.notification.out.NotificationSettingRepository;
import com.jjinmak.notification.out.push.PushMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationPushReader {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationDeviceRepository notificationDeviceRepository;

    @Transactional(readOnly = true)
    public List<PushTarget> read(List<Long> notificationIds) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        Set<Long> receiverIds = notifications.stream()
                .map(Notification::getReceiverId)
                .collect(Collectors.toSet());

        Set<MemberCategory> pushOff = notificationSettingRepository.findByMemberIdInAndPushEnabledFalse(receiverIds).stream()
                .map(setting -> new MemberCategory(setting.getMemberId(), setting.getCategory()))
                .collect(Collectors.toSet());

        List<Notification> pushable = notifications.stream()
                .filter(n -> !pushOff.contains(new MemberCategory(n.getReceiverId(), n.getCategory())))
                .toList();
        if (pushable.isEmpty()) {
            return List.of();
        }

        Set<Long> pushableReceiverIds = pushable.stream()
                .map(Notification::getReceiverId)
                .collect(Collectors.toSet());
        Map<Long, List<String>> tokensByMember = notificationDeviceRepository.findByMemberIdIn(pushableReceiverIds).stream()
                .collect(Collectors.groupingBy(NotificationDevice::getMemberId,
                        Collectors.mapping(NotificationDevice::getToken, Collectors.toList())));

        return pushable.stream()
                .filter(n -> tokensByMember.containsKey(n.getReceiverId()))
                .map(n -> new PushTarget(PushMessage.from(n), tokensByMember.get(n.getReceiverId())))
                .toList();
    }

    private record MemberCategory(Long memberId, NotificationCategory category) {}
}