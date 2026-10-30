package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;

/**
 * 工具执行拦截器接口
 * <p>
 * 拦截器模式：类似于 Spring MVC 的 HandlerInterceptor，按责任链顺序执行。
 *
 * @author Claude
 * @since 1.0
 */
public interface Interceptor {

    /**
     * 拦截工具执行
     *
     * @param context 工具执行上下文
     * @param chain   拦截器链（用于调用下一个拦截器）
     * @return 工具执行结果（JSON 字符串）
     * @throws Exception 工具执行过程中的任何异常
     */
    String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception;
}
