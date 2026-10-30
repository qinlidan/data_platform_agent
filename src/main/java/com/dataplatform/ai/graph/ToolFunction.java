package com.dataplatform.ai.graph;

import java.util.Map;

/**
 * 工具方法接口
 *
 * 所有工具方法实现此接口，支持装饰器包装
 */
@FunctionalInterface
public interface ToolFunction {

    /**
     * 执行工具调用
     *
     * @param params 参数
     * @return 执行结果
     * @throws Exception 执行失败
     */
    Object execute(Map<String, Object> params) throws Exception;
}
