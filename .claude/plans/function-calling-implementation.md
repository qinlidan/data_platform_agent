# Function Calling & Conversation Management Implementation Plan

## 概述

根据设计文档 `2026-10-24-function-calling-toolchain-design.md` 实现完整的会话管理和 Function Calling 功能链路。

## 当前状态分析

### 已完成部分
✅ 数据库迁移脚本已存在：`V1.2__create_conversation_tables.sql`
✅ 实体类已创建：`Conversation.java`, `Message.java`, `Faq.java`
✅ Mapper 已创建：`ConversationMapper`, `MessageMapper`, `FaqMapper`
✅ 基础服务已实现：`ConversationService`, `MessageService`
✅ FaqTool 已实现（带 `@RetryableQuery`）
✅ 前端页面已存在：`AiAssistant.vue`
✅ AiAssistantController 已有 `/chat/stream` 接口，支持基本对话和工具调用
✅ Pinia 已安装（package.json 已包含）

### 缺失部分（需要实现）
❌ Conversation 实体缺少 `title` 和 `deleted_at` 字段
❌ 数据库表结构与设计文档不匹配（status 枚举值、索引设计）
❌ 雪花 ID 生成器（IdGenerator）未实现
❌ 会话管理后端接口（创建/列表/获取消息/更新标题/删除）
❌ 前端 Pinia Store（会话状态管理）
❌ 前端会话列表 UI（侧边栏）
❌ 前端新建会话按钮和逻辑
❌ 前端历史消息加载逻辑
❌ VO 类（ConversationVO、MessageVO）
❌ 二次确认基础设施（ConfirmationService、Interceptor、注解）

## 实现计划

### Phase 1: 数据库迁移修正（修复表结构）

**目标**: 修正现有数据库表结构，使其符合设计文档

**文件**:
- 新建 `V1.3__fix_conversation_schema.sql`

**操作**:
1. 为 `conversations` 表添加字段：
   - `title VARCHAR(128) NOT NULL DEFAULT '新会话'`
   - `deleted_at DATETIME NULL`
2. 修改 `status` 字段注释，明确枚举值为 `ACTIVE/CLOSED/DELETED`
3. 优化索引：
   - 删除 `idx_user_id`, `idx_status` 单列索引
   - 创建复合索引 `idx_user_status (user_id, status, deleted_at)`
   - 保留 `idx_created_at`
4. 为 `messages` 表优化索引：
   - 删除单列索引 `idx_role`, `idx_tool_call_id`
   - 优化为 `idx_conversation_created (conversation_id, created_at)`
5. 修改 `conversations` 和 `messages` 主键类型为 `BIGINT`（非 AUTO_INCREMENT，准备使用雪花 ID）

**验证**:
- 执行 Flyway 迁移后，检查表结构是否正确

---

### Phase 2: 后端基础设施（雪花 ID + VO）

**目标**: 实现雪花 ID 生成器和数据传输对象

**文件**:
- `src/main/java/com/dataplatform/util/IdGenerator.java` (新建)
- `src/main/java/com/dataplatform/vo/ConversationVO.java` (新建)
- `src/main/java/com/dataplatform/vo/MessageVO.java` (新建)
- `src/main/java/com/dataplatform/entity/Conversation.java` (修改)
- `src/main/java/com/dataplatform/entity/Message.java` (修改)

#### 2.1 雪花 ID 生成器

```java
@Component
public class IdGenerator {
    private final Snowflake snowflake;
    
    public IdGenerator() {
        // workerId 和 datacenterId 从配置读取（暂时硬编码）
        this.snowflake = new Snowflake(1L, 1L);
    }
    
    public Long nextId() {
        return snowflake.nextId();
    }
}
```

**依赖**: Hutool 已在 pom.xml 中配置 (hutool-all 5.8.25)

#### 2.2 实体类修改

**Conversation.java**:
- 添加字段：`private String title;`
- 添加字段：`private LocalDateTime deletedAt;`
- 修改 `@TableId` 为 `IdType.ASSIGN_ID`（准备手动分配雪花 ID）

**Message.java**:
- 无需修改（保持 AUTO_INCREMENT）

#### 2.3 VO 类设计

**ConversationVO.java**:
```java
@Data
public class ConversationVO {
    private Long id;
    private String userId;
    private String title;
    private String status;  // ACTIVE/CLOSED/DELETED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 最后一条消息预览（可选，用于列表展示）
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
}
```

