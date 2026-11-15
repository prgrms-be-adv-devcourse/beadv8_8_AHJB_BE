package com.jjinmak.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "notification.push.fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    private static final int TIMEOUT_MILLIS = 10_000;

    @Bean
    public FirebaseApp firebaseApp(NotificationPushProperties properties) throws IOException {
        String encoded = properties.fcm().credentialsBase64();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("FCM_ENABLED=true인데 FCM_CREDENTIALS_BASE64가 비어 있습니다.");
        }

        byte[] json = Base64.getDecoder().decode(encoded.trim());
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)))
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setReadTimeout(TIMEOUT_MILLIS)
                .build();

        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options));
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}