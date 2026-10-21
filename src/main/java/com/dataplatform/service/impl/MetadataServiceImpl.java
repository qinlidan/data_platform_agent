package com.dataplatform.service.impl;

import com.dataplatform.dto.TableFieldDTO;
import com.dataplatform.dto.TableInfoDTO;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.service.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 元数据服务实现（Mock版）
 */
@Slf4j
@Service
public class MetadataServiceImpl implements MetadataService {

    @Override
    public List<String> listDatabases(Long datasourceId) {
        if (datasourceId == null) {
            throw new BusinessException("数据源ID不能为空");
        }

        // MOCK: 每个数据源固定返回 5 个库名
        log.info("MOCK: 获取数据源 {} 的库列表", datasourceId);
        return List.of("PRODDB", "TESTDB", "DEVDB", "ARCHDB", "REPORTDB");
    }

    @Override
    public List<TableInfoDTO> listTables(Long datasourceId, String database) {
        if (datasourceId == null) {
            throw new BusinessException("数据源ID不能为空");
        }
        if (!StringUtils.hasText(database)) {
            throw new BusinessException("库名不能为空");
        }

        // MOCK: 每个库固定返回 5 张表
        log.info("MOCK: 获取数据源 {} 库 {} 的表列表", datasourceId, database);

        List<TableInfoDTO> tables = new ArrayList<>();

        // 表1: user_info
        TableInfoDTO userInfo = new TableInfoDTO();
        userInfo.setTableName("user_info");
        userInfo.setTableComment("用户信息表");
        userInfo.setFields(List.of(
                createField("id", "BIGINT", "用户ID", true, false),
                createField("user_name", "VARCHAR(100)", "用户名", false, false),
                createField("phone", "VARCHAR(20)", "手机号", false, true),
                createField("email", "VARCHAR(100)", "邮箱", false, true),
                createField("status", "INT", "状态(0禁用1启用)", false, false),
                createField("create_time", "DATETIME", "创建时间", false, false)
        ));
        tables.add(userInfo);

        // 表2: order_main
        TableInfoDTO orderMain = new TableInfoDTO();
        orderMain.setTableName("order_main");
        orderMain.setTableComment("订单主表");
        orderMain.setFields(List.of(
                createField("order_id", "BIGINT", "订单ID", true, false),
                createField("order_no", "VARCHAR(50)", "订单号", false, false),
                createField("user_id", "BIGINT", "用户ID", false, false),
                createField("total_amount", "DECIMAL(10,2)", "订单总金额", false, false),
                createField("order_status", "INT", "订单状态", false, false),
                createField("create_time", "DATETIME", "创建时间", false, false)
        ));
        tables.add(orderMain);

        // 表3: order_detail
        TableInfoDTO orderDetail = new TableInfoDTO();
        orderDetail.setTableName("order_detail");
        orderDetail.setTableComment("订单明细表");
        orderDetail.setFields(List.of(
                createField("detail_id", "BIGINT", "明细ID", true, false),
                createField("order_id", "BIGINT", "订单ID", false, false),
                createField("product_id", "BIGINT", "商品ID", false, false),
                createField("quantity", "INT", "数量", false, false),
                createField("unit_price", "DECIMAL(10,2)", "单价", false, false),
                createField("amount", "DECIMAL(10,2)", "小计金额", false, false)
        ));
        tables.add(orderDetail);

        // 表4: product_info
        TableInfoDTO productInfo = new TableInfoDTO();
        productInfo.setTableName("product_info");
        productInfo.setTableComment("商品信息表");
        productInfo.setFields(List.of(
                createField("product_id", "BIGINT", "商品ID", true, false),
                createField("product_code", "VARCHAR(50)", "商品编码", false, false),
                createField("product_name", "VARCHAR(200)", "商品名称", false, false),
                createField("price", "DECIMAL(10,2)", "价格", false, false),
                createField("stock", "INT", "库存数量", false, false),
                createField("category", "VARCHAR(50)", "商品类别", false, true)
        ));
        tables.add(productInfo);

        // 表5: dept_info
        TableInfoDTO deptInfo = new TableInfoDTO();
        deptInfo.setTableName("dept_info");
        deptInfo.setTableComment("部门信息表");
        deptInfo.setFields(List.of(
                createField("dept_id", "BIGINT", "部门ID", true, false),
                createField("dept_code", "VARCHAR(50)", "部门编码", false, false),
                createField("dept_name", "VARCHAR(100)", "部门名称", false, false),
                createField("parent_id", "BIGINT", "上级部门ID", false, true),
                createField("leader", "VARCHAR(50)", "部门负责人", false, true)
        ));
        tables.add(deptInfo);

        return tables;
    }

    /**
     * 快捷创建字段DTO
     */
    private TableFieldDTO createField(String name, String type, String comment,
                                      boolean isPrimaryKey, boolean isNullable) {
        TableFieldDTO field = new TableFieldDTO();
        field.setFieldName(name);
        field.setFieldType(type);
        field.setFieldComment(comment);
        field.setIsPrimaryKey(isPrimaryKey);
        field.setIsNullable(isNullable);
        return field;
    }
}
