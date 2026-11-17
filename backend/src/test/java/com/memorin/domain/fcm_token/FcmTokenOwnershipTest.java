package com.memorin.domain.fcm_token;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.entity.DeviceType;
import com.memorin.domain.fcm_token.entity.FcmToken;
import com.memorin.domain.fcm_token.repository.FcmTokenRepository;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// FCM 토큰은 토큰 값 기준으로 소유자가 한 명이다(V19).
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FcmTokenOwnershipTest extends PostgresTestSupport {

    private static final List<DeviceType> APP_DEVICES = List.of(DeviceType.ANDROID, DeviceType.IOS);

    @Autowired
    private FcmTokenService fcmTokenService;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 같은_기기에서_다른_사용자가_로그인하면_토큰_소유권이_넘어간다() {
        UUID a = persistUser("fcm-a");
        UUID b = persistUser("fcm-b");
        String sharedDeviceToken = "device-" + suffix();

        fcmTokenService.save(a, new FcmTokenRequest(sharedDeviceToken, DeviceType.ANDROID));
        fcmTokenService.save(b, new FcmTokenRequest(sharedDeviceToken, DeviceType.ANDROID));

        assertThat(tokensOf(a)).isEmpty();
        assertThat(tokensOf(b)).containsExactly(sharedDeviceToken);
    }

    @Test
    void 한_사용자의_같은_종류_기기_두_대는_각자_토큰을_가진다() {
        UUID user = persistUser("fcm-two-devices");
        String phone = "phone-" + suffix();
        String tablet = "tablet-" + suffix();

        fcmTokenService.save(user, new FcmTokenRequest(phone, DeviceType.ANDROID));
        fcmTokenService.save(user, new FcmTokenRequest(tablet, DeviceType.ANDROID));

        assertThat(tokensOf(user)).containsExactlyInAnyOrder(phone, tablet);
    }

    @Test
    void 같은_토큰을_다시_등록해도_행이_늘지_않는다() {
        UUID user = persistUser("fcm-idempotent");
        String token = "token-" + suffix();

        fcmTokenService.save(user, new FcmTokenRequest(token, DeviceType.IOS));
        fcmTokenService.save(user, new FcmTokenRequest(token, DeviceType.IOS));

        assertThat(tokensOf(user)).containsExactly(token);
    }

    @Test
    void deleteAll은_해당_사용자의_토큰만_지운다() {
        UUID user = persistUser("fcm-delete-all");
        UUID other = persistUser("fcm-other");
        fcmTokenService.save(user, new FcmTokenRequest("mine-" + suffix(), DeviceType.ANDROID));
        String othersToken = "others-" + suffix();
        fcmTokenService.save(other, new FcmTokenRequest(othersToken, DeviceType.ANDROID));

        fcmTokenService.deleteAll(user);

        assertThat(tokensOf(user)).isEmpty();
        assertThat(tokensOf(other)).containsExactly(othersToken);
    }

    private List<String> tokensOf(UUID userId) {
        return fcmTokenRepository.findAllByUserIdAndDeviceTypeIn(userId, APP_DEVICES).stream()
            .map(FcmToken::getToken)
            .toList();
    }

    private UUID persistUser(String tag) {
        String unique = tag + "-" + suffix();
        return userRepository.save(new User(unique + "@memorin.test", "hash", unique, unique, null)).getId();
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
