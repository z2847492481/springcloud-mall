# P1-01 父 POM 构建指南

> 目标：只创建父工程 `mall-cloud` 的 `pom.xml`，不创建任何子模块，不部署中间件。
> 本文档包含：父 POM 职责说明、依赖管理清单、完整 XML、IDEA 操作步骤、验收标准、常见坑。

---

## 一、这一步做什么，不做什么

**做**

- 创建一个 `packaging = pom` 的聚合父工程。
- 用 `<dependencyManagement>` 统一锁定 Spring Boot / Spring Cloud / Spring Cloud Alibaba 以及第三方依赖的版本。
- 用 `<pluginManagement>` 统一锁定编译、打包、测试插件的版本。

**不做**

- 不创建 `mall-common`、`mall-api`、`mall-gateway` 等子模块（放到后续步骤）。
- 不在父 POM 里引入任何 `spring-boot-starter-*` 实际依赖，父 POM 只做「版本管理」。
- 不继承 `spring-boot-starter-parent`（原因见第五节）。

**最终目录结构（当前阶段）**

```
d:\Projects\Java\springcloud-mall\
├── PROJECT_PLAN.md
├── docs\
│   └── P1-01-parent-pom-guide.md
└── pom.xml                      ← 本步骤唯一产物
```

---

## 二、父 POM 的职责

| 职责 | 说明 |
| --- | --- |
| 聚合模块 | 后续通过 `<modules>` 把各子模块纳入一次构建，`mvn install` 在根目录执行即可 |
| 统一版本 | 子模块写依赖时不写 `<version>`，全部由父 POM 的 `dependencyManagement` 决定 |
| 统一编译参数 | Java 21、UTF-8，避免各模块各写一套 |
| 统一插件版本 | 避免 Maven 默认插件版本过旧导致 JDK 21 编译报错 |
| 统一内部模块版本 | `mall-common`、`mall-api` 通过 `${project.version}` 引用，升级版本只改一处 |

> 关键区别：`<dependencies>` 里的依赖会被所有子模块强制继承；`<dependencyManagement>` 只提供版本和默认 scope，子模块自己决定要不要引入。父 POM 用后者。

---

## 三、依赖管理清单

### 3.1 核心 BOM（三个 import）

| BOM | 版本 | 管理的内容 |
| --- | --- | --- |
| `spring-boot-dependencies` | 3.2.4 | starter-web、validation、actuator、test、data-redis、kafka、jackson、lombok、`mysql-connector-j`、logback 等 |
| `spring-cloud-dependencies` | 2023.0.1 | gateway、openfeign、loadbalancer、circuitbreaker 等 |
| `spring-cloud-alibaba-dependencies` | 2023.0.1.0 | nacos-discovery、nacos-config、sentinel、seata（后两者本阶段不用，仅随 BOM 管理版本） |

三者必须版本配套：Boot 3.2.4 + Cloud 2023.0.1 + SCA 2023.0.1.0。

### 3.2 需要单独锁版本的第三方依赖

| 依赖 | 版本 | 用在哪 | 说明 |
| --- | --- | --- | --- |
| `mybatis-plus-spring-boot3-starter` | 3.5.7 | user / product / stock / order | Boot 3 必须用 `-spring-boot3-` 这个 artifact，旧的 `mybatis-plus-boot-starter` 不兼容 |
| `xxl-job-core` | 2.4.1 | order-service | 执行器内嵌在业务服务中 |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | user-service、gateway | 签发与校验 JWT；impl 和 jackson 是 runtime scope |
| `lombok` | 1.18.36 | 所有模块 | Boot 3.2.4 默认管理 1.18.30，在本机 JDK 21 下注解处理不生效，需显式覆盖 |

无需单独写版本、由 Boot BOM 管理的常用依赖：`mysql-connector-j`、`spring-boot-starter-validation`、`spring-kafka`、`spring-boot-starter-data-redis`、`spring-boot-starter-test`。

### 3.3 内部模块（后续添加）

| 依赖 | 版本 |
| --- | --- |
| `com.mall:mall-common` | `${project.version}` |
| `com.mall:mall-api` | `${project.version}` |

子模块之间引用必须走 `${project.version}`，不要写死 `1.0.0-SNAPSHOT`。

---

## 四、完整 pom.xml

