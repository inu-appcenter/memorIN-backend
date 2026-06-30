package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;

@Configuration
public class FcmConfig {

    // 앱 시작될 때 딱 한 번 실행되는 초기화 메서드
    @PostConstruct
    public void init() throws Exception {
        InputStream serviceAccount =
                getClass().getResourceAsStream("/firebase-service-account.json");

        if (serviceAccount == null) {
            throw new IllegalStateException(
                    "firebase-service-account.json 을 classpath에서 찾을 수 없습니다. " +
                    "src/main/resources/ 에 키 파일이 있는지 확인하세요.");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}