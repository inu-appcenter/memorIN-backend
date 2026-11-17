package com.memorin.domain.web_push;

import com.memorin.domain.web_push.service.WebPushEndpointPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #311: allowlist가 실제 브라우저 endpoint를 막지 않고, 내부 주소·위장 호스트는 막는지 확인한다.
 *
 * <p>허용 쪽 값은 각 브라우저가 {@code PushSubscription.endpoint}로 돌려주는 형식을 그대로 옮긴 것이다
 * (토큰 부분만 임의 값). 새 브라우저나 형식이 늘면 여기에 먼저 추가한다.
 */
class WebPushEndpointPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        // Chrome / Edge(Chromium) / Opera / Samsung Internet — FCM
        "https://fcm.googleapis.com/fcm/send/dQw4w9WgXcQ:APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeBkGtHXTLMjpZ9pMgV0J5Aq8kAF",
        // Firefox — autopush v1 / v2
        "https://updates.push.services.mozilla.com/wpush/v1/gAAAAABkZ3J0b0V4YW1wbGVUb2tlbl9GaXJlZm94X3YxXzEyMzQ1Njc4OTA",
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAAABl0x2Y7VjQmNhcmVfYWJvdXRfdGhpc190b2tlbl9GaXJlZm94X3Yy",
        // Safari (macOS 13+, iOS/iPadOS 16.4+)
        "https://web.push.apple.com/QGuQyavXutnMH2Q9kJ0jbPeOdzBVanCQvHpbIBkYBXtgKpMh6YOcqaUe3yt6zlbRYQ_WiUkbOkwsqXHqsCbxgamnFwHrIDcHBIjgTpN4Sq1Gm1bDgHcMyTkQlBy",
        // Edge(구 EdgeHTML)·Windows — WNS
        "https://wns2-par02p.notify.windows.com/w/?token=BQYAAABg5PFj%2bQ8B5y%2fRk7pKwH1LjTlvgF0GtR3eD9Yb%2bdA%3d",
        // 대소문자·기본 포트 명시는 같은 주소다
        "https://FCM.googleapis.com/fcm/send/abc",
        "https://fcm.googleapis.com:443/fcm/send/abc",
    })
    void 실제_브라우저_endpoint는_허용한다(String endpoint) {
        assertThat(WebPushEndpointPolicy.isAllowed(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // https가 아님
        "http://fcm.googleapis.com/fcm/send/abc",
        "ftp://fcm.googleapis.com/fcm/send/abc",
        // 내부 주소(SSRF 대상)
        "https://localhost/push",
        "https://127.0.0.1/push",
        "https://169.254.169.254/latest/meta-data/",
        "https://10.0.0.5/push",
        "https://minio:9000/bucket",
        // allowlist 호스트로 위장
        "https://fcm.googleapis.com.evil.example/fcm/send/abc",
        "https://evilpush.apple.com/abc",
        "https://notify.windows.com.evil.example/w/",
        "https://fcm.googleapis.com@evil.example/fcm/send/abc",
        "https://evil.example/?fcm.googleapis.com",
        // 같은 호스트의 다른 포트
        "https://fcm.googleapis.com:8443/fcm/send/abc",
        // 형식 오류
        "fcm.googleapis.com/fcm/send/abc",
        "https:///fcm/send/abc",
        "https://fcm.googleapis.com/fcm send/abc",
        "",
    })
    void 허용되지_않은_endpoint는_거절한다(String endpoint) {
        assertThat(WebPushEndpointPolicy.isAllowed(endpoint)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void 길이_제한을_넘으면_거절한다() {
        String prefix = "https://fcm.googleapis.com/fcm/send/";
        String atLimit = prefix + "a".repeat(WebPushEndpointPolicy.MAX_LENGTH - prefix.length());
        assertThat(WebPushEndpointPolicy.isAllowed(atLimit)).isTrue();
        assertThat(WebPushEndpointPolicy.isAllowed(atLimit + "a")).isFalse();
        assertThat(WebPushEndpointPolicy.isAllowed(null)).isFalse();
    }
}
