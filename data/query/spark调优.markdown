# Spark/Hive 参数调优知识库输入材料

> 文档定位：用于沉淀 Spark/Hive 参数调优场景下的典型 symptom、可调参数与边界说明，供调参知识库检索、问答路由和人工排查参考。

## 0. 总边界

**Spark/Hive 参数调优，是改引擎行为、算子行为、内存/IO/并行策略；它不能修复 SQL 语法错误、表不存在、权限、数据源连通、业务逻辑 bug。**

下面按三大场景整理适合参数调优的情况：

1. **作业失败报错**
2. **作业缓慢（能跑完）**
3. **作业长久未执行 / pending**

同时附带典型 symptom，可直接放入调优知识库。

> 区分原则：
>
> - ✅ 适合参数调优 → 进入 `spark_tune_knowledge_chunk` 知识库，记录 symptom 与配套参数
> - ❌ 不属于参数调优：权限、表不存在、SQL 写错、外部服务宕机、调度依赖没就绪

---

## 一、作业报错、执行失败


### 1. OOM 内存溢出（最高频）

**Symptom：**

- 报错 `Java heap space`
- 报错 `OutOfMemoryError`

**细分场景与可调参数：**

| 细分场景 | 典型现象 | 可调参数 / 方向 |
|---|---|---|
| map 端 OOM | 读取大量小文件、读大分区、读高基数维表 | `spark.executor.memory`、`spark.memory.fraction`、`spark.sql.adaptive`、小文件合并参数 |
| shuffle 阶段 OOM | shuffle 读取大量数据 | `spark.shuffle.file.buffer`、`spark.reducer.maxSizeInFlight`、shuffle 分区相关参数、AQE |
| driver 端 OOM | 收集大量数据到 driver，如 `collect`、`show`、`count` 大表 | `spark.driver.memory`，同时禁止大结果 `collect` |

---

### 2. Shuffle 相关失败

**Symptom：**

- `Shuffle fetch failed`
- 连接拉取 shuffle 块失败
- shuffle 文件丢失

**可调参数 / 方向：**

- `spark.shuffle.io.retryWait`
- `spark.shuffle.maxFetchRetries`
- shuffle 服务相关参数
- shuffle 压缩开关

---

### 3. 数据倾斜导致 task 失败 / 卡死

**Symptom：**

- 个别 task 长时间运行，然后 OOM
- 其余 task 很快结束
- join / group by 倾斜

**可调参数 / 方向：**

- AQE 倾斜优化开关：`spark.sql.adaptive.enabled`
- 倾斜 join 开关：`spark.sql.adaptive.skewJoin.enabled`
- 倾斜阈值
- 分桶
- 倾斜打散相关参数

---

### 4. 读写 Hive / ORC / Parquet 序列化、内存溢出、读写报错

**Symptom：**

- 读取大分区、列太多，ORC 读的时候内存爆
- 写文件产生大量小文件

**可调参数 / 方向：**

- ORC / Parquet 读写 buffer
- `hive.exec.dynamic.partition`
- 动态分区最大分区数
- 文件合并参数

---

### 5. 动态分区超限报错

**Symptom：**

- `dynamic partition exceed max number`

**可调参数：**

- `hive.exec.max.dynamic.partitions`
- `hive.exec.max.dynamic.partitions.pernode`

---

### 6. 排序超限、排序内存不够失败

**Symptom：**

- sort 算子报内存不足

**可调参数：**

- `spark.sql.sort.memoryThreshold`
- `spark.shuffle.sort.bypassMergeThreshold`

---

### 7. 广播超时、广播大表失败

**Symptom：**

- Broadcast join 超时
- 广播表太大

**可调参数：**

- `spark.sql.autoBroadcastJoinThreshold`
- 广播超时时间

---

### ❌ 这些失败不能靠参数调优解决

以下问题不属于 Spark/Hive 参数调优范畴：

