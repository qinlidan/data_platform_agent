package com.dataplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 向量化任务线程池配置
 * 专用于 Embedding API 调用（与默认 @Async 线程池隔离）
 */
@Configuration
public class VectorizationConfig {

    /**
     * 向量化线程池
     * - 核心线程数 4：适合 IO 密集型任务（调用 Embedding API）
     * - 最大线程数 8：支持突发流量
     * - 队列容量 500：缓冲大量导入任务
     * - 拒绝策略：CallerRunsPolicy（降级到同步执行）
     */
    @Bean("vectorizationExecutor")
    public Executor vectorizationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("vectorization-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
