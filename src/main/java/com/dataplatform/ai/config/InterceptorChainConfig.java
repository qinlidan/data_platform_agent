package com.dataplatform.ai.config;

import com.dataplatform.ai.interceptor.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 拦截器链配置
 * <p>
 * 定义拦截器的执行顺序和装配规则。
 * </p>
 *
 * <h3>拦截器执行顺序（从外到内）</h3>
 * <ol>
 *   <li>AuditInterceptor - 审计日志（最外层，记录所有状态）</li>
 *   <li>PermissionInterceptor - 权限校验</li>
 *   <li>ConfirmationInterceptor - 二次确认</li>
 *   <li>ValidationInterceptor - 参数校验</li>
 *   <li>TimeoutRetryInterceptor - 超时重试（最内层，直接包裹工具调用）</li>
 * </ol>
 *
 * @author Claude
 * @since 1.0
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorChainConfig {

    private final AuditInterceptor auditInterceptor;
    private final PermissionInterceptor permissionInterceptor;
    private final ConfirmationInterceptor confirmationInterceptor;
    private final ValidationInterceptor validationInterceptor;
    private final TimeoutRetryInterceptor timeoutRetryInterceptor;

    /**
     * 定义拦截器链
     * <p>
     * 注意：列表顺序即为执行顺序（从外到内）
     * </p>
     */
    @Bean
    public List<ToolInterceptor> toolInterceptors() {
        return Arrays.asList(
                auditInterceptor,           // 1. 审计（记录所有状态）
                permissionInterceptor,      // 2. 权限校验
                 validationInterceptor,      // 4. 参数校验
                confirmationInterceptor,    // 3. 二次确认
                timeoutRetryInterceptor     // 5. 超时重试
        );
    }
}
