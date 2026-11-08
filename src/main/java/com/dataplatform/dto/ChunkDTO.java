package com.dataplatform.dto;

import lombok.Data;

/**
 * 知识库切块数据传输对象
 * Task 2 - 实体类与MyBatis Mapper
 *
 * 用于：切分器输出、API传输、业务层数据交换
 */
@Data
public class ChunkDTO {

    /**
     * 文档类型：spark调优|数据研发平台用户手册
     */
    private String docType;

    /**
     * 文件名（不含扩展名）
     */
    private String docId;

    /**
     * 一级标题
     */
    private String category;

    /**
     * 二级标题类型枚举：error|slow|pending|boundary|guide|concept|faq
     */
    private String sceneType;

    /**
     * 二级标题中文名
     */
    private String sceneTitle;

    /**
     * 现象描述（可空）
     */
    private String symptomText;

    /**
     * 三级标题或问法
     */
    private String questions;

    /**
     * 回答正文
     */
    private String answer;

    /**
     * 章节路径
     */
    private String sectionPath;

    /**
     * 内容类型：faq | policy | manual
     */
    private String contentType;

    /**
     * 是否可调优
     */
    private Boolean isTunable;

    /**
     * 路由动作：tunable | not_tunable | escalate | guide
     */
    private String routeAction;
}
