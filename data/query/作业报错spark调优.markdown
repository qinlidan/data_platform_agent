# 文档一：Spark/Hive 参数调优知识库 - 作业报错篇

> **文档定位**：用于沉淀 Spark/Hive 参数调优场景下导致**作业报错、执行失败**的典型现象及可调参数，供检索和问答路由参考。

## 0. 边界声明（不适合参数调优的报错）
以下报错不能靠参数调优解决，若检索命中以下问题，请直接回复排查代码/权限/元数据，**不要去召回调参知识**：
*   SQL 语法错误、表不存在、分区不存在。
*   HDFS 权限不足、账号无读表权限、DB2 数据源密码错误。
*   业务数据脏数据（如 null 主键导致重复）。
*   外部服务宕机。

---

## 1. 报错：Java heap space 或 OutOfMemoryError (OOM)
*OOM 是作业失败的最高频原因，需根据报错发生的位置进行细分排查。*

### 1.1 map 端 OOM，作业在读取阶段失败，日志报 `Java heap space`。
*   **可调参数 / 方向**：
    *   调大 Executor 内存：`spark.executor.memory`
    *   调整执行内存比例：`spark.memory.fraction`
    *   开启自适应查询执行：`spark.sql.adaptive.enabled`
    *   配置并优化小文件合并参数。

### 1.2 shuffle 阶段 OOM，作业在执行 join、group by 等宽依赖操作时失败，日志报 `OutOfMemoryError`。
*   **可调参数 / 方向**：
    *   调大 shuffle 文件缓冲区：`spark.shuffle.file.buffer`
    *   调大 reducer 拉取数据上限：`spark.reducer.maxSizeInFlight`
    *   调整 shuffle 分区相关参数（增加并行度）。
    *   开启 AQE（自适应执行）。

### 1.3 ：driver 端 OOM，driver 节点崩溃报错，通常是因为代码中使用了 `collect`、`show`、`count` 将大表数据全量拉取到了 Driver 端。
*   **可调参数 / 方向**：
    *   调大 Driver 内存：`spark.driver.memory`
    *   **核心动作**：修改代码逻辑，禁止对大结果集执行 `collect`。

---

## 2. 报错：Shuffle fetch failed 或拉取失败
### 作业运行中出现 `Shuffle fetch failed`，或提示连接拉取 shuffle 块失败、shuffle 文件丢失。
*   **可调参数 / 方向**：
    *   增加重试等待时间：`spark.shuffle.io.retryWait`
    *   增加最大重试次数：`spark.shuffle.maxFetchRetries`
    *   检查并调整 shuffle 服务相关参数。
    *   开启 shuffle 压缩开关，降低网络传输压力。

---

## 3. 报错：数据倾斜导致 task 失败 / 卡死
### 个别 task 长时间运行，最终因为内存溢出报错失败，而其余大部分 task 都在几秒内很快结束。通常发生在 join 或 group by 操作中。
*   **可调参数 / 方向**：
    *   开启 AQE 倾斜优化开关：`spark.sql.adaptive.enabled` 设为 true。
    *   开启倾斜 join 自动处理开关：`spark.sql.adaptive.skewJoin.enabled`
    *   调整倾斜阈值、分桶策略或倾斜打散相关参数。

---

## 4. 报错：动态分区超限
### 写入 Hive 表时，由于动态分区产生过多，作业失败，报错包含 `dynamic partition exceed max number`。
*   **可调参数**：
    *   调大单节点最大动态分区数：`hive.exec.max.dynamic.partitions.pernode`
    *   调大全局最大动态分区数：`hive.exec.max.dynamic.partitions`

---

## 5. 报错：读写 Hive / ORC / Parquet 序列化、内存溢出
### 读取大分区、列太多，ORC 读的时候内存爆掉；或者写文件时产生大量小文件导致报错。
*   **可调参数 / 方向**：
    *   调大 ORC / Parquet 读写 buffer。
    *   开启并配置动态分区：`hive.exec.dynamic.partition` 及最大分区数。
    *   调整文件合并相关参数。

---

## 6. 报错：排序超限、排序内存不够失败
### 执行包含 sort 算子的 SQL 时报错，提示内存不足。
*   **可调参数**：
    *   调整排序内存阈值：`spark.sql.sort.memoryThreshold`
    *   调整 shuffle 排序合并阈值：`spark.shuffle.sort.bypassMergeThreshold`

---

## 7. 报错：广播超时、广播大表失败
### Broadcast join 执行超时，或者提示广播表太大无法广播。
*   **可调参数**：
    *   调整自动广播阈值（太小则增加网络开销，太大则易OOM）：`spark.sql.autoBroadcastJoinThreshold`
    *   调整广播超时时间参数。

---
---