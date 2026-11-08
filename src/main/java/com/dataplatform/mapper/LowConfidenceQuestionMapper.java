package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.LowConfidenceQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 低置信度问题池 Mapper
 */
@Mapper
public interface LowConfidenceQuestionMapper extends BaseMapper<LowConfidenceQuestion> {
}
