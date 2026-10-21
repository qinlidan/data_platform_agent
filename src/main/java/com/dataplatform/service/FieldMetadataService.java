package com.dataplatform.service;

import com.dataplatform.dto.FieldMetadataRequest;
import com.dataplatform.entity.FieldMetadata;

import java.util.List;

/**
 * 字段元数据服务接口
 */
public interface FieldMetadataService {

    /**
     * 批量保存字段元数据
     */
    void batchSave(Long jobVersionId, List<FieldMetadataRequest> fields);

    /**
     * 根据版本ID查询字段元数据
     */
    List<FieldMetadata> listByVersionId(Long jobVersionId);

    /**
     * 从SQL解析字段
     */
    List<String> parseFieldsFromSql(String sql);

    /**
     * 删除版本的所有字段元数据
     */
    void deleteByVersionId(Long jobVersionId);
}
