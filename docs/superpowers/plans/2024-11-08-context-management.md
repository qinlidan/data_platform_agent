# 会话上下文管理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为数据平台智能客服添加三层上下文管理
（Layer 0 摘要层、Layer 2 半压层、Layer 1 原文层），支持异步压缩、Token 预算控制、多会话隔离。

**Architecture:** 自定义 MyBatis 实现的 ChatMemoryRepository + 三层拼装 Advisor + 异步摘要服务。使用两个锚点
（summary_upto_msg_id、layer1_from_msg_id）划分三层，降级操作只挪锚点，不修改原始消息数据。

**Tech Stack:** Spring AI 1.0.0 GA + Spring Boot 3.2.2 + MyBatis-Plus + MySQL 5.7 + @Async

**Spec:** `D:\alert4\docs\superpowers\specs\2024-11-08-context-management-design.md`

## Global Constraints

- Spring AI 版本：1.0.0 GA
- 不跨会话存储用户画像
- 默认窗口下 20 轮不触发压缩
- 只在作业列表页智能助手添加（不影响其他对话功能）
- 摘要长度控制在 50-200 字
- 异步摘要不阻塞用户回复
- 日志必须包含 `model_ctx`、`history_ctx`、`summary` 三类埋点

## Review Focus

1. **锚点越界读取** - 当 `summary_upto_msg_id` 或 `layer1_from_msg_id` 指向不存在的消息 ID 时，系统应返回空列表而不是抛异常
2. **并发摘要冲突** - 两个摘要任务同时更新 `summary_upto_msg_id` 时，后者不应覆盖前者的进度
3. **Token 预算为负** - 当模型窗口减去固定成本后剩余空间不足一轮对话时，系统应启动时报警而非运行时崩溃
4. **半压消息截断中文** - 助手消息截取前 50 字时，遇到多字节 UTF-8 字符应按字符而非字节截断
5. **空会话降级** - 当 Layer 1 为空时触发降级，系统应跳过而非尝试降级不存在的消息

---

## File Structure

**New Files:**
- `src/main/resources/db/migration/V20241108__add_context_management.sql` - DDL for three layers
- `src/main/java/com/dataplatform/entity/ConversationSummary.java` - Summary segment entity
- `src/main/java/com/dataplatform/mapper/ConversationSummaryMapper.java` - Summary mapper
- `src/main/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepository.java` - Custom Spring AI repository
- `src/main/java/com/dataplatform/ai/memory/TokenBudgetCalculator.java` - Token budget logic
- `src/main/java/com/dataplatform/ai/memory/LayeredMessageRenderer.java` - Layer 1/2 rendering
- `src/main/java/com/dataplatform/ai/advisor/ContextLayeredAdvisor.java` - Three-layer assembly + degradation
- `src/main/java/com/dataplatform/ai/service/ContextSummaryService.java` - Async summarization
- `src/main/java/com/dataplatform/ai/config/SummaryAsyncConfig.java` - Thread pool for summary tasks

**Modified Files:**
- `src/main/java/com/dataplatform/entity/Conversation.java` - Add `summary`, `summaryUptoMsgId`, `layer1FromMsgId`
- `src/main/java/com/dataplatform/mapper/ConversationMapper.java` - Add custom update methods
- `src/main/java/com/dataplatform/mapper/MessageMapper.java` - Add range query methods
- `src/main/java/com/dataplatform/ai/config/AIConfig.java` - Integrate custom Advisor and ChatMemory

**Test Files:**
- `src/test/java/com/dataplatform/ai/memory/TokenBudgetCalculatorTest.java`
- `src/test/java/com/dataplatform/ai/memory/LayeredMessageRendererTest.java`
- `src/test/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepositoryTest.java`
- `src/test/java/com/dataplatform/ai/advisor/ContextLayeredAdvisorTest.java`
- `src/test/java/com/dataplatform/ai/service/ContextSummaryServiceTest.java`
- `src/test/java/com/dataplatform/ai/ContextManagementIntegrationTest.java`

---

### Task 1: 数据层基础设施

