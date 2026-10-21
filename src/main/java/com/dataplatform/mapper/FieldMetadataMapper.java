package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.FieldMetadata;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段元数据Mapper
 */
@Mapper
public interface FieldMetadataMapper extends BaseMapper<FieldMetadata> {
}
