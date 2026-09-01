package com.memorin.domain.fcm_token.repository;

import com.memorin.domain.fcm_token.entity.DeviceType;
import com.memorin.domain.fcm_token.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    Optional<FcmToken> findByUserIdAndDeviceType(UUID userId, DeviceType deviceType);

    List<FcmToken> findAllByUserIdAndDeviceTypeIn(UUID userId, List<DeviceType> deviceTypes);
}
