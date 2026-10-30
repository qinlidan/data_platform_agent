package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.config.ToolGovernanceProperties;
import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.ToolPermissionDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 权限拦截器
 * <p>
 * 基于角色的工具调用权限控制。
 * 从配置中读取权限规则，检查用户角色是否满足要求。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * tool:
 *   governance:
 *     permission:
 *       deleteJob: [ADMIN]
 *       publishJob: [ADMIN, OPERATOR]
 * </pre>
 *
 * <h3>权限策略</h3>
 * <ul>
 *   <li>未配置的工具 - 默认允许所有人调用</li>
 *   <li>配置了角色列表 - 用户必须拥有其中任意一个角色</li>
 *   <li>用户角色为空 - 拒绝访问</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements ToolInterceptor {

    private final ToolGovernanceProperties properties;

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        String toolName = context.getToolName();
        Set<String> userRoles = context.getRoles();

        // 获取工具所需的角色列表
        List<String> requiredRoles = properties.getPermission().get(toolName);

        // 如果未配置权限要求，默认允许
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            log.debug("Tool {} has no permission requirement, allowing access", toolName);
            return chain.proceed(context);
        }

        // 检查用户是否有任意一个所需角色
        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("User {} has no roles, denying access to tool {}", context.getUserId(), toolName);
            throw new ToolPermissionDeniedException(
                    toolName,
                    context.getUserId(),
                    Set.of(),
                    Set.copyOf(requiredRoles)
            );
        }

        boolean hasPermission = requiredRoles.stream()
                .anyMatch(userRoles::contains);

        if (!hasPermission) {
            log.warn("User {} with roles {} lacks permission for tool {} (requires {})",
                    context.getUserId(), userRoles, toolName, requiredRoles);
            throw new ToolPermissionDeniedException(
                    toolName,
                    context.getUserId(),
                    userRoles,
                    Set.copyOf(requiredRoles)
            );
        }

        log.debug("User {} has permission to call tool {}", context.getUserId(), toolName);
        return chain.proceed(context);
    }
}