**Files:**
- Create: `src/main/resources/db/migration/V20241108__add_context_management.sql`
- Modify: `src/main/java/com/dataplatform/entity/Conversation.java:16-57`
- Create: `src/main/java/com/dataplatform/entity/ConversationSummary.java`
- Create: `src/main/java/com/dataplatform/mapper/ConversationSummaryMapper.java`
- Modify: `src/main/java/com/dataplatform/mapper/ConversationMapper.java`
- Modify: `src/main/java/com/dataplatform/mapper/MessageMapper.java`

**Interfaces:**
- Consumes: 现有 `Conversation` 实体（id, userId, title, status 字段）
- Produces:
  - `Conversation` 新增字段：`summary: String`, `summaryUptoMsgId: Long`, `layer1FromMsgId: Long`
  - `ConversationSummary` 实体：`id: Long`, `conversationId: Long`, `seq: Integer`, `content: String`, `createdAt: LocalDateTime`
  - `ConversationSummaryMapper.insert(ConversationSummary): int`
  - `ConversationSummaryMapper.selectByConversationId(Long): List<ConversationSummary>`
  - `ConversationMapper.updateSummaryFields(Long conversationId, String summary, Long summaryUptoMsgId): int`
  - `MessageMapper.selectByIdRange(Long conversationId, Long fromMsgId, Long uptoMsgId): List<Message>`

- [ ] **Step 1: 编写 DDL 迁移脚本**

创建 `V20241108__add_context_management.sql`:

```sql
-- 扩展 conversations 表
ALTER TABLE conversations 
ADD COLUMN summary TEXT COMMENT '摘要全文（拼接所有分段）',
ADD COLUMN summary_upto_msg_id BIGINT DEFAULT NULL COMMENT 'Layer 0 摘要覆盖到的消息 ID',
ADD COLUMN layer1_from_msg_id BIGINT DEFAULT NULL COMMENT 'Layer 1 原文层起点消息 ID';

-- 创建分段摘要表
CREATE TABLE conversation_summaries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL COMMENT '所属会话 ID',
  seq INT NOT NULL COMMENT '分段序号（从 1 开始）',
  content TEXT NOT NULL COMMENT '摘要段落内容',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  INDEX idx_conversation_seq (conversation_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话分段摘要';
```

- [ ] **Step 2: 扩展 Conversation 实体**

在 `Conversation.java` 第 57 行后添加：

```java
/**
 * 摘要全文（拼接所有分段）
 */
@TableField("summary")
private String summary;

/**
 * Layer 0 摘要覆盖到的消息 ID
 */
@TableField("summary_upto_msg_id")
private Long summaryUptoMsgId;

/**
 * Layer 1 原文层起点消息 ID
 */
@TableField("layer1_from_msg_id")
private Long layer1FromMsgId;
```

- [ ] **Step 3: 创建 ConversationSummary 实体**

```java
package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation_summaries")
public class ConversationSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("conversation_id")
    private Long conversationId;
    
    private Integer seq;
    
    private String content;
    
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 创建 ConversationSummaryMapper**

```java
package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.ConversationSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ConversationSummaryMapper extends BaseMapper<ConversationSummary> {
    
    @Select("SELECT * FROM conversation_summaries WHERE conversation_id = #{conversationId} ORDER BY seq ASC")
    List<ConversationSummary> selectByConversationId(Long conversationId);
}
```

- [ ] **Step 5: 扩展 ConversationMapper**

在 `ConversationMapper.java` 添加：

```java
@Update("UPDATE conversations SET summary = #{summary}, summary_upto_msg_id = #{summaryUptoMsgId} WHERE id = #{conversationId}")
int updateSummaryFields(@Param("conversationId") Long conversationId, 
                       @Param("summary") String summary, 
                       @Param("summaryUptoMsgId") Long summaryUptoMsgId);
```

- [ ] **Step 6: 扩展 MessageMapper**

在 `MessageMapper.java` 添加：

```java
@Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} AND id > #{fromMsgId} AND id <= #{uptoMsgId} ORDER BY id ASC")
List<Message> selectByIdRange(@Param("conversationId") Long conversationId, 
                              @Param("fromMsgId") Long fromMsgId, 
                              @Param("uptoMsgId") Long uptoMsgId);

@Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} AND id > #{fromMsgId} ORDER BY id ASC")
List<Message> selectFromId(@Param("conversationId") Long conversationId, 
                           @Param("fromMsgId") Long fromMsgId);
```

- [ ] **Step 7: 编写数据层集成测试**

创建 `src/test/java/com/dataplatform/mapper/ConversationMapperTest.java`:

```java
@SpringBootTest
class ConversationMapperTest {
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ConversationSummaryMapper summaryMapper;
    @Autowired
    private MessageMapper messageMapper;
    
    @Test
    void testUpdateSummaryFields() {
        // 创建测试会话
        Conversation conv = new Conversation();
        conv.setUserId("test-user");
        conv.setTitle("test");
        conv.setStatus(ConversationStatus.ACTIVE);
        conversationMapper.insert(conv);
        
        // 更新摘要字段
        int updated = conversationMapper.updateSummaryFields(conv.getId(), "test summary", 123L);
        assertEquals(1, updated);
        
        // 验证更新
        Conversation loaded = conversationMapper.selectById(conv.getId());
        assertEquals("test summary", loaded.getSummary());
        assertEquals(123L, loaded.getSummaryUptoMsgId());
    }
    
    @Test
    void testSelectByIdRange() {
        // 准备：插入 5 条消息 (conversationId = 1, id = 1~5)
        Long conversationId = 1L;
        for (int i = 1; i <= 5; i++) {
            Message msg = new Message();
            msg.setId((long) i);
            msg.setConversationId(conversationId);
            msg.setRole(MessageRole.USER);
            msg.setContent("msg" + i);
            messageMapper.insert(msg);
        }
        
        // 查询区间 (2, 4] 应返回 id=3,4
        List<Message> result = messageMapper.selectByIdRange(conversationId, 2L, 4L);
        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals(4L, result.get(1).getId());
    }
}
```

- [ ] **Step 8: 运行测试验证数据层**

Run: `mvn test -Dtest=ConversationMapperTest`
Expected: 所有测试通过

- [ ] **Step 9: 提交数据层变更**

```bash
git add src/main/resources/db/migration/V20241108__add_context_management.sql \
         src/main/java/com/dataplatform/entity/Conversation.java \
         src/main/java/com/dataplatform/entity/ConversationSummary.java \
         src/main/java/com/dataplatform/mapper/ConversationSummaryMapper.java \
         src/main/java/com/dataplatform/mapper/ConversationMapper.java \
         src/main/java/com/dataplatform/mapper/MessageMapper.java \
         src/test/java/com/dataplatform/mapper/ConversationMapperTest.java
git commit -m "feat(context): add three-layer context management schema

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Token 预算计算器

**Files:**
- Create: `src/main/java/com/dataplatform/ai/memory/TokenBudgetCalculator.java`
- Create: `src/test/java/com/dataplatform/ai/memory/TokenBudgetCalculatorTest.java`

**Interfaces:**
- Consumes: 无（独立工具类）
- Produces:
  - `TokenBudgetCalculator.BudgetConfig` record：`modelWindow: int`, `layer1Budget: int`, `layer2Budget: int`, `historyBudget: int`
  - `TokenBudgetCalculator.calculateBudget(): BudgetConfig`
  - `TokenBudgetCalculator.estimateTokens(String text): int` - 中文按字数折算

- [ ] **Step 1: 编写 Token 预算计算测试**

