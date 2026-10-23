package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.JobVersionHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作业版本历史Mapper
 */
@Mapper
public interface JobVersionHistoryMapper extends BaseMapper<JobVersionHistory> {
}
