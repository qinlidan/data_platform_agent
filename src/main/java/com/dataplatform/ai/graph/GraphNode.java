package com.dataplatform.ai.graph;

/**
 * StateGraph 节点接口
 *
 * 每个节点执行一个逻辑单元：
 * - AgentNode: 调用 ChatModel 推理
 * - ToolNode: 执行工具调用
 */
@FunctionalInterface
public interface GraphNode {

    /**
     * 执行节点逻辑
     *
     * @param state 当前状态
     * @return 更新后的状态
     */
    AgentState execute(AgentState state) throws Exception;
}
