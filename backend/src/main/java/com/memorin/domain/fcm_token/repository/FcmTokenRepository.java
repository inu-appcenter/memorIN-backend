package com.memorin.domain.fcm_token.repository;

import com.memorin.domain.fcm_token.entity.DeviceType;
import com.memorin.domain.fcm_token.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO fcm_tokens (id, user_id, token, device_type)
        VALUES (uuidv7(), :userId, :token, :deviceType)
        ON CONFLICT (token) DO UPDATE
        SET user_id = EXCLUDED.user_id,
            device_type = EXCLUDED.device_type,
            updated_at = NOW()
        """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("token") String token, @Param("deviceType") String deviceType);

    List<FcmToken> findAllByUserIdAndDeviceTypeIn(UUID userId, List<DeviceType> deviceTypes);

    void deleteByUserIdAndToken(UUID userId, String token);

    void deleteByUserId(UUID userId);
}
