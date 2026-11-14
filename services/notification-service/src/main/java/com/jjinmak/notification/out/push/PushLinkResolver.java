package com.jjinmak.notification.out.push;

import com.jjinmak.notification.config.NotificationPushProperties;
import com.jjinmak.notification.domain.NotificationCategory;
import org.springframework.stereotype.Component;

@Component
public class PushLinkResolver {

    private final String baseUrl;

    public PushLinkResolver(NotificationPushProperties properties) {
        this.baseUrl = properties.webBaseUrl().replaceAll("/+$", "");
    }

    public String resolve(PushMessage message) {
        Long id = message.targetId();
        String path = switch (message.targetType()) {
            case AUCTION -> "/auctions/" + id;
            case ORDER -> message.category() == NotificationCategory.SELL ? "/seller/orders/" + id : "/orders/" + id;
            case PRODUCT -> "/products/" + id;
            case CART -> "/cart";
            case SETTLEMENT -> "/seller/settlements";
            case MEMBER -> "/mypage";
        };
        return baseUrl + path;
    }
}