package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.Job;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作业Mapper
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {
}
