package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.UsedConfirmationToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 已使用确认 Token Mapper
 */
@Mapper
public interface UsedConfirmationTokenMapper extends BaseMapper<UsedConfirmationToken> {
}
