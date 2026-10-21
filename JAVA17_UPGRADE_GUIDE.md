# Java 17 升级完整指南

## 当前状态
- ❌ 当前版本：Java 8 (1.8.0_202)
- ✅ 需要版本：Java 17
- 📦 项目：Spring Boot 3.2.0（必须使用Java 17+）

## 🚀 升级步骤

### 步骤1：下载 Java 17

**推荐选项A - Eclipse Temurin（OpenJDK，免费）**

1. 访问：https://adoptium.net/temurin/releases/?version=17
2. 选择：
   - Version: 17 - LTS
   - Operating System: Windows
   - Architecture: x64（64位）或 x86（32位，根据你的系统）
   - Package Type: JDK
   - 点击 `.msi` 安装包下载

**选项B - Oracle JDK 17（需要账号）**

1. 访问：https://www.oracle.com/java/technologies/downloads/#java17
2. 下载 Windows x64 Installer

### 步骤2：安装 Java 17

1. **运行安装包**（如 `OpenJDK17U-jdk_x64_windows_hotspot_17.0.x.msi`）

2. **安装路径建议**：
   - 默认路径通常是：`C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot\`
   - 或者自定义：`C:\Java\jdk-17`

3. **安装选项**：
   - ✅ 勾选 "Add to PATH"（添加到环境变量）
   - ✅ 勾选 "Set JAVA_HOME"（设置JAVA_HOME）
   - ✅ 勾选所有默认选项

4. **完成安装**

### 步骤3：配置环境变量（如果安装时未自动配置）

#### 方法A - 使用图形界面

1. **打开系统属性**：
   - 右键"此电脑" → 属性
   - 点击"高级系统设置"
   - 点击"环境变量"

2. **设置 JAVA_HOME**（系统变量）：
   - 点击"新建"
   - 变量名：`JAVA_HOME`
   - 变量值：`C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`
   - 点击"确定"

3. **修改 Path 变量**：
   - 在"系统变量"中找到 `Path`
   - 点击"编辑"
   - 删除或移到最下面：旧的Java路径（如 `C:\Program Files\Java\jdk1.8.0_202\bin`）
   - 在最上面"新建"添加：`%JAVA_HOME%\bin`
   - 点击"确定"

4. **保存所有设置**

#### 方法B - 使用命令行（管理员权限）

```cmd
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot" /M
setx PATH "%JAVA_HOME%\bin;%PATH%" /M
```

### 步骤4：验证安装

**打开新的命令行窗口**（重要！必须是新窗口）

```bash
java -version
```

**期望输出**：
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x (build 17.0.x+x)
OpenJDK 64-Bit Server VM Temurin-17.0.x (build 17.0.x+x, mixed mode, sharing)
```

**验证编译器**：
```bash
javac -version
```

**期望输出**：
```
javac 17.0.x
```

**验证环境变量**：
```bash
echo %JAVA_HOME%
```

**期望输出**：
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot
```

### 步骤5：清理并重新编译项目

```bash
cd D:\alert4

# 清理Maven缓存的编译文件
mvn clean

# 重新编译
mvn clean compile

# 打包
mvn clean package -DskipTests
```

### 步骤6：测试启动

```bash
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

## 🔧 可能遇到的问题

### 问题1：命令行仍显示 Java 8

**原因**：环境变量未生效或有多个Java版本

**解决**：
1. 完全关闭所有命令行窗口
2. 重新打开新的命令行窗口
3. 如果还是不行，重启电脑

**临时解决**（单次有效）：
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
```

### 问题2：IDEA 仍使用 Java 8

**解决方法**：

1. **设置项目SDK**：
   - File → Project Structure → Project
   - SDK: 选择 17
   - Language Level: 17

2. **设置模块SDK**：
   - File → Project Structure → Modules
   - 选择你的模块
   - Dependencies → Module SDK: 选择 17

3. **设置Maven配置**：
   - File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
   - JRE: 选择 17

4. **重新导入Maven项目**：
   - 右键项目 → Maven → Reload Project

### 问题3：Maven 编译失败

**检查 pom.xml**：
```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

### 问题4：找不到 JAVA_HOME

**手动设置**（每次打开命令行都需要）：
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot
```

**永久设置**（需要管理员权限）：
```cmd
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot" /M
```

## 📋 验证清单

完成升级后，请确认：

- [ ] `java -version` 显示 17.0.x
- [ ] `javac -version` 显示 17.0.x
- [ ] `echo %JAVA_HOME%` 显示正确的JDK 17路径
- [ ] IDEA 项目SDK设置为 17
- [ ] Maven 成功编译项目
- [ ] 应用成功启动

## 🎯 升级完成后的测试

```bash
# 1. 清理并重新编译
cd D:\alert4
mvn clean package -DskipTests

# 2. 启动应用（使用正常配置）
java -jar target\data-platform-1.0.0.jar

# 3. 或使用最小配置测试
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

## 🆘 需要帮助？

如果升级过程中遇到问题，请提供：

1. `java -version` 的完整输出
2. `echo %JAVA_HOME%` 的输出
3. `echo %PATH%` 的输出
4. 遇到的具体错误信息
5. 使用的安装包名称

## 📝 快速命令参考

```bash
# 检查Java版本
java -version
javac -version

# 查看环境变量
echo %JAVA_HOME%
echo %PATH%

# 临时设置Java 17（当前窗口有效）
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

# 重新编译项目
cd D:\alert4
mvn clean package -DskipTests

# 启动应用
java -jar target\data-platform-1.0.0.jar
```

---

**现在开始升级吧！** 🚀

下载地址再次提供：
- **Eclipse Temurin（推荐）**：https://adoptium.net/temurin/releases/?version=17
- **Oracle JDK**：https://www.oracle.com/java/technologies/downloads/#java17
