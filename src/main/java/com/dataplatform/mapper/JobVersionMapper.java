package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.JobVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作业版本Mapper
 */
@Mapper
public interface JobVersionMapper extends BaseMapper<JobVersion> {
}
