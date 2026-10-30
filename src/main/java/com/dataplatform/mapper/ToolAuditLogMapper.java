package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.ToolAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用审计日志 Mapper
 *
 * <p>提供基础的 CRUD 操作，后续可扩展自定义查询方法，如：
 * - 按工具名统计调用次数
 * - 按用户查询调用历史
 * - 按时间范围统计成功率
 * - 按状态分组统计
 */
@Mapper
public interface ToolAuditLogMapper extends BaseMapper<ToolAuditLog> {
    // 继承 BaseMapper 提供基础 CRUD
    // 后续可添加自定义查询方法
}