```java
package com.dataplatform.ai.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenBudgetCalculatorTest {
    
    @Test
    void testEstimateTokens_Chinese() {
        TokenBudgetCalculator calculator = new TokenBudgetCalculator(100000);
        // 中文按 1.3 字/token 折算
        int tokens = calculator.estimateTokens("这是一段测试文本共十二个字");
        assertTrue(tokens >= 9 && tokens <= 12); // 12/1.3 ≈ 9
    }
    
    @Test
    void testCalculateBudget_Default() {
        TokenBudgetCalculator calculator = new TokenBudgetCalculator(100000);
        TokenBudgetCalculator.BudgetConfig budget = calculator.calculateBudget();
        
        // history_budget = MODEL_WINDOW - system(8000) - retrieval(2000) - summary(500) - output(4000) - safety(1000) - react_peak(5000)
        int expectedHistory = 100000 - 8000 - 2000 - 500 - 4000 - 1000 - 5000; // 79500
        assertEquals(expectedHistory, budget.historyBudget());
        
        // layer1 = 70%, layer2 = 30%
        assertEquals((int)(expectedHistory * 0.7), budget.layer1Budget());
        assertEquals((int)(expectedHistory * 0.3), budget.layer2Budget());
    }
    
    @Test
    void testCalculateBudget_SmallWindow() {
        TokenBudgetCalculator calculator = new TokenBudgetCalculator(18000);
        TokenBudgetCalculator.BudgetConfig budget = calculator.calculateBudget();
        
        // 小窗口下应能装下至少 1 轮对话
        assertTrue(budget.historyBudget() > 0);
        assertTrue(budget.layer1Budget() > 0);
    }
    
    @Test
    void testCalculateBudget_TooSmallWindow_ThrowsException() {
        // 窗口小到连一轮都装不下应抛异常
        TokenBudgetCalculator calculator = new TokenBudgetCalculator(5000);
        assertThrows(IllegalStateException.class, calculator::calculateBudget);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=TokenBudgetCalculatorTest`
Expected: FAIL with "TokenBudgetCalculator not found"

- [ ] **Step 3: 实现 TokenBudgetCalculator**

```java
package com.dataplatform.ai.memory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenBudgetCalculator {
    
    private final int modelWindow;
    
    // 固定成本
    private static final int SYSTEM_COST = 8000;
    private static final int RETRIEVAL_COST = 2000;
    private static final int SUMMARY_COST = 500;
    private static final int OUTPUT_COST = 4000;
    private static final int SAFETY_COST = 1000;
    private static final int REACT_PEAK_COST = 5000;
    
    // 中文 token 折算率（字/token）
    private static final double CHINESE_CHARS_PER_TOKEN = 1.3;
    
    public TokenBudgetCalculator(int modelWindow) {
        this.modelWindow = modelWindow;
    }
    
    public record BudgetConfig(
        int modelWindow,
        int historyBudget,
        int layer1Budget,
        int layer2Budget
    ) {}
    
    public BudgetConfig calculateBudget() {
        int historyBudget = modelWindow - SYSTEM_COST - RETRIEVAL_COST 
                           - SUMMARY_COST - OUTPUT_COST - SAFETY_COST - REACT_PEAK_COST;
        
        if (historyBudget < 500) {
            throw new IllegalStateException(
                String.format("Model window %d too small, cannot fit even one conversation turn", modelWindow)
            );
        }
        
        int layer1Budget = (int) (historyBudget * 0.7);
        int layer2Budget = (int) (historyBudget * 0.3);
        
        log.info("[TokenBudget] model={} history={} layer1={} layer2={}", 
                 modelWindow, historyBudget, layer1Budget, layer2Budget);
        
        return new BudgetConfig(modelWindow, historyBudget, layer1Budget, layer2Budget);
    }
    
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单估算：中文按字数 / 1.3
        return (int) Math.ceil(text.length() / CHINESE_CHARS_PER_TOKEN);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=TokenBudgetCalculatorTest`
Expected: PASS - 所有测试通过

- [ ] **Step 5: 提交 Token 预算计算器**

```bash
git add src/main/java/com/dataplatform/ai/memory/TokenBudgetCalculator.java \
         src/test/java/com/dataplatform/ai/memory/TokenBudgetCalculatorTest.java
git commit -m "feat(context): add token budget calculator

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 三层消息渲染器

**Files:**
- Create: `src/main/java/com/dataplatform/ai/memory/LayeredMessageRenderer.java`
- Create: `src/test/java/com/dataplatform/ai/memory/LayeredMessageRendererTest.java`

**Interfaces:**
- Consumes:
  - `Message` 实体（id, role, content, toolCalls, toolCallId）
  - `TokenBudgetCalculator.estimateTokens(String): int`
- Produces:
  - `LayeredMessageRenderer.renderLayer1(List<Message>, int budget): RenderedLayer` - 原文渲染
  - `LayeredMessageRenderer.renderLayer2(List<Message>, int budget): RenderedLayer` - 半压渲染
  - `RenderedLayer` record：`messages: List<Message>`, `truncatedCount: int`, `estimatedTokens: int`

- [ ] **Step 1: 编写三层渲染测试**

```java
package com.dataplatform.ai.memory;