**MessageVO.java**:
```java
@Data
public class MessageVO {
    private Long id;
    private Long conversationId;
    private String role;  // user/assistant/tool
    private String content;
    private List<ToolCallVO> toolCalls;  // 解析后的工具调用
    private String toolCallId;
    private LocalDateTime createdAt;
}

@Data
class ToolCallVO {
    private String id;
    private String type;  // "function"
    private FunctionCallVO function;
}

@Data
class FunctionCallVO {
    private String name;
    private String arguments;  // JSON 字符串
}
```

**验证**:
- 单元测试：生成 100 个雪花 ID，确保无重复
- 编译通过，无语法错误

---

### Phase 3: 会话管理服务层扩展

**目标**: 扩展 ConversationService，支持完整的 CRUD 操作

**文件**:
- `src/main/java/com/dataplatform/service/ConversationService.java` (修改)
- `src/main/java/com/dataplatform/service/MessageService.java` (修改)

#### 3.1 ConversationService 新增方法

```java
/**
 * 创建新会话（使用雪花 ID）
 */
public Long createConversation(String userId, String title) {
    Long conversationId = idGenerator.nextId();
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    conversation.setUserId(userId);
    conversation.setTitle(title != null ? title : "新会话");
    conversation.setStatus("ACTIVE");
    conversationMapper.insert(conversation);
    return conversationId;
}

/**
 * 获取用户的会话列表（按更新时间倒序）
 */
public List<ConversationVO> listUserConversations(String userId) {
    LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Conversation::getUserId, userId)
           .isNull(Conversation::getDeletedAt)
           .orderByDesc(Conversation::getUpdatedAt);
    List<Conversation> conversations = conversationMapper.selectList(wrapper);
    // 转换为 VO（可添加最后一条消息预览）
    return conversations.stream()
        .map(this::toVO)
        .collect(Collectors.toList());
}

/**
 * 更新会话标题
 */
public void updateTitle(Long conversationId, String userId, String newTitle) {
    Conversation conversation = conversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new IllegalArgumentException("会话不存在或无权限");
    }
    conversation.setTitle(newTitle);
    conversationMapper.updateById(conversation);
}

/**
 * 软删除会话
 */
public void softDelete(Long conversationId, String userId) {
    Conversation conversation = conversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new IllegalArgumentException("会话不存在或无权限");
    }
    conversation.setStatus("DELETED");
    conversation.setDeletedAt(LocalDateTime.now());
    conversationMapper.updateById(conversation);
}

/**
 * 获取会话详情（校验权限）
 */
public Conversation getById(Long conversationId, String userId) {
    Conversation conversation = conversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new IllegalArgumentException("会话不存在或无权限");
    }
    return conversation;
}
```

#### 3.2 MessageService 新增方法

```java
/**
 * 获取会话的历史消息（按时间正序）
 */
public List<MessageVO> listConversationMessages(Long conversationId) {
    LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Message::getConversationId, conversationId)
           .orderByAsc(Message::getCreatedAt);
    List<Message> messages = messageMapper.selectList(wrapper);
    // 转换为 VO（解析 tool_calls JSON）
    return messages.stream()
        .map(this::toVO)
        .collect(Collectors.toList());
}

/**
 * 转换为 VO（解析 tool_calls）
 */
private MessageVO toVO(Message message) {
    MessageVO vo = new MessageVO();
    BeanUtils.copyProperties(message, vo);
    if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
        // 解析 JSON 字符串为 List<ToolCallVO>
        vo.setToolCalls(objectMapper.readValue(message.getToolCalls(), 
            new TypeReference<List<ToolCallVO>>() {}));
    }
    return vo;
}
```

**验证**:
- 单元测试：创建会话 → 发送消息 → 获取历史 → 更新标题 → 软删除

---

### Phase 4: 会话管理 REST 接口

**目标**: 实现会话管理的 6 个 RESTful 接口

**文件**:
- `src/main/java/com/dataplatform/controller/AiAssistantController.java` (修改)
- `src/main/java/com/dataplatform/dto/CreateConversationRequest.java` (新建)
- `src/main/java/com/dataplatform/dto/UpdateTitleRequest.java` (新建)

#### 4.1 新增接口

