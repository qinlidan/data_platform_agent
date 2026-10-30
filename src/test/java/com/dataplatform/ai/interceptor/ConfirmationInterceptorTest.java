package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.config.ToolGovernanceProperties;
import com.dataplatform.ai.confirmation.ConfirmationService;
import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.NeedConfirmationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConfirmationInterceptor 单元测试
 */
class ConfirmationInterceptorTest {

    private ConfirmationInterceptor confirmationInterceptor;
    private ToolGovernanceProperties properties;

    @Mock
    private ConfirmationService confirmationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        properties = new ToolGovernanceProperties();
        ToolGovernanceProperties.ConfirmationConfig confirmationConfig =
                new ToolGovernanceProperties.ConfirmationConfig();
        confirmationConfig.setEnabled(true);
        confirmationConfig.setTools(List.of("deleteJob", "publishJob"));
        properties.setConfirmation(confirmationConfig);

        confirmationInterceptor = new ConfirmationInterceptor(
                properties,
                confirmationService,
                objectMapper
        );
    }

    @Test
    void testIntercept_确认功能未启用_直接通过() throws Exception {
        // 禁用确认功能
        properties.getConfirmation().setEnabled(false);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = confirmationInterceptor.intercept(context, chain);

        assertEquals("success", result);
        verify(confirmationService, never()).generateToken(anyString(), anyString(), anyMap());
    }

    @Test
    void testIntercept_工具不需要确认_直接通过() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("queryJob")  // 不在确认列表中
                .toolInput("{}")
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = confirmationInterceptor.intercept(context, chain);

        assertEquals("success", result);
        verify(confirmationService, never()).generateToken(anyString(), anyString(), anyMap());
    }

    @Test
    void testIntercept_未提供Token_要求确认() {
        // Mock 生成 token
        when(confirmationService.generateToken(anyString(), anyString(), anyMap()))
                .thenReturn("generated-token-123");

        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{\"jobId\": 1}")
                .confirmationToken(null)  // 未提供 token
                .build();

        InterceptorChain chain = ctx -> "success";

        NeedConfirmationException exception = assertThrows(
                NeedConfirmationException.class,
                () -> confirmationInterceptor.intercept(context, chain)
        );

        assertEquals("deleteJob", exception.getToolName());
        assertEquals("generated-token-123", exception.getConfirmationToken());
        verify(confirmationService).generateToken(eq("deleteJob"), eq("execute"), anyMap());
    }

    @Test
    void testIntercept_提供有效Token_通过() throws Exception {
        // Mock token 验证成功
        when(confirmationService.validateToken("valid-token", "deleteJob", "execute"))
                .thenReturn(true);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .confirmationToken("valid-token")
                .build();

        InterceptorChain chain = ctx -> "success";

        String result = confirmationInterceptor.intercept(context, chain);

        assertEquals("success", result);
        verify(confirmationService).validateToken("valid-token", "deleteJob", "execute");
    }

    @Test
    void testIntercept_提供无效Token_拒绝() {
        // Mock token 验证失败
        when(confirmationService.validateToken("invalid-token", "deleteJob", "execute"))
                .thenReturn(false);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .toolName("deleteJob")
                .toolInput("{}")
                .confirmationToken("invalid-token")
                .build();

        InterceptorChain chain = ctx -> "success";

        NeedConfirmationException exception = assertThrows(
                NeedConfirmationException.class,
                () -> confirmationInterceptor.intercept(context, chain)
        );

        assertEquals("deleteJob", exception.getToolName());
        assertNull(exception.getConfirmationToken());
        assertTrue(exception.getMessage().contains("Invalid or expired"));
    }
}
