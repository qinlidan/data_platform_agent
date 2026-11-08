package com.dataplatform.chunker;

/**
 * Scene Type映射器
 * 根据二级标题和文档类型推断scene_type
 */
public class SceneTypeMapper {

    /**
     * 根据关键词映射scene_type
     * @param sceneTitle 二级标题中文名
     * @param docType 文档类型
     * @return scene_type枚举值
     */
    public static String inferSceneType(String sceneTitle, String docType) {
        if (sceneTitle == null) {
            return getDefaultSceneType(docType);
        }

        // 报错类
        if (sceneTitle.contains("报错") || sceneTitle.contains("异常")
            || sceneTitle.contains("错误") || sceneTitle.contains("失败")) {
            return "error";
        }

        // 性能慢类
        if (sceneTitle.contains("慢") || sceneTitle.contains("性能")
            || sceneTitle.contains("优化") || sceneTitle.contains("缓慢")) {
            return "slow";
        }

        // 排队等待类
        if (sceneTitle.contains("pending") || sceneTitle.contains("排队")
            || sceneTitle.contains("等待") || sceneTitle.contains("未执行")) {
            return "pending";
        }

        // 边界限制类
        if (sceneTitle.contains("边界") || sceneTitle.contains("限制")
            || sceneTitle.contains("quota") || sceneTitle.contains("超限")) {
            return "boundary";
        }

        // 指南类
        if (sceneTitle.contains("指南") || sceneTitle.contains("操作")
            || sceneTitle.contains("使用") || sceneTitle.contains("步骤")) {
            return "guide";
        }

        // 概念类
        if (sceneTitle.contains("概念") || sceneTitle.contains("原理")
            || sceneTitle.contains("介绍") || sceneTitle.contains("说明")) {
            return "concept";
        }

        // FAQ类
        if (sceneTitle.contains("FAQ") || sceneTitle.contains("问答")
            || sceneTitle.contains("常见问题")) {
            return "faq";
        }

        // 默认兜底
        return getDefaultSceneType(docType);
    }

    /**
     * 根据文档类型返回默认的scene_type
     */
    private static String getDefaultSceneType(String docType) {
        if ("spark调优".equals(docType)) {
            return "slow";
        } else if ("数据研发平台用户手册".equals(docType)) {
            return "guide";
        }
        return "guide"; // 通用兜底
    }
}