```java
@PostMapping("/conversations")
@Operation(summary = "创建新会话")
public ApiResponse<ConversationVO> createConversation(@RequestBody CreateConversationRequest request) {
    String userId = "qld_001";  // 当前硬编码，后续从 SecurityContext 获取
    Long conversationId = conversationService.createConversation(userId, request.getTitle());
    Conversation conversation = conversationService.getById(conversationId, userId);
    return ApiResponse.success(conversationService.toVO(conversation));
}

@GetMapping("/conversations")
@Operation(summary = "获取会话列表")
public ApiResponse<List<ConversationVO>> listConversations() {
    String userId = "qld_001";
    return ApiResponse.success(conversationService.listUserConversations(userId));
}

@GetMapping("/conversations/{conversationId}/messages")
@Operation(summary = "获取会话历史消息")
public ApiResponse<List<MessageVO>> getConversationMessages(
    @PathVariable Long conversationId
) {
    String userId = "qld_001";
    // 校验权限
    conversationService.getById(conversationId, userId);
    return ApiResponse.success(messageService.listConversationMessages(conversationId));
}

@PatchMapping("/conversations/{conversationId}/title")
@Operation(summary = "更新会话标题")
public ApiResponse<Void> updateTitle(
    @PathVariable Long conversationId,
    @RequestBody UpdateTitleRequest request
) {
    String userId = "qld_001";
    conversationService.updateTitle(conversationId, userId, request.getTitle());
    return ApiResponse.success();
}

@DeleteMapping("/conversations/{conversationId}")
@Operation(summary = "删除会话（软删除）")
public ApiResponse<Void> deleteConversation(@PathVariable Long conversationId) {
    String userId = "qld_001";
    conversationService.softDelete(conversationId, userId);
    return ApiResponse.success();
}
```

#### 4.2 修改 `/chat/stream` 接口

支持传入 `conversationId` 参数：

```java
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
    @RequestParam String message,
    @RequestParam(required = false) Long conversationId,
    @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // ...
    
    // 获取或创建会话
    Long finalConversationId;
    if (conversationId != null) {
        // 校验权限
        conversationService.getById(conversationId, userId);
        finalConversationId = conversationId;
    } else {
        // 自动创建新会话
        finalConversationId = conversationService.createConversation(userId, "新会话");
    }
    
    // 保存用户消息
    messageService.saveUserMessage(finalConversationId, message);
    
    // ... 后续流式响应逻辑不变
}
```

**验证**:
- Postman/Swagger UI 测试所有接口
- 测试场景：
  1. 创建新会话 → 响应包含 conversationId
  2. 发送消息（带 conversationId）→ 数据库写入正确
  3. 获取历史消息 → 返回完整列表
  4. 更新标题 → 数据库更新
  5. 删除会话 → status=DELETED, deleted_at 已设置

---

### Phase 5: 前端 Pinia Store

**目标**: 创建会话状态管理 Store

**文件**:
- `frontend/src/stores/conversation.js` (新建，需创建 stores 目录)

#### 5.1 Store 结构

```javascript
import { defineStore } from 'pinia'
import axios from 'axios'

export const useConversationStore = defineStore('conversation', {
  state: () => ({
    currentConversationId: null,
    conversations: [],
    messages: [],
    loading: false,
  }),
  
  actions: {
    async createNewConversation(title = '新会话') {
      try {
        const resp = await axios.post('/api/ai/conversations', { title })
        this.conversations.unshift(resp.data.data)
        this.switchToConversation(resp.data.data.id)
        return resp.data.data.id
      } catch (error) {
        console.error('创建会话失败', error)
        throw error
      }
    },
    
    async loadConversations() {
      this.loading = true
      try {
        const resp = await axios.get('/api/ai/conversations')
        this.conversations = resp.data.data
      } finally {
        this.loading = false
      }
    },
    
    async switchToConversation(conversationId) {
      this.currentConversationId = conversationId
      localStorage.setItem('lastConversationId', String(conversationId))
      
      // 加载历史消息
      const resp = await axios.get(`/api/ai/conversations/${conversationId}/messages`)
      this.messages = resp.data.data
    },
    
    async updateTitle(conversationId, newTitle) {
      await axios.patch(`/api/ai/conversations/${conversationId}/title`, { title: newTitle })
      const conv = this.conversations.find(c => c.id === conversationId)
      if (conv) conv.title = newTitle
    },
    
    async deleteConversation(conversationId) {
      await axios.delete(`/api/ai/conversations/${conversationId}`)
      this.conversations = this.conversations.filter(c => c.id !== conversationId)
      if (this.currentConversationId === conversationId) {
        this.currentConversationId = null
        this.messages = []
      }
    },
  },
})
```

#### 5.2 注册 Pinia

修改 `frontend/src/main.js`:

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

**验证**:
- 前端页面能正常加载
- 在 Vue DevTools 中看到 Pinia Store

