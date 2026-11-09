package com.dataplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobCreateRequest;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.dto.JobUpdateRequest;
import com.dataplatform.entity.Job;
import com.dataplatform.entity.JobVersion;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.VersionStatus;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.mapper.JobMapper;
import com.dataplatform.mapper.JobVersionMapper;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.service.FieldMetadataService;
import com.dataplatform.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作业服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;
    private final JobVersionMapper jobVersionMapper;
    private final DataSourceService dataSourceService;
    private final FieldMetadataService fieldMetadataService;

    /**
     * 检查操作权限（仅创建者可操作）
     */
    private void checkOwnership(Job job, String operatorUserId, String operation) {
        if (job.getCreatorId() == null) {
            // 旧数据没有创建者信息，允许操作（降级策略）
            return;
        }

        if (!job.getCreatorId().equals(operatorUserId)) {
            throw new BusinessException(
                    String.format("无权限%s：该作业由用户 %s 创建，只有创建者可以操作",
                            operation, job.getCreatorId())
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job create(JobCreateRequest request, String creatorUserId) {
        // 检查作业名称是否重复
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobName, request.getJobName());
        if (jobMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("作业名称已存在");
        }

        // 校验数据源：DB2HIVE 必须选择源数据源
        if (request.getJobType() == JobType.DB2HIVE && request.getSourceDatasourceId() == null) {
            throw new BusinessException("DB2HIVE 作业必须选择源数据源");
        }

        // 检查数据源是否存在（HIVE2HIVE 无源数据源）
        if (request.getSourceDatasourceId() != null) {
            dataSourceService.getById(request.getSourceDatasourceId());
        }

        // 如果目标表名为空，自动设置为作业名称
        if (request.getTargetTable() == null || request.getTargetTable().trim().isEmpty()) {
            request.setTargetTable(request.getJobName());
        }

        // 如果monitored为空，默认设置为true
        if (request.getMonitored() == null) {
            request.setMonitored(true);
        }

        // 创建作业
        Job job = new Job();
        BeanUtils.copyProperties(request, job);
        job.setStatus(JobStatus.DRAFT);
        // 使用随机负数作为占位符，版本创建后再更新
        job.setJobVersionId((long) (Math.random() * -1000000));
        job.setCreatorId(creatorUserId); // 设置创建者ID

        jobMapper.insert(job);

        // 自动创建版本1
        JobVersion version = new JobVersion();
        version.setJobId(job.getId());
        version.setVersion(1);
        version.setStatus(VersionStatus.DRAFT);
        version.setChangeLog("初始版本");
        // 复制作业配置到版本
        version.setJobName(job.getJobName());
        version.setJobCnName(job.getJobCnName());
        version.setJobType(job.getJobType());
        version.setEngineType(job.getEngineType());
        version.setSourceDatasourceId(job.getSourceDatasourceId());
        version.setSourceDatabase(job.getSourceDatabase());
        version.setSourceTable(job.getSourceTable());
        version.setTargetDatabase(job.getTargetDatabase());
        version.setTargetTable(job.getTargetTable());
        version.setScheduleFrequency(job.getScheduleFrequency());
        version.setMonitored(job.getMonitored());
        version.setDescription(job.getDescription());
        jobVersionMapper.insert(version);

        // 更新作业的 jobVersionId 为真实版本ID
        job.setJobVersionId(version.getId());
        jobMapper.updateById(job);

        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job update(Long id, JobUpdateRequest request, String operatorUserId) {
        // 检查作业是否存在
        Job job = getById(id);

        // 检查操作权限
        checkOwnership(job, operatorUserId, "修改作业");

        // 检查作业状态（已发布的作业不能直接修改基本信息）
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new BusinessException("已发布的作业不能修改，请创建新版本");
        }

        // 如果修改了数据源，检查数据源是否存在
        if (request.getSourceDatasourceId() != null) {
            dataSourceService.getById(request.getSourceDatasourceId());
        }

        // 更新作业
        if (request.getJobCnName() != null) {
            job.setJobCnName(request.getJobCnName());
        }
        if (request.getJobType() != null) {
            job.setJobType(request.getJobType());
        }
        if (request.getSourceDatasourceId() != null) {
            job.setSourceDatasourceId(request.getSourceDatasourceId());
        }
        if (request.getSourceDatabase() != null) {
            job.setSourceDatabase(request.getSourceDatabase());
        }
        if (request.getSourceTable() != null) {
            job.setSourceTable(request.getSourceTable());
        }
        if (request.getTargetDatabase() != null) {
            job.setTargetDatabase(request.getTargetDatabase());
        }
        if (request.getTargetTable() != null) {
            job.setTargetTable(request.getTargetTable());
        }
        if (request.getEngineType() != null) {
            job.setEngineType(request.getEngineType());
        }
        if (request.getDescription() != null) {
            job.setDescription(request.getDescription());
        }
        if (request.getScheduleFrequency() != null) {
            job.setScheduleFrequency(request.getScheduleFrequency());
        }
        if (request.getMonitored() != null) {
            job.setMonitored(request.getMonitored());
        }

        jobMapper.updateById(job);
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String operatorUserId) {
        // 检查作业是否存在
        Job job = getById(id);

        // 检查操作权限
        checkOwnership(job, operatorUserId, "删除作业");

        // 检查作业状态（已发布的作业不能删除，需要先归档）
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new BusinessException("已发布的作业不能删除，请先归档");
        }

        // 删除作业的所有版本
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, id);
        List<JobVersion> versions = jobVersionMapper.selectList(wrapper);
        for (JobVersion version : versions) {
            // 删除版本的字段元数据
//            fieldMetadataService.deleteByVersionId(version.getId());
            // 删除版本
            jobVersionMapper.deleteById(version.getId());
        }

        // 删除作业
        jobMapper.deleteById(id);
    }

    @Override
    public Job getById(Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException("作业不存在");
        }
        return job;
    }

    @Override
    public Page<Job> page(JobQueryRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;

        // 计算分页参数
        Integer limit = pageSize;
        Integer offset = (pageNum - 1) * pageSize;

        // 查询数据列表
        List<Job> jobs = jobMapper.selectJobList(request, limit, offset);

        // 查询总数
        Long total = jobMapper.selectJobCount(request);

        // 填充分页对象
        Page<Job> page = new Page<>(pageNum, pageSize);
        page.setRecords(jobs);
        page.setTotal(total);

        // 填充 currentVersion（从 JobVersion 表查询版本号）
        page.getRecords().forEach(job -> {
            if (job.getJobVersionId() != null) {
                JobVersion version = jobVersionMapper.selectById(job.getJobVersionId());
                if (version != null) {
                    job.setCurrentVersion(version.getVersion());
                }
            }
        });

        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version, String operatorUserId) {
        // 检查作业是否存在
        Job job = getById(id);

        // 检查操作权限
        checkOwnership(job, operatorUserId, "发布作业");

        // 检查版本是否存在
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, id)
               .eq(JobVersion::getVersion, version);
        JobVersion jobVersion = jobVersionMapper.selectOne(wrapper);
        if (jobVersion == null) {
            throw new BusinessException("版本不存在");
        }

        // 检查版本是否已配置SQL和字段元数据
        if (!StringUtils.hasText(jobVersion.getSqlContent())) {
            throw new BusinessException("版本未配置SQL，无法发布");
        }

