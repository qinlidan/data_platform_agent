package com.dataplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 @Scheduled（如 DataReconciliationTask 每日对账）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
