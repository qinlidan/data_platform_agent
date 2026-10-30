package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.ToolPermissionDeniedException;
import com.dataplatform.mapper.ToolAuditLogMapper;
import com.dataplatform.entity.ToolAuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuditInterceptor 单元测试
 */
class AuditInterceptorTest {

    private AuditInterceptor auditInterceptor;

    @Mock
    private ToolAuditLogMapper auditLogMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditInterceptor = new AuditInterceptor(auditLogMapper);
    }

    @Test
    void testIntercept_执行成功_记录成功日志() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("queryJob")
                .toolInput("{\"jobId\": 1}")
                .userId("user1")
                .conversationId("12345")
                .build();

        InterceptorChain chain = ctx -> "{\"status\": \"ok\"}";

        String result = auditInterceptor.intercept(context, chain);

        assertEquals("{\"status\": \"ok\"}", result);

        // 等待异步日志保存
        Thread.sleep(200);

        ArgumentCaptor<ToolAuditLog> captor = ArgumentCaptor.forClass(ToolAuditLog.class);
        verify(auditLogMapper, timeout(1000)).insert(captor.capture());

        ToolAuditLog log = captor.getValue();
        assertEquals("queryJob", log.getToolName());
        assertEquals("{\"jobId\": 1}", log.getToolInput());
        assertEquals("{\"status\": \"ok\"}", log.getToolResult());
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("user1", log.getUserId());
        assertEquals(12345L, log.getConversationId());
        assertNull(log.getErrorMessage());
        assertTrue(log.getDuration() >= 0);
    }

    @Test
    void testIntercept_权限拒绝_记录拒绝日志() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{\"jobId\": 1}")
                .userId("user1")
                .conversationId("67890")
                .build();

        InterceptorChain chain = ctx -> {
            throw new ToolPermissionDeniedException("deleteJob", "user1", Set.of("USER"), Set.of("ADMIN"));
        };

        assertThrows(ToolPermissionDeniedException.class,
                () -> auditInterceptor.intercept(context, chain));

        // 等待异步日志保存
        Thread.sleep(200);

        ArgumentCaptor<ToolAuditLog> captor = ArgumentCaptor.forClass(ToolAuditLog.class);
        verify(auditLogMapper, timeout(1000)).insert(captor.capture());

        ToolAuditLog log = captor.getValue();
        assertEquals("deleteJob", log.getToolName());
        assertEquals("PERMISSION_DENIED", log.getStatus());
        assertNull(log.getToolResult());
        assertNotNull(log.getErrorMessage());
        assertTrue(log.getErrorMessage().contains("Permission denied"));
    }

    @Test
    void testIntercept_执行异常_记录失败日志() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("executeJob")
                .toolInput("{\"jobId\": 1}")
                .userId("user1")
                .build();

        InterceptorChain chain = ctx -> {
            throw new RuntimeException("Database connection failed");
        };

        assertThrows(RuntimeException.class,
                () -> auditInterceptor.intercept(context, chain));

        // 等待异步日志保存
        Thread.sleep(200);

        ArgumentCaptor<ToolAuditLog> captor = ArgumentCaptor.forClass(ToolAuditLog.class);
        verify(auditLogMapper, timeout(1000)).insert(captor.capture());

        ToolAuditLog log = captor.getValue();
        assertEquals("executeJob", log.getToolName());
        assertEquals("FAILED", log.getStatus());
        assertNotNull(log.getErrorMessage());
        assertTrue(log.getErrorMessage().contains("Database connection failed"));
    }

    @Test
    void testIntercept_ConversationId为空_正常处理() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("queryJob")
                .toolInput("{}")
                .userId("user1")
                .conversationId(null)  // 没有 conversationId
                .build();

        InterceptorChain chain = ctx -> "{}";

        auditInterceptor.intercept(context, chain);

        // 等待异步日志保存
        Thread.sleep(200);

        ArgumentCaptor<ToolAuditLog> captor = ArgumentCaptor.forClass(ToolAuditLog.class);
        verify(auditLogMapper, timeout(1000)).insert(captor.capture());

        ToolAuditLog log = captor.getValue();
        assertNull(log.getConversationId());
    }

    @Test
    void testIntercept_ConversationId非数字_设为null() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("queryJob")
                .toolInput("{}")
                .userId("user1")
                .conversationId("invalid-id")  // 非数字
                .build();

        InterceptorChain chain = ctx -> "{}";

        auditInterceptor.intercept(context, chain);

        // 等待异步日志保存
        Thread.sleep(200);

        ArgumentCaptor<ToolAuditLog> captor = ArgumentCaptor.forClass(ToolAuditLog.class);
        verify(auditLogMapper, timeout(1000)).insert(captor.capture());

        ToolAuditLog log = captor.getValue();
        assertNull(log.getConversationId());
    }
}
