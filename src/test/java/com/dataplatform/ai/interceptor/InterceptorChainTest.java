package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 拦截器链测试
 */
class InterceptorChainTest {

    @Test
    void testInterceptorChain_顺序执行() {
        // 测试拦截器按顺序执行
        List<String> executionOrder = new ArrayList<>();

        Interceptor interceptor1 = new TestInterceptor(executionOrder, "1");
        Interceptor interceptor2 = new TestInterceptor(executionOrder, "2");
        Interceptor interceptor3 = new TestInterceptor(executionOrder, "3");

        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("test")
                .toolInput("{}")
                .build();

        // 创建拦截器链
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(interceptor1);
        interceptors.add(interceptor2);
        interceptors.add(interceptor3);

        InterceptorChain chain = new DefaultInterceptorChain(interceptors, 0);

        try {
            String result = chain.proceed(context);

            assertEquals("{}", result);
            assertEquals("[enter-1, enter-2, enter-3, exit-3, exit-2, exit-1]", executionOrder.toString());
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    /**
     * 测试用拦截器
     */
    private static class TestInterceptor implements Interceptor {
        private final List<String> executionOrder;
        private final String name;

        public TestInterceptor(List<String> executionOrder, String name) {
            this.executionOrder = executionOrder;
            this.name = name;
        }

        @Override
        public String intercept(ToolExecutionContext context, InterceptorChain chain) {
            try {
                executionOrder.add("enter-" + name);
                String result = chain.proceed(context);
                executionOrder.add("exit-" + name);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 默认拦截器链实现（用于测试）
     */
    private static class DefaultInterceptorChain implements InterceptorChain {
        private final List<Interceptor> interceptors;
        private final int index;

        public DefaultInterceptorChain(List<Interceptor> interceptors, int index) {
            this.interceptors = interceptors;
            this.index = index;
        }

        @Override
        public String proceed(ToolExecutionContext context) throws Exception {
            if (index >= interceptors.size()) {
                return "{}";  // 默认返回
            }

            Interceptor interceptor = interceptors.get(index);
            InterceptorChain next = new DefaultInterceptorChain(interceptors, index + 1);

            return interceptor.intercept(context, next);
        }
    }
}
