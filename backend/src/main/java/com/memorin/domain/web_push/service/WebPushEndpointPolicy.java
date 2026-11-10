package com.memorin.domain.web_push.service;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Web Push endpoint로 받아도 되는 URL인지 판단한다(#311).
 *
 * <p>서버는 등록된 endpoint로 직접 POST한다. 검증 없이 받으면 내부 주소(localhost, 사설망, 메타데이터
 * 서버 등)를 등록해 서버가 대신 요청을 보내게 만드는 blind SSRF가 된다. 그래서 브라우저 벤더의
 * 푸시 서비스 호스트만 허용한다.
 *
 * <ul>
 *   <li>Chrome·Edge(Chromium)·Opera·Samsung Internet — {@code fcm.googleapis.com}</li>
 *   <li>Firefox — {@code updates.push.services.mozilla.com}</li>
 *   <li>Safari(macOS·iOS 16.4+) — {@code *.push.apple.com} (예: {@code web.push.apple.com})</li>
 *   <li>Windows(WNS) — {@code *.notify.windows.com}</li>
 * </ul>
 *
 * <p>새 브라우저가 다른 호스트를 쓰면 구독 등록이 400으로 실패한다. 그때 여기에 추가한다.
 */
public final class WebPushEndpointPolicy {

    /** DB 컬럼 길이(web_push_subscriptions.endpoint)와 같다. */
    public static final int MAX_LENGTH = 2048;

    private static final Set<String> EXACT_HOSTS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com"
    );

    // 앞의 점까지 포함해 비교한다. "evilpush.apple.com" 같은 이름이 걸리지 않게 하기 위해서다.
    private static final List<String> HOST_SUFFIXES = List.of(
            ".push.apple.com",
            ".notify.windows.com"
    );

    private WebPushEndpointPolicy() {
    }

    public static boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.isEmpty() || endpoint.length() > MAX_LENGTH) {
            return false;
        }

        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        // user@host 형태나 다른 포트로 같은 호스트의 다른 서비스를 노리는 것을 막는다.
        if (uri.getRawUserInfo() != null) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (EXACT_HOSTS.contains(host)) {
            return true;
        }
        return HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    public static void validate(String endpoint) {
        if (!isAllowed(endpoint)) {
            throw new BusinessException(ErrorCode.COMMON_002, "지원하지 않는 Web Push endpoint입니다.");
        }
    }
}
