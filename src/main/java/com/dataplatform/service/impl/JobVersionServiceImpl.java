package com.dataplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.dto.SqlConfigRequest;
import com.dataplatform.entity.Job;
import com.dataplatform.entity.JobVersion;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.mapper.JobMapper;
import com.dataplatform.mapper.JobVersionMapper;
import com.dataplatform.service.FieldMetadataService;
import com.dataplatform.service.JobVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 作业版本服务实现类
 */
@Service
@RequiredArgsConstructor
public class JobVersionServiceImpl implements JobVersionService {

    private final JobVersionMapper jobVersionMapper;
    private final JobMapper jobMapper;
    private final FieldMetadataService fieldMetadataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobVersion configureSql(Long jobId, SqlConfigRequest request) {
        // 检查作业是否存在
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("作业不存在");
        }

        // 检查作业状态（已归档的作业不能配置）
        if (job.getStatus() == JobStatus.ARCHIVED) {
            throw new BusinessException("已归档的作业不能配置");
        }

        // 获取当前版本
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, jobId)
               .eq(JobVersion::getVersion, job.getCurrentVersion());
        JobVersion currentVersion = jobVersionMapper.selectOne(wrapper);

        // 如果当前版本已发布，创建新版本
        if (currentVersion.getIsPublished() != null && currentVersion.getIsPublished() == 1) {
            JobVersion newVersion = new JobVersion();
            newVersion.setJobId(jobId);
            newVersion.setVersion(job.getCurrentVersion() + 1);
            newVersion.setSqlContent(request.getSqlContent());
            newVersion.setSparkConfig(request.getSparkConfig());
            newVersion.setChangeLog(request.getChangeLog());
            newVersion.setIsPublished(0);

            jobVersionMapper.insert(newVersion);

            // 更新作业当前版本
            job.setCurrentVersion(newVersion.getVersion());
            jobMapper.updateById(job);

            return newVersion;
        } else {
            // 更新当前版本
            currentVersion.setSqlContent(request.getSqlContent());
            currentVersion.setSparkConfig(request.getSparkConfig());
            currentVersion.setChangeLog(request.getChangeLog());

            jobVersionMapper.updateById(currentVersion);
            return currentVersion;
        }
    }

    @Override
    public List<JobVersion> listByJobId(Long jobId) {
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, jobId)
               .orderByDesc(JobVersion::getVersion);
        return jobVersionMapper.selectList(wrapper);
    }

    @Override
    public JobVersion getById(Long id) {
        JobVersion version = jobVersionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        // 检查版本是否存在
        JobVersion version = getById(id);

        // 检查作业是否存在
        Job job = jobMapper.selectById(version.getJobId());
        if (job == null) {
            throw new BusinessException("作业不存在");
        }

        // 检查版本是否已配置SQL
        if (version.getSqlContent() == null || version.getSqlContent().trim().isEmpty()) {
            throw new BusinessException("版本未配置SQL，无法发布");
        }

        // 检查字段元数据
        if (fieldMetadataService.listByVersionId(id).isEmpty()) {
            throw new BusinessException("版本未配置字段元数据，无法发布");
        }

        // 取消之前的发布版本
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, version.getJobId())
               .eq(JobVersion::getIsPublished, 1);
        List<JobVersion> publishedVersions = jobVersionMapper.selectList(wrapper);
        for (JobVersion pv : publishedVersions) {
            pv.setIsPublished(0);
            jobVersionMapper.updateById(pv);
        }

        // 发布当前版本
        version.setIsPublished(1);
        jobVersionMapper.updateById(version);

        // 更新作业状态
        job.setStatus(JobStatus.PUBLISHED);
        job.setPublishedVersion(version.getVersion());
        jobMapper.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查版本是否存在
        JobVersion version = getById(id);

        // 检查版本是否已发布
        if (version.getIsPublished() != null && version.getIsPublished() == 1) {
            throw new BusinessException("已发布的版本不能删除");
        }

        // 检查是否是作业的唯一版本
        LambdaQueryWrapper<JobVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobVersion::getJobId, version.getJobId());
        long count = jobVersionMapper.selectCount(wrapper);
        if (count <= 1) {
            throw new BusinessException("作业至少需要保留一个版本");
        }

        // 删除版本的字段元数据
        fieldMetadataService.deleteByVersionId(id);

        // 删除版本
        jobVersionMapper.deleteById(id);

        // 如果删除的是当前版本，需要更新作业的当前版本号
        Job job = jobMapper.selectById(version.getJobId());
        if (job.getCurrentVersion().equals(version.getVersion())) {
            // 查找最新的版本
            LambdaQueryWrapper<JobVersion> latestWrapper = new LambdaQueryWrapper<>();
            latestWrapper.eq(JobVersion::getJobId, version.getJobId())
                        .orderByDesc(JobVersion::getVersion)
                        .last("LIMIT 1");
            JobVersion latestVersion = jobVersionMapper.selectOne(latestWrapper);

            job.setCurrentVersion(latestVersion.getVersion());
            jobMapper.updateById(job);
        }
    }
}