---

### Phase 6: 前端会话管理 UI

**目标**: 在 AiAssistant.vue 中添加会话列表和管理功能

**文件**:
- `frontend/src/views/AiAssistant.vue` (修改)

#### 6.1 UI 布局改造

```vue
<template>
  <div class="ai-assistant-container">
    <!-- 左侧会话列表 -->
    <el-aside width="260px" class="conversation-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" size="small" @click="createConversation">
          <el-icon><Plus /></el-icon> 新建会话
        </el-button>
      </div>
      
      <el-scrollbar class="conversation-list">
        <div
          v-for="conv in conversationStore.conversations"
          :key="conv.id"
          :class="['conversation-item', { active: conv.id === conversationStore.currentConversationId }]"
          @click="switchConversation(conv.id)"
        >
          <div class="conversation-title">{{ conv.title }}</div>
          <div class="conversation-time">{{ formatTime(conv.updatedAt) }}</div>
          <el-dropdown trigger="click" @command="handleConvAction">
            <el-icon class="more-icon"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="{ action: 'rename', id: conv.id }">
                  重命名
                </el-dropdown-item>
                <el-dropdown-item :command="{ action: 'delete', id: conv.id }">
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-scrollbar>
    </el-aside>
    
    <!-- 右侧对话区（原有内容保持不变）-->
    <el-main class="chat-main">
      <!-- 原有的聊天界面 -->
    </el-main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import { Plus, MoreFilled } from '@element-plus/icons-vue'

const conversationStore = useConversationStore()

onMounted(async () => {
  // 加载会话列表
  await conversationStore.loadConversations()
  
  // 恢复上次会话
  const lastConvId = localStorage.getItem('lastConversationId')
  if (lastConvId && conversationStore.conversations.find(c => c.id === Number(lastConvId))) {
    await conversationStore.switchToConversation(Number(lastConvId))
    // 将历史消息渲染到界面
    messages.value = conversationStore.messages.map(msg => ({
      role: msg.role,
      content: msg.content,
      time: formatTime(msg.createdAt),
      tableData: null  // 历史消息不包含表格数据
    }))
  }
})

const createConversation = async () => {
  const convId = await conversationStore.createNewConversation()
  // 清空当前消息
  messages.value = []
}

const switchConversation = async (convId) => {
  await conversationStore.switchToConversation(convId)
  // 加载历史消息到界面
  messages.value = conversationStore.messages.map(msg => ({
    role: msg.role,
    content: msg.content,
    time: formatTime(msg.createdAt),
    tableData: null
  }))
}

const handleConvAction = async (command) => {
  const { action, id } = command
  if (action === 'rename') {
    const newTitle = await ElMessageBox.prompt('请输入新标题', '重命名会话', {
      inputValue: conversationStore.conversations.find(c => c.id === id).title
    })
    if (newTitle.value) {
      await conversationStore.updateTitle(id, newTitle.value)
    }
  } else if (action === 'delete') {
    await ElMessageBox.confirm('确认删除此会话？', '提示', { type: 'warning' })
    await conversationStore.deleteConversation(id)
  }
}
</script>
```

#### 6.2 修改消息发送逻辑

```javascript
const sendMessage = () => {
  if (!inputMessage.value.trim() || loading.value) return
  
  // 如果没有当前会话，先创建
  if (!conversationStore.currentConversationId) {
    await conversationStore.createNewConversation()
  }
  
  // ... 原有发送逻辑
  
  // 修改 EventSource URL，带上 conversationId
  currentEventSource = new EventSource(
    `/api/ai/chat/stream?message=${encodeURIComponent(userMessage)}&conversationId=${conversationStore.currentConversationId}`
  )
  
  // ... 后续逻辑不变
}
```

**CSS 样式**:
- 左侧边栏固定宽度 260px
- 会话列表项高度 60px，hover 显示操作按钮
- 当前激活会话使用蓝色背景高亮

**验证**:
- 刷新页面后自动恢复上次会话
- 点击"新建会话"后，URL 保持不变，页面切换到空白对话
- 切换会话后，历史消息正确加载
- 重命名/删除会话功能正常

---

### Phase 7: 二次确认基础设施（可选，后续 Phase）

**说明**: 设计文档中包含二次确认机制，但当前系统已有简单的确认逻辑（`NeedConfirmationException`）。此 Phase 可选择性实现，暂不影响核心功能。