import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LayeredMessageRendererTest {
    
    private final TokenBudgetCalculator calculator = new TokenBudgetCalculator(100000);
    private final LayeredMessageRenderer renderer = new LayeredMessageRenderer(calculator);
    
    @Test
    void testRenderLayer1_WithinBudget() {
        List<Message> messages = createTestMessages(3);
        LayeredMessageRenderer.RenderedLayer result = renderer.renderLayer1(messages, 10000);
        
        // 预算足够，保留所有消息原文
        assertEquals(3, result.messages().size());
        assertEquals(0, result.truncatedCount());
        assertEquals(messages.get(0).getContent(), result.messages().get(0).getContent());
    }
    
    @Test
    void testRenderLayer2_Compressed() {
        List<Message> messages = new ArrayList<>();
        
        // 用户消息：保留原文
        Message userMsg = new Message();
        userMsg.setId(1L);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent("用户问题");
        messages.add(userMsg);
        
        // 助手消息：超过 50 字应截断
        Message assistantMsg = new Message();
        assistantMsg.setId(2L);
        assistantMsg.setRole(MessageRole.ASSISTANT);
        assistantMsg.setContent("这是一个很长的助手回复内容" + "重复".repeat(30)); // >50 字
        messages.add(assistantMsg);
        
        // 工具消息：省略为标识符
        Message toolMsg = new Message();
        toolMsg.setId(3L);
        toolMsg.setRole(MessageRole.TOOL);
        toolMsg.setContent("工具调用返回的大量数据...");
        messages.add(toolMsg);
        
        LayeredMessageRenderer.RenderedLayer result = renderer.renderLayer2(messages, 10000);
        
        // 验证用户消息保留原文
        assertEquals("用户问题", result.messages().get(0).getContent());
        
        // 验证助手消息被截断
        assertTrue(result.messages().get(1).getContent().length() <= 53); // 50字 + "..."
        
        // 验证工具消息被省略
        assertEquals("[工具调用结果，已省略]", result.messages().get(2).getContent());
    }
    
    @Test
    void testRenderLayer2_ChineseCharacterSafeTruncation() {
        Message msg = new Message();
        msg.setId(1L);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("中文字符截断测试" + "测试".repeat(20)); // 超过 50 字
        
        LayeredMessageRenderer.RenderedLayer result = renderer.renderLayer2(List.of(msg), 10000);
        
        // 验证截断后的文本是有效的 UTF-8 字符串
        String truncated = result.messages().get(0).getContent();
        assertDoesNotThrow(() -> truncated.getBytes("UTF-8"));
        assertTrue(truncated.endsWith("..."));
    }
    
    private List<Message> createTestMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message msg = new Message();
            msg.setId((long) i + 1);
            msg.setRole(i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT);
            msg.setContent("测试消息 " + (i + 1));
            messages.add(msg);
        }
        return messages;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=LayeredMessageRendererTest`
Expected: FAIL with "LayeredMessageRenderer not found"

- [ ] **Step 3: 实现 LayeredMessageRenderer**

```java
package com.dataplatform.ai.memory;

