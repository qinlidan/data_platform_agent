# 知识库管理前端实现计划

## Context
实现SDD第7节"知识库管理界面"的前端部分，包括：
1. 文档管理：查看已导入文档列表、查看切块、重新导入
2. 手工录入：粘贴Markdown预览切分、查重标注、确认入库
3. 检索自测：测试向量检索效果

## Tasks

### Task 1: 后端API接口
**Brief**: 实现知识库管理所需的REST API

**Steps**:
1. 创建KnowledgeManagementController
2. 实现GET /api/knowledge/documents - 获取文档列表
3. 实现GET /api/knowledge/documents/{docId}/chunks - 查看文档切块
4. 实现POST /api/knowledge/documents/{docId}/reimport - 重新导入
5. 实现POST /api/knowledge/manual/preview - 预览切分
6. 实现POST /api/knowledge/manual/import - 确认入库
7. 实现POST /api/knowledge/search/test - 检索自测

**Expected**: 编译通过，Postman可调用

**Commit**: feat: 知识库管理API接口

### Task 2: 前端路由和页面骨架
**Brief**: 创建KnowledgeManagement.vue页面及路由

**Steps**:
1. 在frontend/src/views/创建KnowledgeManagement.vue
2. 在router/index.js添加路由 /knowledge
3. 在Layout.vue菜单中添加"知识库管理"入口
4. 创建三个Tab: 文档管理、手工录入、检索自测

**Expected**: 访问/knowledge显示页面骨架，三个Tab可切换

**Commit**: feat: 知识库管理前端页面骨架

### Task 3: 文档管理Tab
**Brief**: 实现文档列表展示、切块查看、重新导入功能

**Steps**:
1. 实现文档列表表格（文件名、切块数、最后更新时间）
2. 点击"查看切块"弹出Dialog展示分页的chunk列表
3. 实现"重新导入"按钮及确认逻辑
4. 调用后端API /api/knowledge/documents和/api/knowledge/documents/{docId}/chunks

**Expected**: 可查看文档列表、查看切块、触发重新导入

**Commit**: feat: 文档管理Tab实现

### Task 4: 手工录入Tab
**Brief**: 实现Markdown粘贴、预览切分、查重标注、确认入库

**Steps**:
1. 创建Markdown文本框和docType输入框
2. 实现"预览切分"按钮调用/api/knowledge/manual/preview
3. 展示切分结果表格，标注isDuplicate为true的行（红色标记）
4. 添加多选框支持用户勾选要保留的chunks
5. 实现"确认入库"按钮调用/api/knowledge/manual/import

**Expected**: 可粘贴Markdown、预览切分、查看重复标注、确认入库

**Commit**: feat: 手工录入Tab实现

### Task 5: 检索自测Tab
**Brief**: 实现检索测试功能

**Steps**:
1. 创建搜索输入框和docType过滤下拉框
2. 实现"搜索"按钮调用/api/knowledge/search/test
3. 展示结果表格（questions、answer、score、docType、sectionPath）
4. 高亮匹配关键词（使用v-html或highlight.js）

**Expected**: 可输入查询、展示召回结果、高亮关键词

**Commit**: feat: 检索自测Tab实现

## Review Focus
- API接口是否符合SDD第7.2节定义
- 前端交互是否符合SDD第7.1节描述
- 查重标注逻辑是否正确
- 错误处理是否完善
