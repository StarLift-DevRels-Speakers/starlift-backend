package ru.shaxowskiy.javaspeakerclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:jti:";

    private final StringRedisTemplate redisTemplate;

    public void store(String jti, Long userId, Duration ttl) {
        if (jti == null || userId == null || ttl == null) {
            return;
        }
        redisTemplate.opsForValue()
                .set(key(jti), String.valueOf(userId), ttl);
    }

    public boolean isActive(String jti, Long userId) {
        if (jti == null || userId == null) {
            return false;
        }
        var stored = redisTemplate.opsForValue().get(key(jti));
        return String.valueOf(userId).equals(stored);
    }

    public void replace(String oldJti, String newJti, Long userId, Duration ttl) {
        if (!isActive(oldJti, userId)) {
            throw new IllegalStateException("Refresh token is expired or revoked");
        }
        delete(oldJti);
        store(newJti, userId, ttl);
    }

    public void delete(String jti) {
        if (jti == null) {
            return;
        }
        redisTemplate.delete(key(jti));
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
