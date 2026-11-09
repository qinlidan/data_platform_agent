package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业Mapper
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 查询作业列表（支持多条件筛选 + 右模糊查询）- 不分页
     * @param request 查询条件
     * @param limit 限制数量
     * @param offset 偏移量
     * @return 作业列表
     */
    List<Job> selectJobList(@Param("req") JobQueryRequest request,
                            @Param("limit") Integer limit,
                            @Param("offset") Integer offset);

    /**
     * 统计作业数量（支持多条件筛选）
     * @param request 查询条件
     * @return 总数
     */
    Long selectJobCount(@Param("req") JobQueryRequest request);
}
