package com.dataplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.entity.Job;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Job分页查询测试
 */
@SpringBootTest
class JobServicePaginationTest {

    @Autowired
    private JobService jobService;

    @Test
    void testPageSize_ShouldReturn10Records() {
        // Given: 请求第1页，每页10条
        JobQueryRequest request = new JobQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);

        // When: 查询作业列表
        Page<Job> result = jobService.page(request);

        // Debug: 打印分页信息
        System.out.println("=== Page 10 Test ===");
        System.out.println("Total records: " + result.getTotal());
        System.out.println("Returned records: " + result.getRecords().size());
        System.out.println("Page size: " + result.getSize());
        System.out.println("Current page: " + result.getCurrent());

        // Then: 验证分页参数正确
        assertNotNull(result);
        assertEquals(10, result.getSize(), "Page size should be 10");
        assertEquals(1, result.getCurrent(), "Current page should be 1");

        // 如果总记录数 >= 10，则返回10条；否则返回实际记录数
        if (result.getTotal() >= 10) {
            assertEquals(10, result.getRecords().size(),
                "Should return exactly 10 records when total >= 10");
        } else {
            assertTrue(result.getRecords().size() <= 10,
                "Should return at most 10 records");
        }
    }

    @Test
    void testPageSize_CustomSize5() {
        // Given: 请求第1页，每页5条
        JobQueryRequest request = new JobQueryRequest();
        request.setPageNum(1);
        request.setPageSize(5);

        // When: 查询作业列表
        Page<Job> result = jobService.page(request);

        // Debug: 打印分页信息
        System.out.println("=== Page 5 Test ===");
        System.out.println("Total records: " + result.getTotal());
        System.out.println("Returned records: " + result.getRecords().size());
        System.out.println("Page size: " + result.getSize());
        System.out.println("Current page: " + result.getCurrent());

        // Then: 验证分页参数正确
        assertNotNull(result);
        assertEquals(5, result.getSize(), "Page size should be 5");

        if (result.getTotal() >= 5) {
            assertEquals(5, result.getRecords().size(),
                "Should return exactly 5 records when total >= 5");
        } else {
            assertTrue(result.getRecords().size() <= 5,
                "Should return at most 5 records");
        }
    }
}
