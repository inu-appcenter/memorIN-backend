package com.memorin.domain.web_push;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebPushService가 쓰는 {@code PushService.send(notification)}의 요청 헤더를 확인한다.
 *
 * <p>{@code send(notification)}은 기본으로 aesgcm 인코딩을 쓰고, 이때 VAPID 공개키가 Crypto-Key 헤더의
 * {@code p256ecdsa}로 나간다. FCM은 이 값에 base64 패딩(=)이 붙으면 403
 * "crypto-key header had invalid format"으로 거절한다. web-push 5.1.2가 패딩을 붙이므로
 * (web-push-libs/webpush-java#216) 라이브러리를 올릴 때 이 테스트로 먼저 확인한다.
 */
class WebPushRequestHeaderTest {

    @BeforeAll
    static void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void vapid_public_key_in_crypto_key_header_has_no_base64_padding() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair vapid = generator.generateKeyPair();
        KeyPair subscriber = generator.generateKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);

        // WebPushService와 같은 방식으로 base64url 문자열 키를 넣는다.
        String vapidPublicKey = base64Url(publicKeyBytes(vapid));
        PushService pushService = new PushService();
        pushService.setPublicKey(vapidPublicKey);
        pushService.setPrivateKey(base64Url(privateKeyBytes(vapid)));
        pushService.setSubject("mailto:test@example.com");

        Notification notification = new Notification(
            "https://fcm.googleapis.com/fcm/send/test",
            base64Url(publicKeyBytes(subscriber)),
            base64Url(auth),
            "{}".getBytes(StandardCharsets.UTF_8)
        );

        // send(notification)과 같은 인코딩으로 요청만 만들고 보내지는 않는다.
        HttpPost post = pushService.preparePost(notification, Encoding.AESGCM);
        String cryptoKey = post.getFirstHeader("Crypto-Key").getValue();
        String p256ecdsa = Arrays.stream(cryptoKey.split(";"))
            .filter(part -> part.startsWith("p256ecdsa="))
            .map(part -> part.substring("p256ecdsa=".length()))
            .findFirst()
            .orElseThrow();

        assertThat(p256ecdsa).isEqualTo(vapidPublicKey);
    }

    private static byte[] publicKeyBytes(KeyPair keyPair) {
        return ((ECPublicKey) keyPair.getPublic()).getQ().getEncoded(false);
    }

    private static byte[] privateKeyBytes(KeyPair keyPair) {
        BigInteger d = ((ECPrivateKey) keyPair.getPrivate()).getD();
        byte[] raw = d.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
