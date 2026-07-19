package com.memorin.domain.auth.jwt;

import com.memorin.domain.users.Entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpiry;
    private final UserRepository userRepository;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiry-ms}") long accessExpiry,
            UserRepository userRepository
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = accessExpiry;
        this.userRepository = userRepository;
    }

    //Access Token 생성
    public String createAccessToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    //토큰에서 userId 추출
    public UUID getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        return null;
    }

    public Authentication getAuthentication(String token) {
        UUID userId = getUserId(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
