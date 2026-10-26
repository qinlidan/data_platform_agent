package com.dataplatform.common;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器
 * <p>
 * 基于 Hutool 的 Snowflake 算法生成分布式唯一 ID
 * </p>
 */
@Component
public class IdGenerator {

    private final Snowflake snowflake;

    public IdGenerator(
            @Value("${snowflake.worker-id:1}") long workerId,
            @Value("${snowflake.datacenter-id:1}") long datacenterId) {
        this.snowflake = IdUtil.getSnowflake(workerId, datacenterId);
    }

    /**
     * 生成下一个唯一 ID
     *
     * @return 唯一 Long 型 ID
     */
    public Long nextId() {
        return snowflake.nextId();
    }
}
