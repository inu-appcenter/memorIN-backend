package com.memorin;

import org.springframework.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends CrudRepository<FcmToken, Long> {
    List<FcmToken> findByUserId(String userId);
    Optional<FcmToken> findByToken(String token);
}