把下面的内容整体替换 IDEA 生成的 `pom.xml` 内容。当前没有子模块，所以**不要写 `<modules>`**，后续加模块时再补。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mall</groupId>
    <artifactId>mall-cloud</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>mall-cloud</name>
    <description>Spring Cloud 面试复习项目</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <maven.compiler.release>21</maven.compiler.release>

        <spring-boot.version>3.2.4</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>

        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <xxl-job.version>2.4.1</xxl-job.version>
        <jjwt.version>0.12.6</jjwt.version>
        <lombok.version>1.18.36</lombok.version>
    </properties>

    <!--
        子模块按阶段逐个加入，例如：
        <modules>
            <module>mall-common</module>
            <module>mall-api</module>
        </modules>
    -->

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot 依赖版本 -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud 依赖版本 -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud Alibaba 依赖版本（Nacos 等） -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 覆盖 Boot BOM 中的 Lombok 版本，适配 JDK 21 高版本补丁 -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </dependency>

            <!-- MyBatis-Plus（Spring Boot 3 专用 starter） -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>

            <!-- XXL-JOB 执行器 -->
            <dependency>
                <groupId>com.xuxueli</groupId>
                <artifactId>xxl-job-core</artifactId>
                <version>${xxl-job.version}</version>
            </dependency>

            <!-- JWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>

            <!-- 内部模块，后续创建后即可被引用 -->
            <dependency>
                <groupId>com.mall</groupId>
                <artifactId>mall-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mall</groupId>
                <artifactId>mall-api</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                    <executions>
                        <!-- 未继承 spring-boot-starter-parent，需显式绑定 repackage -->
                        <execution>
                            <goals>
                                <goal>repackage</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <!-- 未继承 Boot parent，需显式开启，Spring MVC 才能反射拿到参数名 -->
                        <parameters>true</parameters>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### 为什么用 pluginManagement 而不是 plugins

- 父工程如果直接把 `spring-boot-maven-plugin` 放进 `<build><plugins>`，会被所有子模块继承并尝试执行 `repackage`，导致 `mall-common`、`mall-api` 这类 jar 库模块也被打成交付包，甚至构建失败。
- 放在 `pluginManagement` 只锁定版本，哪个模块是启动服务，就在它自己的 POM 里声明一次插件。
- 因为本项目没有继承 `spring-boot-starter-parent`，插件不会自动绑定 `repackage`。如果只声明插件、不配 `<executions>`，打出来的 jar 只有 3 KB，`java -jar` 会报「没有主清单属性」。所以 `repackage` 的执行必须显式写在 `pluginManagement` 里。
- 同理，Boot parent 默认开启的 `-parameters` 编译参数也要自己补。否则 Controller 里写 `@PathVariable Long id`（不写名称）会在运行时报 `Name for argument ... not specified`，需要给每个注解显式写名称或开启该参数。

---

## 五、为什么不继承 spring-boot-starter-parent

两种写法二选一，不要混用：

| 方案 | 写法 | 优缺点 |
| --- | --- | --- |
| A：继承 `spring-boot-starter-parent` | `<parent>` 指向 Boot | 省事，但父工程只能有一个 parent，且默认插件行为绑得较死 |
| B：import BOM（本方案） | `spring-boot-dependencies` + `scope=import` | 版本控制灵活，适合 Cloud 多模块，需自己声明插件版本 |

本项目采用方案 B，所以：

- 必须设置 `<maven.compiler.release>21</maven.compiler.release>`；
- 必须显式声明 `spring-boot-maven-plugin`、`maven-compiler-plugin` 版本；
- 启动类模块需在自身 POM 中加 `<build><plugins>` 声明 `spring-boot-maven-plugin`。

---

## 六、IDEA 操作步骤

### 6.1 创建父工程

1. 打开 IDEA，选择 **File → New → Project**。
2. 左侧选择 **Maven**（不要选 Spring Initializr）。
3. 填写：
   - Name：`mall-cloud`
   - Location：`d:\Projects\Java\springcloud-mall`
   - GroupId：`com.mall`
   - ArtifactId：`mall-cloud`
   - Version：`1.0.0-SNAPSHOT`
4. 点击 **Create**。目录里已有 `PROJECT_PLAN.md`，IDEA 若提示目录非空，继续即可。

### 6.2 替换 POM 内容

1. 打开根目录 `pom.xml`。
2. 用第四节 XML 整体替换（保留第一行 `<?xml version="1.0" encoding="UTF-8"?>`）。
3. 保存后若未自动同步，右键 `pom.xml` → **Maven → Reload project**；或点右上角出现的 Maven 刷新图标。

