package com.memorin.domain.auth.jwt;

import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    // HS256 키는 256비트(32바이트) 이상이어야 한다.
    static final int MIN_SECRET_BYTES = 32;
    // 공개 저장소의 예시 값. 이 값으로 기동하면 누구나 유효한 토큰을 직접 서명할 수 있다.
    static final String EXAMPLE_SECRET = "changeme-in-production-use-256bit-secret";

    private final SecretKey secretKey;
    private final long accessExpiry;
    private final long refreshExpiry;
    private final UserRepository userRepository;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiry-ms}") long accessExpiry,
            @Value("${jwt.refresh-expiry-ms}") long refreshExpiry,
            UserRepository userRepository
    ) {
        this.secretKey = Keys.hmacShaKeyFor(validateSecret(secret));
        this.accessExpiry = accessExpiry;
        this.refreshExpiry = refreshExpiry;
        this.userRepository = userRepository;
    }

    // 예시 값이거나 짧은 시크릿이면 기동을 거부한다. 시크릿 값 자체는 메시지에 넣지 않는다.
    static byte[] validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET이 비어 있습니다. 무작위 값을 설정하세요(예: openssl rand -base64 48).");
        }
        if (secret.trim().equalsIgnoreCase(EXAMPLE_SECRET) || secret.trim().toLowerCase().startsWith("changeme")) {
            throw new IllegalStateException("JWT_SECRET이 .env.example의 예시 값입니다. 무작위 값을 설정하세요(예: openssl rand -base64 48).");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다(현재 " + bytes.length + "바이트).");
        }
        return bytes;
    }

    //Access Token 생성
    public String createAccessToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
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
        return validateToken(token, null);
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN_TYPE);
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not hash refresh token", e);
        }
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (expectedType != null && !expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw new BusinessException(ErrorCode.AUTH_004);
            }

            return true;

        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_003);

        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.AUTH_004);
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
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_004));

        // principal은 반드시 UserDetailsImpl로 넣는다.
        // User 엔티티를 그대로 넣으면 컨트롤러의 @AuthenticationPrincipal UserDetailsImpl이 null로 주입된다.
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new UsernamePasswordAuthenticationToken(
                new UserDetailsImpl(user, authorities),
                null,
                authorities
        );
    }

    public String createRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }
}
