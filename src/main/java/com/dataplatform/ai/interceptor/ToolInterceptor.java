package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;

/**
 * 工具拦截器接口
 * <p>
 * 定义工具调用的拦截器规范，使用责任链模式实现统一的治理能力。
 * 拦截器可以在工具调用前后执行额外逻辑（权限校验、参数校验、审计等）。
 * </p>
 *
 * <h3>执行顺序</h3>
 * <pre>
 * Audit → Permission → Confirmation → Validation → TimeoutRetry → 原始工具
 * </pre>
 *
 * @author Claude
 * @since 1.0
 */
@FunctionalInterface
public interface ToolInterceptor extends Interceptor {
    // 继承 Interceptor 接口，无需重复定义方法
}
