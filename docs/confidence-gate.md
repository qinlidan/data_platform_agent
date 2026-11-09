# 置信度闸（Confidence Gate）

## 概述

置信度闸是知识库问答系统的质量控制机制，基于多维信号评估检索证据的可靠性，对置信度过低的问题进行拦截和记录。

## 核心架构

### 1. 多维信号计算

置信度综合评估包含三个维度：

- **Top1重排分** (60%权重)：精排模型给出的Top1相关性分数
- **有效证据数** (20%权重)：精排返回的文档数量，理想值为5
- **分差** (20%权重)：Top1和Top2的分数差距，期望≥0.2

综合得分计算公式：
```
overallScore = 0.6 * normalize(topScore) + 0.2 * normalize(count) + 0.2 * normalize(delta)
```

### 2. 闸门拦截策略

- 当`overallScore < threshold`时拦截问题
- 默认阈值：`0.17`（建议通过标定工具优化）
- 拦截后记录到`low_confidence_questions`表

### 3. 问题池分类

记录的低置信度问题按来源分类：

- `retrieval_low_conf`：检索置信度低（闸门拦截）
- `self_check`：模型自评拒答
- `user_feedback`：用户反馈质量差（预留）

## 使用指南

### 1. 访问管理页面

启动应用后访问：
```
http://localhost:8089/confidence/pool
```

页面功能：
- 查看被拦截的问题列表
- 查看统计指标（总拦截数、各来源分布）
- 执行阈值标定
- 翻页浏览历史记录

### 2. 阈值标定

#### 自动标定（推荐）

点击页面上的"📊 标定置信度阈值"按钮，系统会：

1. 加载评估集（`resources/evaluation/evaluation-cases.json`）
2. 对每个样本执行检索+重排+置信度计算
3. 扫描阈值空间（0.05-0.95，步长0.01）
4. 寻找Youden指数最大的阈值点

Youden指数定义：
```
Youden = Accuracy + Recall - 1
```

其中：
- **Accuracy**：通过闸门的样本中可答问题的占比
- **Recall**：可答问题中通过闸门的占比

#### 准备评估集

评估集格式（JSON数组）：
```json
[
  {
    "query": "如何创建调度任务",
    "groundTruth": [
      {"expect_section": "任务调度文档"}
    ]
  },
  {
    "query": "今天天气怎么样",
    "groundTruth": []
  }
]
```

- `groundTruth`非空：可答问题
- `groundTruth`为空：不可答问题

#### 更新阈值配置

标定完成后，将推荐阈值更新到配置文件：

```yaml
knowledge:
  confidence:
    threshold: 0.23  # 替换为标定结果
```

### 3. 监控和分析

#### 查看统计

API端点：
```bash
GET /confidence/stats
```

返回示例：
```json
{
  "total": 42,
  "retrievalLowConf": 35,
  "selfCheck": 7
}
```

#### 导出问题池

使用MyBatis-Plus直接查询：
```java
List<LowConfidenceQuestion> questions = 
    lowConfidenceQuestionMapper.selectList(null);
```

或通过页面手动复制需要分析的问题。

## 配置项

### 置信度闸

```yaml
knowledge:
  confidence:
    threshold: 0.17  # 综合置信度阈值
```

### 归一化参数（硬编码）

如需调整，修改`ConfidenceGate.java`：

```java
private static final double WEIGHT_TOP_SCORE = 0.6;
private static final double WEIGHT_COUNT = 0.2;
private static final double WEIGHT_DELTA = 0.2;

private static final double MAX_RERANK_SCORE = 1.0;
private static final int IDEAL_EVIDENCE_COUNT = 5;
private static final double EXPECTED_DELTA = 0.2;
```

## 典型场景

### 场景1：提升准确率（减少误答）

- **目标**：降低低质量回答比例
- **操作**：提高threshold（如0.17 → 0.25）
- **代价**：拒答率上升，可能拒掉部分可答问题

### 场景2：提升召回率（减少拒答）

- **目标**：让更多可答问题通过
- **操作**：降低threshold（如0.17 → 0.12）
- **代价**：可能放过一些低质量问题

### 场景3：平衡优化

- **目标**：准确率和召回率平衡
- **操作**：使用标定工具，选择Youden指数最大的阈值
- **效果**：理论最优平衡点

## 技术细节

### 置信度计算流程

```
用户提问
  ↓
混合检索（向量+BM25）
  ↓
精排Top5
  ↓
提取信号（topScore, count, delta）
  ↓
归一化 + 加权
  ↓
计算overallScore
  ↓
是否 >= threshold？
  ├─ 是：通过，生成prompt
  └─ 否：拦截，记录到问题池
```

### 数据库表结构

```sql
CREATE TABLE low_confidence_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT,
    raw_question VARCHAR(500),
    source VARCHAR(50),  -- retrieval_low_conf/self_check/user_feedback
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 最佳实践

1. **初始部署**
   - 使用默认阈值0.17
   - 收集1-2周真实问题数据
   - 基于实际问题准备评估集
   - 运行标定工具优化阈值

2. **持续优化**
   - 定期（如每月）查看问题池
   - 将高频低置信问题补充到知识库
   - 更新评估集并重新标定

3. **A/B测试**
   - 在不同环境使用不同阈值
   - 对比用户满意度和拒答率
   - 选择业务效果最优的配置

## 扩展方向

### 1. 引入更多信号

- 向量相似度方差（检索一致性）
- 问题长度和复杂度
- 历史问题相似度

### 2. 自适应阈值

- 根据实时反馈动态调整
- 不同业务场景使用不同阈值

### 3. 闸门后处理

- 低置信度问题自动转人工
- 触发主动学习标注流程
- 推送到运营同学补充文档

## 参考资料

- `ConfidenceGate.java`：置信度计算和闸门逻辑
- `ThresholdCalibrator.java`：阈值标定算法
- `ConfidenceGateController.java`：管理接口
- `templates/confidence/pool.html`：可视化页面
