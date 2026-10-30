package com.dataplatform.ai.checkpoint;

import com.dataplatform.ai.graph.AgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis Checkpoint - 状态持久化
 *
 * 职责：
 * 1. 保存 AgentState 到 Redis
 * 2. 加载 AgentState 从 Redis
 * 3. 删除过期的 Checkpoint
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCheckpoint {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Checkpoint 过期时间（10分钟）
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * 保存状态
     *
     * @param checkpointId Checkpoint ID
     * @param state        状态对象
     */
    public void save(String checkpointId, AgentState state) {
        try {
            String key = buildKey(checkpointId);
            String value = objectMapper.writeValueAsString(state);

            redisTemplate.opsForValue()
                    .set(key, value, TTL)
                    .doOnSuccess(result -> log.info("[RedisCheckpoint] 保存成功：{}, TTL={}分钟", key, TTL.toMinutes()))
                    .doOnError(e -> log.error("[RedisCheckpoint] 保存失败：{}", key, e))
                    .subscribe();

        } catch (Exception e) {
            log.error("[RedisCheckpoint] 序列化失败", e);
            throw new RuntimeException("保存 Checkpoint 失败", e);
        }
    }

    /**
     * 加载状态
     *
     * @param checkpointId Checkpoint ID
     * @return 状态对象（不存在返回 null）
     */
    public AgentState load(String checkpointId) {
        try {
            String key = buildKey(checkpointId);

            String value = redisTemplate.opsForValue()
                    .get(key)
                    .block();

            if (value == null) {
                log.warn("[RedisCheckpoint] Checkpoint 不存在：{}", key);
                return null;
            }

            AgentState state = objectMapper.readValue(value, AgentState.class);
            log.info("[RedisCheckpoint] 加载成功：{}", key);

            return state;

        } catch (Exception e) {
            log.error("[RedisCheckpoint] 反序列化失败：{}", checkpointId, e);
            return null;
        }
    }

    /**
     * 删除 Checkpoint
     *
     * @param checkpointId Checkpoint ID
     */
    public void delete(String checkpointId) {
        String key = buildKey(checkpointId);

        redisTemplate.delete(key)
                .doOnSuccess(result -> log.info("[RedisCheckpoint] 删除成功：{}", key))
                .doOnError(e -> log.error("[RedisCheckpoint] 删除失败：{}", key, e))
                .subscribe();
    }

    /**
     * 检查 Checkpoint 是否存在
     */
    public boolean exists(String checkpointId) {
        String key = buildKey(checkpointId);
        Boolean exists = redisTemplate.hasKey(key).block();
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(String checkpointId) {
        return "stategraph:checkpoint:" + checkpointId;
    }
}
