package com.dataplatform.controller;

import com.dataplatform.dto.version.RollbackRequest;
import com.dataplatform.dto.version.RollbackResult;
import com.dataplatform.service.JobVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobVersionController 简单单元测试
 *
 * <p>不使用Spring上下文，直接测试Controller逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("作业版本Controller简单测试")
class JobVersionControllerSimpleTest {

    @Mock
    private JobVersionService jobVersionService;

    @InjectMocks
    private JobVersionController jobVersionController;

    private RollbackRequest request;
    private RollbackResult expectedResult;

    @BeforeEach
    void setUp() {
        request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(2)
                .operatorId(100L)
                .operatorName("admin")
                .reason("修复线上Bug")
                .build();

        expectedResult = RollbackResult.builder()
                .resultCode(0)
                .resultMsg("回滚成功！新版本：v4，基于v2创建")
                .newVersion(4)
                .targetVersion(2)
                .build();
    }

    @Test
    @DisplayName("回滚接口 - 成功")
    void testRollbackSuccess() {
        // Mock service behavior
        when(jobVersionService.rollback(any(RollbackRequest.class))).thenReturn(expectedResult);

        // 执行Controller方法
        var response = jobVersionController.rollback(request);

        // 验证结果
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(expectedResult);
        assertThat(response.getData().getResultCode()).isEqualTo(0);
        assertThat(response.getData().getNewVersion()).isEqualTo(4);

        // 验证Service方法被调用
        verify(jobVersionService).rollback(request);
    }

    @Test
    @DisplayName("回滚接口 - Service返回失败结果")
    void testRollbackServiceFailure() {
        // Mock service返回失败结果
        RollbackResult failureResult = RollbackResult.builder()
                .resultCode(1)
                .resultMsg("回滚失败：目标版本不存在")
                .build();

        when(jobVersionService.rollback(any(RollbackRequest.class))).thenReturn(failureResult);

        // 执行Controller方法
        var response = jobVersionController.rollback(request);

        // 验证结果 - Controller会将Service的failureResult包装到ApiResponse的error响应中
        assertThat(response.getCode()).isEqualTo(1);  // 使用Service返回的resultCode
        assertThat(response.getMessage()).contains("失败");
        assertThat(response.getData()).isEqualTo(failureResult);

        // 验证Service方法被调用
        verify(jobVersionService).rollback(request);
    }
}
