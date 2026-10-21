package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.DataSourceType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源实体
 */
@Data
@TableName("data_source")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceCode;

    private String sourceName;

    @TableField("source_type")
    private DataSourceType sourceType;

    private String driver;

    private String jdbcUrl;

    @TableField("config_info")
    private String configInfo;

    private String username;

    private String password;

    private String description;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