//        // 检查字段元数据
//        if (fieldMetadataService.listByVersionId(jobVersion.getId()).isEmpty()) {
//            throw new BusinessException("版本未配置字段元数据，无法发布");
//        }

        // 取消之前的发布版本
        LambdaQueryWrapper<JobVersion> publishedWrapper = new LambdaQueryWrapper<>();
        publishedWrapper.eq(JobVersion::getJobId, id)
                       .eq(JobVersion::getStatus, VersionStatus.PUBLISHED);
        List<JobVersion> publishedVersions = jobVersionMapper.selectList(publishedWrapper);
        for (JobVersion pv : publishedVersions) {
            pv.setStatus(VersionStatus.DRAFT);
            jobVersionMapper.updateById(pv);
        }

        // 发布当前版本
        jobVersion.setStatus(VersionStatus.PUBLISHED);
        jobVersionMapper.updateById(jobVersion);

        // 更新作业状态
        job.setStatus(JobStatus.PUBLISHED);
        jobMapper.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id, String operatorUserId) {
        // 检查作业是否存在
        Job job = getById(id);

        // 检查操作权限
        checkOwnership(job, operatorUserId, "归档作业");

        // 更新作业状态
        job.setStatus(JobStatus.ARCHIVED);
        jobMapper.updateById(job);
    }
}
