# P1-02 创建 mall-common 与 mall-api

> 范围：只建两个**库模块**的骨架（POM + 包目录），不写业务代码、不建启动类、不建服务模块。
> 建好后告诉我，`Result`、异常处理、DTO、Feign 接口这些样板代码我直接补齐，你不用手抄。

---

## 一、两个模块的职责边界

| 模块 | 放什么 | 不放什么 | 谁依赖它 |
| --- | --- | --- | --- |
| `mall-common` | 统一响应 `Result`、错误码、业务异常、全局异常处理、常量、TraceId 工具 | 业务逻辑、Mapper、启动类、`spring-boot-starter-web` | 所有服务 + gateway |
| `mall-api` | Feign 接口 + 请求/响应 DTO（服务间契约） | 实现类、Controller、Mapper、启动类 | 只被业务服务依赖，**gateway 不依赖** |

关键约束：`mall-common` **不能引 `spring-boot-starter-web`**。否则 gateway（WebFlux）依赖它时会同时出现 Spring MVC，启动直接报错。只引 `spring-web` 这一个底层包即可。

---

## 二、IDEA 创建步骤

两个模块操作完全一样，重复两次。

1. 在项目树中右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**（不要选 Spring Boot 模板）。
3. 填写：
   - Name：`mall-common`
   - ArtifactId：`mall-common`
   - GroupId：`com.mall`
   - Version：`1.0.0-SNAPSHOT`
   - Parent：确认是 `mall-cloud`（从根工程右键进来通常是默认值）
4. 点 **Create**，不要勾选创建示例代码/启动类。
5. 同样方式创建 `mall-api`。

创建后 IDEA 可能自动往父 POM 写 `<modules>`，需要人工检查是否与第三节一致。

---

## 三、需要手动确认/修改的配置

### 3.1 父 `pom.xml` 增加 modules

顺序不重要，但目录名必须和 `<module>` 值一致：

```xml
<modules>
    <module>mall-common</module>
    <module>mall-api</module>
</modules>
```

### 3.2 `mall-common/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mall</groupId>
        <artifactId>mall-cloud</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mall-common</artifactId>
    <packaging>jar</packaging>
    <name>mall-common</name>

    <dependencies>
        <!-- 只用底层 spring-web 提供 @RestControllerAdvice，不能引 starter-web -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>

        <!-- BindException 等校验相关类位于 spring-context -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>

        <!-- 全局异常处理与 TraceId 需要 slf4j 门面 -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### 3.3 `mall-api/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mall</groupId>
        <artifactId>mall-cloud</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mall-api</artifactId>
    <packaging>jar</packaging>
    <name>mall-api</name>

    <dependencies>
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-common</artifactId>
        </dependency>

        <!-- 提供 @FeignClient，调用方不必再单独引 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- DTO 上的 @NotBlank 等校验注解 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

两个模块都**不要**加 `spring-boot-maven-plugin`，库模块打成普通 jar 即可。

---

## 四、包结构约定

只建目录，文件由我后续补齐。

```
mall-common/src/main/java/com/mall/common/
├── result/        Result.java、PageResult.java
├── enums/         ErrorCode.java
├── exception/     BusinessException.java、GlobalExceptionHandler.java
├── constant/      CommonConstants.java（Header 名、Topic 名等）
└── util/          TraceIdUtil.java

mall-api/src/main/java/com/mall/api/
├── user/          UserApi.java + dto/LoginRequest、LoginResponse、UserDTO
├── product/       ProductApi.java + dto/ProductDTO、ProductQuery
└── stock/         StockApi.java + dto/StockLockRequest
```

`mall-api` 不放 `order` 的接口：目前没有别的服务需要调用订单服务。

---

## 五、后续服务模块的约定（现在不做，先知道）

- 启动类统一放在 `com.mall.<服务名>`，例如 `com.mall.order`。
- 启动类加 `scanBasePackages = "com.mall"`，否则扫描不到 `com.mall.common` 里的全局异常处理器。
- 调用方服务在启动类加 `@EnableFeignClients(basePackages = "com.mall.api")`。

---

## 六、验收标准

- [ ] IDEA 项目树中 `mall-common`、`mall-api` 都挂在 `mall-cloud` 下。
- [ ] 父 POM 的 `<modules>` 与目录名一致。
- [ ] 两个子 POM 的 `packaging` 为 `jar`，且没有 Boot 打包插件。
- [ ] `mall-common` 依赖里没有 `spring-boot-starter-web`。
- [ ] 根目录执行 `mvn -q clean install -DskipTests` 成功，两个模块都安装到本地仓库。

---

## 七、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 子模块 POM 飘红，提示 parent 找不到 | 父 POM 未先 install / 相对路径不对 | 在根目录先 `mvn install -N`，再 Reload |
| 子模块被识别成独立工程 | 创建时 Parent 没选 `mall-cloud` | 检查子 POM 的 `<parent>` 段 |
| `mvn` 报 "Child module ... does not exist" | `<module>` 名字与目录名不一致 | 按实际目录名改 |
| gateway 启动报 Spring MVC 冲突 | common 引了 `starter-web` | common 只用 `spring-web` |
| DTO 校验注解编译不过 | 缺 validation 依赖 | 确认 `mall-api` 引入了 `spring-boot-starter-validation` |
| 编译报「找不到 getter/setter」 | Lombok 注解处理未生效 | 父 POM 已覆盖 Lombok 1.18.36；子模块需自行声明 `lombok`（provided） |

---

## 八、完成后

执行 `mvn -q clean install -DskipTests` 通过后告诉我，我直接把 `Result`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler` 以及 `mall-api` 下的 DTO/Feign 接口写进你建好的目录里。