**如需实现，包含**:
- `@RequireConfirmation` 注解
- `ConfirmationInterceptor` (AOP 拦截器)
- `ConfirmationService` (生成/验证 JWT token)
- `ConfirmationContext` (ThreadLocal)
- 修改 `JobTool.deleteJob` 和 `publishJob` 添加注解
- 前端适配确认对话框（识别 `confirmation_required` 事件）

**暂不实现原因**: 当前系统已有基本的二次确认机制，可先完成核心会话管理功能，后续迭代时再增强确认机制。

---

## 实施顺序

1. **Phase 1** → 数据库迁移修正（确保数据结构正确）
2. **Phase 2** → 后端基础设施（ID 生成器 + VO）
3. **Phase 3** → 服务层扩展（CRUD 方法）
4. **Phase 4** → REST 接口实现（后端 API 完整）
5. **Phase 5** → Pinia Store（前端状态管理）
6. **Phase 6** → 前端 UI 改造（会话列表 + 切换逻辑）
7. **Phase 7** → （可选）二次确认增强

## 验收标准

### 后端验收
- [ ] Flyway 迁移成功，表结构符合设计
- [ ] 雪花 ID 生成器单元测试通过
- [ ] 6 个 REST 接口全部可用（Swagger UI 测试通过）
- [ ] `/chat/stream` 接口支持 `conversationId` 参数
- [ ] 消息持久化完整（user、assistant、tool 三种角色）

### 前端验收
- [ ] 左侧会话列表正常显示
- [ ] 点击"新建会话"立即创建，数据库有记录
- [ ] 切换会话后，历史消息正确加载
- [ ] 刷新页面后，自动恢复上次会话
- [ ] 重命名会话功能正常
- [ ] 删除会话后，列表更新正确

### 集成验收
- [ ] 创建会话 → 发送消息 → 刷新页面 → 历史消息完整
- [ ] 多会话并行：会话 A 发送消息 → 切换到会话 B → 切回会话 A，历史正确
- [ ] FAQ 查询工具调用：问"OOM 怎么办"，返回 FAQ 答案

## 风险与应对

### 风险 1: 数据库迁移冲突
**应对**: 先备份数据库，迁移失败时可回滚

### 风险 2: 前端历史消息渲染与流式响应冲突
**应对**: 区分历史消息（静态渲染）和新消息（流式渲染），使用不同的数据结构

### 风险 3: 雪花 ID 时钟回拨
**应对**: Hutool Snowflake 已处理时钟回拨问题，无需额外处理

## 时间估算

- Phase 1: 30 分钟
- Phase 2: 1 小时
- Phase 3: 1.5 小时
- Phase 4: 2 小时
- Phase 5: 1 小时
- Phase 6: 2.5 小时
- **总计**: 约 8.5 小时（纯编码时间，不含测试和调试）

## 依赖项检查

- ✅ Hutool 5.8.25（雪花 ID）
- ✅ MyBatis-Plus 3.5.5
- ✅ Spring AI 1.0.3
- ✅ Pinia 4.0.3
- ✅ Element Plus 2.14.7
- ✅ Vue Router 5.4.0

## 实现完成后的系统架构

```
前端
├── AiAssistant.vue (会话列表 + 对话界面)
├── stores/conversation.js (Pinia Store)
└── API 调用
    ├── POST /api/ai/conversations (创建会话)
    ├── GET /api/ai/conversations (列表)
    ├── GET /api/ai/conversations/{id}/messages (历史)
    ├── PATCH /api/ai/conversations/{id}/title (重命名)
    ├── DELETE /api/ai/conversations/{id} (删除)
    └── GET /api/ai/chat/stream?conversationId={id} (对话)

后端
├── AiAssistantController (REST 接口)
├── ConversationService (会话 CRUD)
├── MessageService (消息 CRUD)
├── IdGenerator (雪花 ID)
├── FaqTool (已有，带重试)
├── JobTool (已有)
└── DataSourceTool (已有)

数据库
├── conversations (会话表，雪花 ID)
├── messages (消息表，外键 CASCADE)
└── faq (知识库，全文索引)
```

## 后续优化方向

1. **会话标题自动生成**: 根据首条消息内容，AI 自动生成会话标题
2. **消息搜索**: 支持全文搜索历史消息
3. **会话归档**: 长时间未使用的会话自动归档
4. **多用户隔离**: 从 SecurityContext 获取真实 userId
5. **工具调用可视化**: 在前端展示工具调用的详细参数和结果
6. **流式表格优化**: 表格数据逐行渲染时，自动滚动到底部
