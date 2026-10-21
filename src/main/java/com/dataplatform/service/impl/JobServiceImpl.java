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
import com.dataplatform.exception.BusinessException;
import com.dataplatform.mapper.JobMapper;
import com.dataplatform.mapper.JobVersionMapper;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.service.FieldMetadataService;
import com.dataplatform.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作业服务实现类
 */
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;
    private final JobVersionMapper jobVersionMapper;
    private final DataSourceService dataSourceService;
    private final FieldMetadataService fieldMetadataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job create(JobCreateRequest request) {
        // 检查作业编码是否重复
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobCode, request.getJobCode());
        if (jobMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("作业编码已存在");
        }

        // 校验数据源：DB2HIVE 必须选择源数据源
        if (request.getJobType() == JobType.DB2HIVE && request.getSourceDatasourceId() == null) {
            throw new BusinessException("DB2HIVE 作业必须选择源数据源");
        }

        // 检查数据源是否存在（HIVE2HIVE 无源数据源）
        if (request.getSourceDatasourceId() != null) {
            dataSourceService.getById(request.getSourceDatasourceId());
        }

        // 创建作业
        Job job = new Job();
        BeanUtils.copyProperties(request, job);
        job.setStatus(JobStatus.DRAFT);
        job.setCurrentVersion(0);
        job.setPublishedVersion(0);

        jobMapper.insert(job);

        // 自动创建版本1
        JobVersion version = new JobVersion();
        version.setJobId(job.getId());
        version.setVersion(1);
        version.setIsPublished(0);
        version.setChangeLog("初始版本");
        jobVersionMapper.insert(version);

        // 更新作业当前版本
        job.setCurrentVersion(1);
        jobMapper.updateById(job);

        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job update(Long id, JobUpdateRequest request) {
        // 检查作业是否存在
        Job job = getById(id);

        // 检查作业状态（已发布的作业不能直接修改基本信息）
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new BusinessException("已发布的作业不能修改，请创建新版本");
        }

        // 如果修改了数据源，检查数据源是否存在
        if (request.getSourceDatasourceId() != null) {
            dataSourceService.getById(request.getSourceDatasourceId());
        }

        // 更新作业
        if (request.getJobName() != null) {
            job.setJobName(request.getJobName());
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

        jobMapper.updateById(job);
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查作业是否存在
        Job job = getById(id);

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
            fieldMetadataService.deleteByVersionId(version.getId());
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
    public Job getByCode(String jobCode) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobCode, jobCode);
        return jobMapper.selectOne(wrapper);
    }

    @Override
    public Page<Job> page(JobQueryRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;

        Page<Job> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like(Job::getJobCode, request.getKeyword())
                             .or()
                             .like(Job::getJobName, request.getKeyword()));
        }

        // 作业类型筛选
        if (StringUtils.hasText(request.getJobType())) {
            wrapper.eq(Job::getJobType, JobType.valueOf(request.getJobType()));
        }

        // 作业状态筛选
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Job::getStatus, JobStatus.valueOf(request.getStatus()));
        }

        // 源数据源ID筛选
        if (request.getSourceDatasourceId() != null) {
            wrapper.eq(Job::getSourceDatasourceId, request.getSourceDatasourceId());
        }

        // 源库名筛选
        if (StringUtils.hasText(request.getSourceDatabase())) {
            wrapper.eq(Job::getSourceDatabase, request.getSourceDatabase());
        }

        // 源表名筛选
        if (StringUtils.hasText(request.getSourceTable())) {
            wrapper.like(Job::getSourceTable, request.getSourceTable());
        }

        // 作业频率筛选
        if (StringUtils.hasText(request.getScheduleFrequency())) {
            wrapper.eq(Job::getScheduleFrequency, request.getScheduleFrequency());
        }

        wrapper.orderByDesc(Job::getCreateTime);
        return jobMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version) {
        // 检查作业是否存在
        Job job = getById(id);

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

        // 检查字段元数据
        if (fieldMetadataService.listByVersionId(jobVersion.getId()).isEmpty()) {
            throw new BusinessException("版本未配置字段元数据，无法发布");
        }

        // 取消之前的发布版本
        LambdaQueryWrapper<JobVersion> publishedWrapper = new LambdaQueryWrapper<>();
        publishedWrapper.eq(JobVersion::getJobId, id)
                       .eq(JobVersion::getIsPublished, 1);
        List<JobVersion> publishedVersions = jobVersionMapper.selectList(publishedWrapper);
        for (JobVersion pv : publishedVersions) {
            pv.setIsPublished(0);
            jobVersionMapper.updateById(pv);
        }

        // 发布当前版本
        jobVersion.setIsPublished(1);
        jobVersionMapper.updateById(jobVersion);

        // 更新作业状态
        job.setStatus(JobStatus.PUBLISHED);
        job.setPublishedVersion(version);
        jobMapper.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        // 检查作业是否存在
        Job job = getById(id);

        // 更新作业状态
        job.setStatus(JobStatus.ARCHIVED);
        jobMapper.updateById(job);
    }
}
