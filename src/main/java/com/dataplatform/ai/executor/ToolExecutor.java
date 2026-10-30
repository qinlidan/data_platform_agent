package com.dataplatform.ai.executor;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.interceptor.InterceptorChain;
import com.dataplatform.ai.interceptor.ToolInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 工具执行器
 * <p>
 * 负责构建拦截器链并执行工具调用。
 * 使用责任链模式，将拦截器与实际工具调用逻辑串联起来。
 * </p>
 *
 * <h3>执行流程</h3>
 * <pre>
 * 用户请求
 *   → ToolExecutor.execute()
 *   → 构建拦截器链
 *   → 依次执行各拦截器
 *   → 最终调用 actualToolCall
 *   → 返回结果
 * </pre>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final List<ToolInterceptor> interceptors;

    /**
     * 执行工具调用
     *
     * @param context        工具执行上下文
     * @param actualToolCall 实际的工具调用逻辑（Lambda 函数）
     * @return 工具执行结果
     * @throws Exception 执行过程中的任何异常
     */
    public String execute(ToolExecutionContext context,
                          Function<ToolExecutionContext, String> actualToolCall) throws Exception {
        // 构建拦截器链
        InterceptorChain chain = buildChain(0, actualToolCall);

        // 执行拦截器链
        return chain.proceed(context);
    }

    /**
     * 递归构建拦截器链
     *
     * @param index          当前拦截器索引
     * @param actualToolCall 实际工具调用
     * @return 拦截器链
     */
    private InterceptorChain buildChain(int index, Function<ToolExecutionContext, String> actualToolCall) {
        if (index >= interceptors.size()) {
            // 所有拦截器都执行完毕，调用实际工具
            return context -> {
                try {
                    return actualToolCall.apply(context);
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException("Tool execution failed", e);
                }
            };
        }

        // 递归构建下一个拦截器
        ToolInterceptor currentInterceptor = interceptors.get(index);
        InterceptorChain nextChain = buildChain(index + 1, actualToolCall);

        return context -> currentInterceptor.intercept(context, nextChain);
    }
}
