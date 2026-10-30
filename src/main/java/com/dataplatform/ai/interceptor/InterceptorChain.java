package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;

/**
 * 拦截器链接口
 * <p>
 * 用于在拦截器中继续执行下一个拦截器或最终的工具调用。
 * </p>
 *
 * @author Claude
 * @since 1.0
 */
@FunctionalInterface
public interface InterceptorChain {

    /**
     * 继续执行下一个拦截器
     *
     * @param context 工具执行上下文
     * @return 工具执行结果
     * @throws Exception 执行过程中的任何异常
     */
    String proceed(ToolExecutionContext context) throws Exception;
}