- SQL 语法错
- 表不存在
- 分区不存在
- HDFS 权限不足
- 账号无读表权限
- DB2 数据源密码错误
- 业务数据脏数据，例如 null 主键导致重复

---

## 二、作业可以成功跑完，但是耗时很长、变慢


### 1. Shuffle spill to disk

**Symptom：**

- 日志出现 `spill to disk`
- shuffle 阶段拖很久，IO 高

**可调参数 / 方向：**

- shuffle buffer
- reducer 拉取上限
- AQE

---

### 2. 数据倾斜

**Symptom：**

- 单个 task 拖慢整体
- 大部分 task 几秒，1~2 个 task 几十分钟

**可调参数 / 方向：**

- AQE 倾斜优化
- skew join 参数

---

### 3. 小文件太多

**Symptom：**

- 读取阶段很慢
- task 数量巨多，每个 task 处理很少数据，task 调度开销大

**可调参数 / 方向：**

- 小文件合并
- 动态分区输出文件数
- 输入合并参数

---

### 4. 并行度不合理

**Symptom：**

- 分区太少或太多
- 并行度太低导致单 task 压力大
- 并行度太高导致 task 调度、shuffle 开销爆炸

**可调参数 / 方向：**

- `spark.sql.shuffle.partitions`
- AQE 自动调整 shuffle 分区

---

### 5. 压缩未开启、序列化格式不合理

**Symptom：**

- 网络传输慢

**可调参数 / 方向：**

- shuffle 压缩
- 序列化器 `spark.serializer`

---

### 6. 大量 sort、distinct

**Symptom：**

- 排序开销高

**可调参数 / 方向：**

- 排序相关阈值
- AQE 自动优化

---

## 三、作业提交成功，长久未执行、一直 Pending

> 场景说明：作业处于排队状态，根本还没开始跑 task。  
> 这里要区分两种 pending：**资源排队 pending** 和 **引擎内部等待**。

### ✅ 1. 申请资源过大，不容易拿到容器，长时间排队

**Symptom：**

- 状态 pending
- 日志出现 `Waiting for container`
- 资源池有资源但很难满足申请规格

**可调参数 / 方向：**

- 调小 executor 内存
- 调小 executor 核数
- 拆资源规格

> 注意：这不是调业务 SQL，而是资源申请参数。

---

### ✅ 2. 动态资源分配相关

**Symptom：**

- 动态扩缩容参数配置不合理，获取资源缓慢

**可调参数 / 方向：**

- `spark.dynamicAllocation.*` 一系列参数
- 如 `minExecutors` / `maxExecutors`、申请等待超时

---

### ❌ 绝大多数 pending，参数调优救不了（重点）

以下情况不要放入调参知识库：

- 资源池整体容量打满，集群没有空闲资源 → 调度排队，只能等别的作业释放资源
- 调度依赖还没就绪，例如上游分区没产出
- 队列 / 资源池配额、优先级限制、租户配额限流
- YARN / K8s 调度策略、标签亲和限制

---

## 四、快速汇总：适合放进知识库的 Symptom 清单

1. 作业报错：`Java heap space` OOM
2. 作业报错：`Shuffle fetch failed`，拉取 shuffle 块失败
3. 作业报错：动态分区数量超出上限
4. 作业报错：Broadcast join 广播表过大、广播超时
5. 任务能跑完，日志大量 `spill to disk`，shuffle 阶段很慢
6. 任务能跑完，存在长尾 task、数据倾斜
7. 任务能跑完，输入小文件极多，读取阶段耗时久
8. 排序算子执行慢、排序内存不足
9. shuffle 并行度不合理，作业整体耗时高
10. 动态资源分配模式下，获取 executor 缓慢，长时间 pending

---

## 五、问答路由落地提醒

> 如果现象是【pending、等待容器、依赖未就绪、权限报错、表不存在】，直接回复：  
> **该问题不属于 Spark/Hive 参数调优范畴，请排查资源 / 调度 / 权限，不要去召回调参知识。**