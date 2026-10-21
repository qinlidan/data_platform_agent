package com.dataplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.dto.FieldMetadataRequest;
import com.dataplatform.entity.FieldMetadata;
import com.dataplatform.mapper.FieldMetadataMapper;
import com.dataplatform.service.FieldMetadataService;
import com.dataplatform.util.SqlParserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字段元数据服务实现类
 */
@Service
@RequiredArgsConstructor
public class FieldMetadataServiceImpl implements FieldMetadataService {

    private final FieldMetadataMapper fieldMetadataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long jobVersionId, List<FieldMetadataRequest> fields) {
        // 先删除该版本的所有字段元数据
        deleteByVersionId(jobVersionId);

        // 批量保存新的字段元数据
        if (fields != null && !fields.isEmpty()) {
            for (FieldMetadataRequest request : fields) {
                FieldMetadata metadata = new FieldMetadata();
                BeanUtils.copyProperties(request, metadata);
                metadata.setJobVersionId(jobVersionId);

                fieldMetadataMapper.insert(metadata);
            }
        }
    }

    @Override
    public List<FieldMetadata> listByVersionId(Long jobVersionId) {
        LambdaQueryWrapper<FieldMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FieldMetadata::getJobVersionId, jobVersionId)
               .orderByAsc(FieldMetadata::getId);
        return fieldMetadataMapper.selectList(wrapper);
    }

    @Override
    public List<String> parseFieldsFromSql(String sql) {
        return SqlParserUtil.parseFieldsFromSql(sql);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByVersionId(Long jobVersionId) {
        LambdaQueryWrapper<FieldMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FieldMetadata::getJobVersionId, jobVersionId);
        fieldMetadataMapper.delete(wrapper);
    }
}
