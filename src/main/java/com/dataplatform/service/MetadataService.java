package com.dataplatform.service;

import com.dataplatform.dto.TableInfoDTO;
import java.util.List;

/**
 * 元数据服务（Mock版）
 */
public interface MetadataService {

    /**
     * 获取指定数据源下的所有库名
     *
     * @param datasourceId 数据源ID
     * @return 库名列表
     */
    List<String> listDatabases(Long datasourceId);

    /**
     * 获取指定库下的所有表（含字段）
     *
     * @param datasourceId 数据源ID
     * @param database     库名
     * @return 表信息列表
     */
    List<TableInfoDTO> listTables(Long datasourceId, String database);
}
