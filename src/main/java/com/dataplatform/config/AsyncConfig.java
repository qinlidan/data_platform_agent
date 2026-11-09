package com.dataplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 为摘要生成等后台任务提供独立线程池
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 摘要生成专用线程池
     * 核心线程数：2（通常同时只有少量会话需要摘要）
     * 最大线程数：4
     * 队列容量：50
     */
    @Bean(name = "summaryExecutor")
    public Executor summaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("summary-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("[AsyncConfig] Initialized summaryExecutor: core=2, max=4, queue=50");
        return executor;
    }
}
