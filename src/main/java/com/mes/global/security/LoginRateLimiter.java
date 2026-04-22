package com.mes.global.security;

import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 로그인 실패 횟수 제한.
 * 15분 내 5회 연속 실패 시 계정을 임시 잠금.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;
    private static final String PREFIX = "login:fail:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 잠금 여부 사전 확인.
     * 이미 잠긴 상태이면 CustomException(TOO_MANY_LOGIN_ATTEMPTS) throw.
     */
    public void checkNotLocked(String username) {
        Object val = redisTemplate.opsForValue().get(PREFIX + username);
        if (val != null && ((Number) val).intValue() >= MAX_ATTEMPTS) {
            throw new CustomException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /**
     * 로그인 실패 기록.
     * MAX_ATTEMPTS 도달 시 CustomException(TOO_MANY_LOGIN_ATTEMPTS) throw.
     */
    public void recordFailure(String username) {
        String key = PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            // 첫 실패 시 TTL 세팅
            redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            throw new CustomException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /**
     * 로그인 성공 시 실패 카운터 초기화.
     */
    public void clearFailures(String username) {
        redisTemplate.delete(PREFIX + username);
    }
}
