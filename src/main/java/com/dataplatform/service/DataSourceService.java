package com.dataplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.DataSourceRequest;
import com.dataplatform.entity.DataSource;

import java.util.List;

/**
 * 数据源服务接口
 */
public interface DataSourceService {

    /**
     * 创建数据源
     */
    DataSource create(DataSourceRequest request);

    /**
     * 更新数据源
     */
    DataSource update(Long id, DataSourceRequest request);

    /**
     * 删除数据源
     */
    void delete(Long id);

    /**
     * 根据ID查询数据源
     */
    DataSource getById(Long id);

    /**
     * 根据数据源编码查询数据源
     */
    DataSource getByCode(String sourceCode);

    /**
     * 分页查询数据源列表
     */
    Page<DataSource> page(int pageNum, int pageSize, String keyword);

    /**
     * 查询所有数据源
     */
    List<DataSource> listAll();

    /**
     * 根据数据源名称查询数据源ID
     */
    Long getIdByName(String sourceName);

    /**
     * 测试数据源连接
     */
    boolean testConnection(Long id);

    /**
     * 根据参数测试数据源连接（用于新增数据源时未保存的场景）
     */
    boolean testConnection(DataSourceRequest request);
}
