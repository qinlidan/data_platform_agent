package com.dataplatform.ai.exception;

import lombok.Getter;

import java.util.Set;

/**
 * 工具权限拒绝异常
 *
 * @author Claude
 * @since 1.0
 */
@Getter
public class ToolPermissionDeniedException extends ToolGovernanceException {

    private final String toolName;
    private final String userId;
    private final Set<String> userRoles;
    private final Set<String> requiredRoles;

    public ToolPermissionDeniedException(String toolName, String userId,
                                          Set<String> userRoles, Set<String> requiredRoles) {
        super(String.format("Permission denied for tool '%s'. User '%s' with roles %s lacks required roles: %s",
                toolName, userId, userRoles, requiredRoles));
        this.toolName = toolName;
        this.userId = userId;
        this.userRoles = userRoles;
        this.requiredRoles = requiredRoles;
    }
}
