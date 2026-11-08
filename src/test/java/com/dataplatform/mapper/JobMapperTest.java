package com.dataplatform.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.entity.Job;
import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobMapper XML 查询测试
 */
@SpringBootTest
@Transactional
class JobMapperTest {

    @Autowired
    private JobMapper jobMapper;

    private final List<Long> testJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 清理测试数据
        for (Long id : testJobIds) {
            jobMapper.deleteById(id);
        }
        testJobIds.clear();
    }

    @Test
    void selectJobPage_作业名称右模糊查询() {
        // 插入测试数据（使用唯一前缀避免与已有数据冲突）
        String testPrefix = "mapper_test_user_";
        Job job1 = createJob(testPrefix + "sync_daily", "用户同步", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob(testPrefix + "export_hourly", "用户导出", JobType.HIVE2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        Job job3 = createJob("mapper_test_order_sync_daily", "订单同步", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job3);
        testJobIds.add(job3.getId());

        // 查询：jobName 以 "mapper_test_user_" 开头
        JobQueryRequest request = new JobQueryRequest();
        request.setJobName(testPrefix);
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        // 验证：应该只返回 mapper_test_user_ 开头的2条
        assertThat(result.getRecords())
                .filteredOn(j -> j.getJobName().startsWith(testPrefix))
                .hasSize(2);
    }

    @Test
    void selectJobPage_作业中文名右模糊查询() {
        String testPrefix = "mapper_test_";
        Job job1 = createJob(testPrefix + "job1", "测试用户数据同步", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob(testPrefix + "job2", "测试用户画像分析", JobType.HIVE2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        Job job3 = createJob(testPrefix + "job3", "测试订单数据同步", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job3);
        testJobIds.add(job3.getId());

        // 查询：jobCnName 以 "测试用户" 开头
        JobQueryRequest request = new JobQueryRequest();
        request.setJobCnName("测试用户");
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        assertThat(result.getRecords())
                .filteredOn(j -> j.getJobCnName().startsWith("测试用户"))
                .hasSize(2);
    }

    @Test
    void selectJobPage_多条件组合查询() {
        // 插入测试数据
        String testPrefix = "mapper_test_combo_";
        Job job1 = createJob(testPrefix + "user_sync", "组合用户同步", JobType.DB2HIVE, JobStatus.PUBLISHED);
        job1.setSourceDatabase("mapper_test_db");
        job1.setSourceTable("user_table");
        job1.setScheduleFrequency(ScheduleFrequency.DAILY);
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob(testPrefix + "user_export", "组合用户导出", JobType.HIVE2HIVE, JobStatus.DRAFT);
        job2.setSourceDatabase("mapper_test_db");
        job2.setSourceTable("user_table");
        job2.setScheduleFrequency(ScheduleFrequency.WEEKLY);
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        Job job3 = createJob(testPrefix + "order_sync", "组合订单同步", JobType.DB2HIVE, JobStatus.PUBLISHED);
        job3.setSourceDatabase("mapper_test_db");
        job3.setSourceTable("order_table");
        job3.setScheduleFrequency(ScheduleFrequency.DAILY);
        jobMapper.insert(job3);
        testJobIds.add(job3.getId());

        // 查询：jobName 以 testPrefix + "user" 开头 + jobType=DB2HIVE + status=PUBLISHED + scheduleFrequency=DAILY
        JobQueryRequest request = new JobQueryRequest();
        request.setJobName(testPrefix + "user");
        request.setJobType(JobType.DB2HIVE);
        request.setStatus(JobStatus.PUBLISHED);
        request.setScheduleFrequency(ScheduleFrequency.DAILY);
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        // 验证：应该只返回 mapper_test_combo_user_sync
        assertThat(result.getRecords())
                .filteredOn(j -> j.getJobName().equals(testPrefix + "user_sync"))
                .hasSize(1);
    }

    @Test
    void selectJobPage_源表名右模糊查询() {
        String testPrefix = "mapper_test_table_";
        Job job1 = createJob(testPrefix + "job1", "作业1", JobType.DB2HIVE, JobStatus.DRAFT);
        job1.setSourceTable(testPrefix + "user_base_info");
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob(testPrefix + "job2", "作业2", JobType.DB2HIVE, JobStatus.DRAFT);
        job2.setSourceTable(testPrefix + "user_profile");
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        Job job3 = createJob(testPrefix + "job3", "作业3", JobType.DB2HIVE, JobStatus.DRAFT);
        job3.setSourceTable(testPrefix + "order_detail");
        jobMapper.insert(job3);
        testJobIds.add(job3.getId());

        // 查询：sourceTable 以 testPrefix + "user" 开头
        JobQueryRequest request = new JobQueryRequest();
        request.setSourceTable(testPrefix + "user");
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        assertThat(result.getRecords())
                .filteredOn(j -> j.getSourceTable().startsWith(testPrefix + "user"))
                .hasSize(2);
    }

    @Test
    void selectJobPage_源库名精确查询() {
        String testDb = "mapper_test_database_unique";
        Job job1 = createJob("mapper_test_dbquery_job1", "作业1", JobType.DB2HIVE, JobStatus.DRAFT);
        job1.setSourceDatabase(testDb);
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob("mapper_test_dbquery_job2", "作业2", JobType.DB2HIVE, JobStatus.DRAFT);
        job2.setSourceDatabase("other_db");
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        // 查询：sourceDatabase=testDb
        JobQueryRequest request = new JobQueryRequest();
        request.setSourceDatabase(testDb);
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        assertThat(result.getRecords())
                .filteredOn(j -> testDb.equals(j.getSourceDatabase()))
                .hasSize(1);
    }

    @Test
    void selectJobPage_验证右模糊匹配() {
        // 验证不会左匹配
        String testPrefix = "prefix_";
        Job job1 = createJob(testPrefix + "target", "目标作业", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job1);
        testJobIds.add(job1.getId());

        Job job2 = createJob("not_matching_" + testPrefix + "target", "不匹配", JobType.DB2HIVE, JobStatus.DRAFT);
        jobMapper.insert(job2);
        testJobIds.add(job2.getId());

        // 查询：只应该匹配以 testPrefix 开头的
        JobQueryRequest request = new JobQueryRequest();
        request.setJobName(testPrefix);
        request.setPageNum(1);
        request.setPageSize(10);

        Page<Job> page = new Page<>(1, 10);
        Page<Job> result = jobMapper.selectJobPage(page, request);

        // 不应该包含 "not_matching_" 开头的
        assertThat(result.getRecords())
                .filteredOn(j -> j.getJobName().startsWith(testPrefix))
                .hasSize(1);
        assertThat(result.getRecords())
                .filteredOn(j -> j.getJobName().contains("not_matching"))
                .isEmpty();
    }

    private Job createJob(String jobName, String jobCnName, JobType jobType, JobStatus status) {
        Job job = new Job();
        job.setJobName(jobName);
        job.setJobCnName(jobCnName);
        job.setJobType(jobType);
        job.setStatus(status);
        job.setEngineType(EngineType.HIVE);
        job.setSourceDatabase("test_db");
        job.setSourceTable("test_table");
        job.setTargetDatabase("target_db");
        job.setTargetTable("target_table");
        job.setJobVersionId(-1L);
        return job;
    }
}