import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class LayeredMessageRenderer {
    
    private final TokenBudgetCalculator calculator;
    
    public record RenderedLayer(
        List<Message> messages,
        int truncatedCount,
        int estimatedTokens
    ) {}
    
    /**
     * Layer 1: 原文渲染（最近几轮完整对话）
     */
    public RenderedLayer renderLayer1(List<Message> messages, int budget) {
        List<Message> rendered = new ArrayList<>();
        int totalTokens = 0;
        int truncatedCount = 0;
        
        // 从最新向最旧遍历，塞满预算
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int tokens = calculator.estimateTokens(msg.getContent());
            
            if (totalTokens + tokens > budget) {
                truncatedCount = i + 1;
                break;
            }
            
            rendered.add(0, copyMessage(msg)); // 保持原序
            totalTokens += tokens;
        }
        
        return new RenderedLayer(rendered, truncatedCount, totalTokens);
    }
    
    /**
     * Layer 2: 半压渲染（用户原文 + 助手截短 + 工具省略）
     */
    public RenderedLayer renderLayer2(List<Message> messages, int budget) {
        List<Message> rendered = new ArrayList<>();
        int totalTokens = 0;
        
        for (Message msg : messages) {
            Message compressed = compressMessage(msg);
            int tokens = calculator.estimateTokens(compressed.getContent());
            
            if (totalTokens + tokens > budget) {
                break;
            }
            
            rendered.add(compressed);
            totalTokens += tokens;
        }
        
        return new RenderedLayer(rendered, 0, totalTokens);
    }
    
    private Message compressMessage(Message original) {
        Message compressed = copyMessage(original);
        
        if (original.getRole() == MessageRole.ASSISTANT) {
            // 助手消息：保留前 50 字
            String content = original.getContent();
            if (content != null && content.length() > 50) {
                compressed.setContent(content.substring(0, 50) + "...");
            }
        } else if (original.getRole() == MessageRole.TOOL) {
            // 工具消息：省略为标识符
            compressed.setContent("[工具调用结果，已省略]");
        }
        // USER 消息保持原文
        
        return compressed;
    }
    
    private Message copyMessage(Message original) {
        Message copy = new Message();
        copy.setId(original.getId());
        copy.setConversationId(original.getConversationId());
        copy.setRole(original.getRole());
        copy.setContent(original.getContent());
        copy.setToolCalls(original.getToolCalls());
        copy.setToolCallId(original.getToolCallId());
        copy.setCreatedAt(original.getCreatedAt());
        return copy;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=LayeredMessageRendererTest`
Expected: PASS - 所有测试通过

- [ ] **Step 5: 提交三层渲染器**

```bash
git add src/main/java/com/dataplatform/ai/memory/LayeredMessageRenderer.java \
         src/test/java/com/dataplatform/ai/memory/LayeredMessageRendererTest.java
git commit -m "feat(context): add layered message renderer

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: 自定义 ChatMemoryRepository

**Files:**
- Create: `src/main/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepository.java`
- Create: `src/test/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepositoryTest.java`

**Interfaces:**
- Consumes:
  - `MessageMapper.insert(Message): int`
  - `MessageMapper.selectByConversationId(Long): List<Message>`
  - Spring AI `org.springframework.ai.chat.memory.ChatMemoryRepository`
  - Spring AI `org.springframework.ai.chat.messages.Message` (需转换为实体)
- Produces:
  - `MyBatisChatMemoryRepository implements ChatMemoryRepository`
  - `add(String conversationId, List<org.springframework.ai.chat.messages.Message>): void`
  - `get(String conversationId, int lastN): List<org.springframework.ai.chat.messages.Message>`
  - `clear(String conversationId): void`

- [ ] **Step 1: 编写 ChatMemoryRepository 测试**

```java
package com.dataplatform.ai.memory;

import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MyBatisChatMemoryRepositoryTest {
    
    @Mock
    private MessageMapper messageMapper;
    
    private MyBatisChatMemoryRepository repository;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new MyBatisChatMemoryRepository(messageMapper);
    }
    
    @Test
    void testAdd_ConvertAndInsert() {
        String conversationId = "conv-123";
        List<org.springframework.ai.chat.messages.Message> aiMessages = List.of(
            new UserMessage("用户问题")
        );
        
        repository.add(conversationId, aiMessages);
        
        verify(messageMapper, times(1)).insert(argThat(msg -> 
            msg.getConversationId().equals(123L) && 
            msg.getRole() == MessageRole.USER &&
            msg.getContent().equals("用户问题")
        ));
    }
    
    @Test
    void testGet_ConvertFromEntity() {
        String conversationId = "conv-456";
        List<Message> dbMessages = new ArrayList<>();
        Message msg = new Message();
        msg.setId(1L);
        msg.setConversationId(456L);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("助手回复");
        dbMessages.add(msg);
        
        when(messageMapper.selectList(any())).thenReturn(dbMessages);
        
        List<org.springframework.ai.chat.messages.Message> result = repository.get(conversationId, 10);
        
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof AssistantMessage);
        assertEquals("助手回复", result.get(0).getContent());
    }
    
    @Test
    void testClear_LogicDelete() {
        String conversationId = "conv-789";
        
        repository.clear(conversationId);
        
        verify(messageMapper, times(1)).delete(argThat(wrapper -> 
            wrapper.getEntity().getConversationId().equals(789L)
        ));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=MyBatisChatMemoryRepositoryTest`
Expected: FAIL with "MyBatisChatMemoryRepository not found"

- [ ] **Step 3: 实现 MyBatisChatMemoryRepository**

```java
package com.dataplatform.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyBatisChatMemoryRepository implements ChatMemoryRepository {
    
    private final MessageMapper messageMapper;
    
    @Override
    public void add(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
        Long convId = parseConversationId(conversationId);
        
        for (org.springframework.ai.chat.messages.Message aiMsg : messages) {
            Message entity = convertToEntity(aiMsg, convId);
            messageMapper.insert(entity);
        }
        
        log.debug("[ChatMemory] Added {} messages to conversation {}", messages.size(), conversationId);
    }
    
    @Override
    public List<org.springframework.ai.chat.messages.Message> get(String conversationId, int lastN) {
        Long convId = parseConversationId(conversationId);
        
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, convId)
               .orderByDesc(Message::getId)
               .last("LIMIT " + lastN);
        
        List<Message> entities = messageMapper.selectList(wrapper);
        
        // 反转顺序（查询是倒序，需恢复正序）
        List<org.springframework.ai.chat.messages.Message> aiMessages = entities.stream()
            .map(this::convertToAIMessage)
            .collect(Collectors.toList());
        java.util.Collections.reverse(aiMessages);
        
        return aiMessages;
    }
    
    @Override
    public void clear(String conversationId) {
        Long convId = parseConversationId(conversationId);
        
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, convId);
        messageMapper.delete(wrapper);
        
        log.info("[ChatMemory] Cleared conversation {}", conversationId);
    }
    
    private Long parseConversationId(String conversationId) {
        // conversationId 格式: "conv-{id}"
        if (conversationId == null || !conversationId.startsWith("conv-")) {
            throw new IllegalArgumentException("Invalid conversationId: " + conversationId);
        }
        return Long.parseLong(conversationId.substring(5));
    }
    
    private Message convertToEntity(org.springframework.ai.chat.messages.Message aiMsg, Long convId) {
        Message entity = new Message();
        entity.setConversationId(convId);
        entity.setContent(aiMsg.getContent());
        
        if (aiMsg instanceof UserMessage) {
            entity.setRole(MessageRole.USER);
        } else if (aiMsg instanceof AssistantMessage) {
            entity.setRole(MessageRole.ASSISTANT);
            // TODO: 提取 toolCalls 如果有
        } else if (aiMsg instanceof SystemMessage) {
            entity.setRole(MessageRole.SYSTEM);
        } else if (aiMsg instanceof ToolResponseMessage) {
            entity.setRole(MessageRole.TOOL);
            // TODO: 提取 toolCallId
        }
        
        return entity;
    }
    
    private org.springframework.ai.chat.messages.Message convertToAIMessage(Message entity) {
        return switch (entity.getRole()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            case TOOL -> new ToolResponseMessage(entity.getContent(), null); // TODO: metadata
        };
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=MyBatisChatMemoryRepositoryTest`
Expected: PASS - 所有测试通过

- [ ] **Step 5: 提交 ChatMemoryRepository**

```bash
git add src/main/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepository.java \
         src/test/java/com/dataplatform/ai/memory/MyBatisChatMemoryRepositoryTest.java
git commit -m "feat(context): add MyBatis-based ChatMemoryRepository

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

