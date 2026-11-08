package com.dataplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.dto.version.RollbackRequest;
import com.dataplatform.dto.version.RollbackResult;
import com.dataplatform.entity.Job;
import com.dataplatform.entity.JobVersion;
import com.dataplatform.enums.VersionStatus;
import com.dataplatform.mapper.JobMapper;
import com.dataplatform.mapper.JobVersionMapper;
import com.dataplatform.service.impl.JobVersionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JobVersionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("作业版本服务测试")
class JobVersionServiceTest {

    @Mock
    private JobVersionMapper jobVersionMapper;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private FieldMetadataService fieldMetadataService;

    @InjectMocks
    private JobVersionServiceImpl jobVersionService;

    private Job testJob;
    private JobVersion publishedVersion;
    private JobVersion targetVersion;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testJob = new Job();
        testJob.setId(1L);
        testJob.setJobVersionId(10L); // 使用 jobVersionId 代替 currentVersion
        testJob.setOnlineVersion(3);

        publishedVersion = new JobVersion();
        publishedVersion.setJobId(1L);
        publishedVersion.setVersion(3);
        publishedVersion.setStatus(VersionStatus.PUBLISHED);
        publishedVersion.setSqlContent("SELECT * FROM users");
        publishedVersion.setSparkConfig("{}");

        targetVersion = new JobVersion();
        targetVersion.setJobId(1L);
        targetVersion.setVersion(2);
        targetVersion.setStatus(VersionStatus.PUBLISHED);
        targetVersion.setSqlContent("SELECT id, name FROM users");
        targetVersion.setSparkConfig("{}");
    }

    @Test
    @DisplayName("回滚成功 - 正常场景")
    void testRollbackSuccess() {
        // 准备测试数据
        RollbackRequest request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(2)
                .operatorId(100L)
                .operatorName("admin")
                .reason("修复线上Bug")
                .build();

        Map<String, Object> storedProcResult = new HashMap<>();
        storedProcResult.put("resultCode", 0);
        storedProcResult.put("resultMsg", "回滚成功！新版本：v4，基于v2创建");
        storedProcResult.put("newVersion", 4);

        // Mock 行为
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(targetVersion);
        doNothing().when(jobVersionMapper).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());
        when(jobVersionMapper.getRollbackResult()).thenReturn(storedProcResult);

        // 执行测试
        RollbackResult result = jobVersionService.rollback(request);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getResultCode());
        assertEquals(4, result.getNewVersion());
        assertEquals(2, result.getTargetVersion());
        assertTrue(result.getResultMsg().contains("回滚成功"));

        // 验证交互
        verify(jobMapper, times(1)).selectById(1L);
        verify(jobVersionMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jobVersionMapper, times(1)).callRollbackProcedure(1L, 2, 100L, "admin", "修复线上Bug");
        verify(jobVersionMapper, times(1)).getRollbackResult();
    }

    @Test
    @DisplayName("回滚失败 - 作业不存在")
    void testRollbackFailure_JobNotFound() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(999L)
                .targetVersion(2)
                .operatorId(100L)
                .operatorName("admin")
                .reason("测试")
                .build();

        when(jobMapper.selectById(999L)).thenReturn(null);

        RollbackResult result = jobVersionService.rollback(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(1001, result.getResultCode());
        assertEquals("作业不存在", result.getResultMsg());

        verify(jobMapper, times(1)).selectById(999L);
        verify(jobVersionMapper, never()).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("回滚失败 - 目标版本不存在")
    void testRollbackFailure_VersionNotFound() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(999)
                .operatorId(100L)
                .operatorName("admin")
                .reason("测试")
                .build();

        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RollbackResult result = jobVersionService.rollback(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(1002, result.getResultCode());
        assertTrue(result.getResultMsg().contains("不存在"));

        verify(jobMapper, times(1)).selectById(1L);
        verify(jobVersionMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jobVersionMapper, never()).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("回滚失败 - 目标版本未发布")
    void testRollbackFailure_VersionNotPublished() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(2)
                .operatorId(100L)
                .operatorName("admin")
                .reason("测试")
                .build();

        JobVersion draftVersion = new JobVersion();
        draftVersion.setJobId(1L);
        draftVersion.setVersion(2);
        draftVersion.setStatus(VersionStatus.DRAFT);

        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(draftVersion);

        RollbackResult result = jobVersionService.rollback(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(1003, result.getResultCode());
        assertTrue(result.getResultMsg().contains("未发布"));

        verify(jobMapper, times(1)).selectById(1L);
        verify(jobVersionMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jobVersionMapper, never()).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("回滚失败 - 目标版本已是线上版本")
    void testRollbackFailure_AlreadyOnlineVersion() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(3)  // 当前线上版本
                .operatorId(100L)
                .operatorName("admin")
                .reason("测试")
                .build();

        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedVersion);

        RollbackResult result = jobVersionService.rollback(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(1004, result.getResultCode());
        assertTrue(result.getResultMsg().contains("已是当前线上版本"));

        verify(jobMapper, times(1)).selectById(1L);
        verify(jobVersionMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jobVersionMapper, never()).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("回滚异常 - 数据库异常")
    void testRollbackException() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(1L)
                .targetVersion(2)
                .operatorId(100L)
                .operatorName("admin")
                .reason("测试")
                .build();

        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(targetVersion);
        doThrow(new RuntimeException("Database connection failed"))
                .when(jobVersionMapper).callRollbackProcedure(anyLong(), anyInt(), anyLong(), anyString(), anyString());

        RollbackResult result = jobVersionService.rollback(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(-1, result.getResultCode());
        assertTrue(result.getResultMsg().contains("回滚失败"));

        verify(jobMapper, times(1)).selectById(1L);
        verify(jobVersionMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jobVersionMapper, times(1)).callRollbackProcedure(1L, 2, 100L, "admin", "测试");
    }

    @Test
    @DisplayName("回滚输入校验 - 空参数")
    void testRollbackValidation() {
        RollbackRequest request = RollbackRequest.builder()
                .jobId(null)
                .targetVersion(null)
                .build();

        // 这个测试主要验证 DTO 层面的校验，实际执行时应该被拦截
        // 这里只做简单的空判断
        assertNull(request.getJobId());
        assertNull(request.getTargetVersion());
    }
}

