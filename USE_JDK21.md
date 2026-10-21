# 🚀 使用已有的 JDK 21 快速启动

## ✅ 好消息

你已经安装了 JDK 21（位置：`D:\jdk-21.0.12`），完全可以运行 Spring Boot 3.2.0！

只需要切换环境变量即可。

## 🎯 最快的方法（一键完成）

**直接双击运行**：`quick-start-jdk21.bat`

这个脚本会：
1. ✅ 切换到 JDK 21
2. ✅ 重新编译项目
3. ✅ 启动应用测试

**期望结果**：看到 `Started DataPlatformApplication` 说明成功！

## 🔧 方法2：分步执行

### 步骤1：切换到 JDK 21

**双击运行**：`switch-to-jdk21.bat`

或手动执行：
```cmd
set JAVA_HOME=D:\jdk-21.0.12
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
```

应该看到：`java version "21.0.12"`

### 步骤2：重新编译

在同一个命令行窗口中：
```cmd
cd D:\alert4
mvn clean package -DskipTests
```

### 步骤3：启动测试

```cmd
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

## 💡 永久设置（可选）

如果想永久切换到 JDK 21：

1. **方法A - 使用脚本（需管理员权限）**：
   - 右键 `switch-to-jdk21.bat`
   - 选择"以管理员身份运行"

2. **方法B - 手动设置**：
   - 右键"此电脑" → 属性 → 高级系统设置 → 环境变量
   - 系统变量中：
     - 新建或修改 `JAVA_HOME` = `D:\jdk-21.0.12`
     - 编辑 `Path`，将 `%JAVA_HOME%\bin` 移到最前面
   - 确定并重启命令行

## 🎯 快速对比

| 方法 | 操作 | 是否永久 |
|------|------|---------|
| 方法1 | 双击 `quick-start-jdk21.bat` | 临时（当前窗口） |
| 方法2 | 分步执行 | 临时（当前窗口） |
| 永久设置 | 以管理员运行脚本或手动设置环境变量 | 永久 |

## ⚡ 推荐操作

**立即执行**：双击 `quick-start-jdk21.bat`

这个脚本会自动完成所有步骤，你只需要查看是否启动成功！

---

**现在就运行吧！** 🚀
