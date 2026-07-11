package com.campushare.agent.util;

import com.campushare.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "agent:lock:";

    public boolean tryLock(String lockKey, Duration expire) {
        String key = LOCK_PREFIX + lockKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", expire);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        redisTemplate.delete(key);
    }

    public <T> T executeWithLock(String lockKey, Duration expire, Callable<T> action) {
        if (!tryLock(lockKey, expire)) {
            throw new BusinessException(4090, "任务正在执行中，请稍后再试");
        }
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }

    public void executeWithLock(String lockKey, Duration expire, Runnable action) {
        executeWithLock(lockKey, expire, () -> {
            action.run();
            return null;
        });
    }
}
