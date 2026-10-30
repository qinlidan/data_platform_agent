package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.config.ToolGovernanceProperties;
import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.ToolPermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionInterceptor 单元测试
 */
class PermissionInterceptorTest {

    private PermissionInterceptor permissionInterceptor;
    private ToolGovernanceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ToolGovernanceProperties();
        // 配置权限规则
        Map<String, List<String>> permissionMap = Map.of(
                "deleteJob", List.of("ADMIN"),
                "publishJob", List.of("ADMIN", "OPERATOR")
        );
        properties.setPermission(permissionMap);

        permissionInterceptor = new PermissionInterceptor(properties);
    }

    @Test
    void testIntercept_无权限要求_允许访问() throws Exception {
        // 未配置权限的工具，应该允许任何人访问
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("queryJob")  // 未配置权限
                .toolInput("{}")
                .userId("user1")
                .roles(Set.of("USER"))
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = permissionInterceptor.intercept(context, chain);

        assertEquals("success", result);
    }

    @Test
    void testIntercept_有权限_允许访问() throws Exception {
        // 用户有 ADMIN 角色，可以调用 deleteJob
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .userId("admin1")
                .roles(Set.of("ADMIN"))
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = permissionInterceptor.intercept(context, chain);

        assertEquals("success", result);
    }

    @Test
    void testIntercept_有多个角色其中一个满足_允许访问() throws Exception {
        // publishJob 需要 ADMIN 或 OPERATOR，用户有 OPERATOR
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("publishJob")
                .toolInput("{}")
                .userId("operator1")
                .roles(Set.of("OPERATOR", "USER"))
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = permissionInterceptor.intercept(context, chain);

        assertEquals("success", result);
    }

    @Test
    void testIntercept_无角色_拒绝访问() {
        // 用户没有角色，尝试调用需要权限的工具
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .userId("user1")
                .roles(Set.of())  // 空角色
                .build();

        InterceptorChain chain = ctx -> "success";

        assertThrows(ToolPermissionDeniedException.class,
                () -> permissionInterceptor.intercept(context, chain));
    }

    @Test
    void testIntercept_角色不匹配_拒绝访问() {
        // 用户只有 USER 角色，尝试调用需要 ADMIN 的工具
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .userId("user1")
                .roles(Set.of("USER", "GUEST"))
                .build();

        InterceptorChain chain = ctx -> "success";

        ToolPermissionDeniedException exception = assertThrows(
                ToolPermissionDeniedException.class,
                () -> permissionInterceptor.intercept(context, chain)
        );

        assertEquals("deleteJob", exception.getToolName());
        assertEquals("user1", exception.getUserId());
    }
}
