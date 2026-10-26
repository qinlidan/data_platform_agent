package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.UsedConfirmationToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 已用确认令牌 Mapper
 */
@Mapper
public interface UsedConfirmationTokenMapper extends BaseMapper<UsedConfirmationToken> {
}