### 6.3 配置 Maven 与 JDK

1. **File → Settings → Build, Execution, Deployment → Build Tools → Maven**
   - Maven home：`D:\Tools\apache-maven-3.9.15`（或使用 Bundled，二选一）
   - User settings file：默认 `C:\Users\zhq123\.m2\settings.xml`
   - JDK for importer：21
2. **File → Project Structure → Project**
   - SDK：21
   - Language level：21
3. **File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors**
   - 勾选 **Enable annotation processing**（后续用 Lombok 必须开）

### 6.4 验证

在 IDEA 终端或 PowerShell 执行：

```powershell
# 1. 能解析 POM，不报错
mvn -q validate

# 2. 生成有效 POM，检查版本是否按预期生效
mvn help:effective-pom -Doutput=target\effective-pom.xml
```

打开 `target\effective-pom.xml`，确认：

- `spring-boot-dependencies` 版本为 `3.2.4`
- `spring-cloud-dependencies` 版本为 `2023.0.1`
- `spring-cloud-alibaba-dependencies` 版本为 `2023.0.1.0`
- `maven.compiler.release` 为 `21`

IDEA 侧确认：Maven 工具窗口无红色报错，`Lifecycle → validate` 可双击执行成功。

### 6.5 后续添加子模块（预演，本步骤不做）

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选择 **Maven**，ArtifactId 填 `mall-common`，GroupId 保持 `com.mall`，Version 保持 `1.0.0-SNAPSHOT`。
3. 确认 Parent 显示 `mall-cloud`。
4. IDEA 会自动在父 POM 中插入：

```xml
<modules>
    <module>mall-common</module>
</modules>
```

注意：`mall-common`、`mall-api` 是库模块，`packaging` 保持默认 `jar`，**不要**加 `spring-boot-maven-plugin`；只有 `gateway`、`user`、`product`、`stock`、`order` 这些可启动模块才加。

---

## 七、验收标准

- [ ] `pom.xml` 的 `packaging` 为 `pom`。
- [ ] 根目录执行 `mvn -q validate` 成功，无警告以外的报错。
- [ ] `mvn help:effective-pom` 中三个 BOM 版本正确。
- [ ] IDEA Maven 面板无红色依赖，Project SDK 为 21。
- [ ] 父 POM 中没有实际的 `spring-boot-starter-*` 依赖。
- [ ] 父 POM 中没有 `<modules>`（或为空，等后续再填）。

---

## 八、常见坑

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `packaging` 写成 `jar` | 聚合工程必须是 pom | 改成 `<packaging>pom</packaging>` |
| BOM 版本不生效 | 缺 `<type>pom</type>` 或 `<scope>import</scope>` | 两者都必须写，且顺序在普通依赖之前 |
| 子模块编译报「无效的目标发行版: 21」 | 未配置编译版本 | 设 `maven.compiler.release=21`，确认 IDEA 项目 SDK 为 21 |
| `mvn` 仍报 JAVA_HOME | 终端是改环境变量前打开的旧进程 | 已修复，若复现则重开终端 |
| MyBatis-Plus 启动报 `factoryBeanObjectType` | 用了 `mybatis-plus-boot-starter` | 换成 `mybatis-plus-spring-boot3-starter` |
| MySQL 驱动找不到 | 用了旧坐标 `mysql-connector-java` | 用 `com.mysql:mysql-connector-j`，版本交给 Boot BOM |
| 所有子模块都被打成可执行 jar | 父 POM 在 `<plugins>` 里声明了 Boot 插件 | 移到 `<pluginManagement>`，启动模块自行声明 |
| IDEA 不自动下载依赖 | Maven 未同步或镜像慢 | Reload project；必要时在 `settings.xml` 配阿里云镜像 |
| 运行时报 `Name for argument ... not specified` | 未开启 `-parameters`，注解里也没写名称 | 父 POM 已配置 `<parameters>true</parameters>` |
| Lombok 报找不到符号 | 未开注解处理 | Settings → Compiler → Annotation Processors 勾选启用 |

### 可选：加快依赖下载（改 `settings.xml`，不改 POM）

```xml
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

---

## 九、完成后告诉我

完成后把结果告诉我，我再给出 `mall-common` 与 `mall-api` 的创建指南（统一响应、异常处理、Feign 接口与 DTO 的放法），继续保持「你实操、我出文档」的节奏。
