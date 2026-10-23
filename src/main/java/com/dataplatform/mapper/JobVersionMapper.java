package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.JobVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 作业版本Mapper
 */
@Mapper
public interface JobVersionMapper extends BaseMapper<JobVersion> {

    /**
     * 调用存储过程：版本回滚
     *
     * @param params 参数Map，包含：
     *               - p_job_id: 作业ID
     *               - p_target_version: 目标版本号
     *               - p_operator_id: 操作人ID
     *               - p_operator_name: 操作人名称
     *               - p_rollback_reason: 回滚原因
     */
    @Select("CALL sp_rollback_job_version(" +
            "#{p_job_id}, " +
            "#{p_target_version}, " +
            "#{p_operator_id}, " +
            "#{p_operator_name}, " +
            "#{p_rollback_reason}, " +
            "@p_new_version, " +
            "@p_result_code, " +
            "@p_result_msg)")
    void callRollbackProcedure(@Param("p_job_id") Long jobId,
                               @Param("p_target_version") Integer targetVersion,
                               @Param("p_operator_id") Long operatorId,
                               @Param("p_operator_name") String operatorName,
                               @Param("p_rollback_reason") String rollbackReason);

    /**
     * 获取存储过程的输出参数
     */
    @Select("SELECT @p_new_version AS newVersion, @p_result_code AS resultCode, @p_result_msg AS resultMsg")
    Map<String, Object> getRollbackResult();
}

