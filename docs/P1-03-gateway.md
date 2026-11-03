# P1-03 创建 mall-gateway

> 范围：只建网关模块骨架（POM + 启动类位置 + 路由配置），不做 JWT 过滤器、限流、跨域。
> 网关不依赖数据库，先建它可以直接验证「WebFlux 与 Spring MVC 冲突」这个高频坑。

---

## 一、模块职责

| 项 | 内容 |
| --- | --- |
| 端口 | 8080 |
| 服务名 | `mall-gateway`（Nacos 注册名） |
| 职责 | 统一入口、按路径路由到四个业务服务 |
| 本阶段不做 | JWT 鉴权、Redis 限流、CORS、日志过滤器 |

路由规则：

| 外部请求 | 转发目标 |
| --- | --- |
| `/api/user/**` | `lb://mall-user` → `/user/**` |
| `/api/product/**` | `lb://mall-product` → `/product/**` |
| `/api/stock/**` | `lb://mall-stock` → `/stock/**` |
| `/api/order/**` | `lb://mall-order` → `/order/**` |

`StripPrefix=1` 的作用是去掉 `/api` 这一段，业务服务的 Controller 仍按 `/user/**` 编写。

---

## 二、IDEA 创建步骤

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**。
3. 填写 Name / ArtifactId：`mall-gateway`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 确认是 `mall-cloud`。
4. 创建后确认父 POM 的 `<modules>` 里出现 `mall-gateway`。
5. 在 `mall-gateway/src/main/resources` 下新建 `application.yml`，内容见第四节。
6. 启动类 `com.mall.gateway.GatewayApplication` 先不用写，等你建好模块我直接补。

---

## 三、`mall-gateway/pom.xml`

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

    <artifactId>mall-gateway</artifactId>
    <packaging>jar</packaging>
    <name>mall-gateway</name>

    <dependencies>
        <!-- 响应式网关，绝对不能引 spring-boot-starter-web -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <!-- lb:// 协议依赖负载均衡器 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- 统一响应与错误码，后续 JWT 过滤器要用；注意它只带 spring-web，不带 MVC -->
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-common</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 只有可启动模块才声明 Boot 打包插件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

注意：网关**不要**依赖 `mall-api`，它不需要 Feign 契约。

---

## 四、`application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: mall-gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: mall-user
          uri: lb://mall-user
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1
        - id: mall-product
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1
        - id: mall-stock
          uri: lb://mall-stock
          predicates:
            - Path=/api/stock/**
          filters:
            - StripPrefix=1
        - id: mall-order
          uri: lb://mall-order
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=1

management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
  endpoint:
    health:
      show-details: always
```

说明：

- `discovery.locator.enabled: false` 表示关闭「按服务名自动生成路由」，只用上面手写的 4 条。
- `namespace: dev` 填的是 Nacos 的**命名空间 ID**，不是控制台显示的命名空间名称。新建命名空间时要把 ID 显式写成 `dev`，否则 Nacos 会自动生成一串 UUID，客户端写 `dev` 就会注册到一个控制台看不到的“隐形”命名空间。
- 四个业务服务后续必须使用同一个命名空间 ID，否则互相发现不到。
- 目前 Nacos 尚未启动，启动时会打印注册失败日志，但网关本身可以正常启动，不影响本阶段验收。
- `lb://` 表示「从注册中心取实例列表并负载均衡」，服务未注册时请求会返回 503。

---

## 五、包结构

```
mall-gateway/src/main/java/com/mall/gateway/
└── GatewayApplication.java        ← 我后续补

后续阶段再加：
├── filter/     JWT 鉴权、TraceId
├── config/     CORS、限流
```

---

## 六、验收标准

- [ ] IDEA 项目树中出现 `mall-gateway`，父 POM 的 `<modules>` 已包含它。
- [ ] POM 中没有 `spring-boot-starter-web`。
- [ ] `mvn -q clean package -DskipTests` 成功。
- [ ] 启动后日志显示 `Netty started on port 8080`，没有 `Spring MVC found on classpath` 报错。
- [ ] 访问 `http://localhost:8080/actuator/health` 返回 `UP`。
- [ ] 访问 `http://localhost:8080/api/user/1` 返回 503（服务未启动），说明路由已生效而不是 404。

---

## 七、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `Spring MVC found on classpath` | 引入了 `spring-boot-starter-web`（包括间接引入） | 用 `mvn dependency:tree` 排查，移除 |
| 路由一直 404 | 路径写错或未加 `StripPrefix` | 对照第一节的映射表 |
| 路由 503 | 目标服务未注册到 Nacos | 先启动对应服务 |
| `lb://` 报没有 LoadBalancer | 缺 `spring-cloud-starter-loadbalancer` | 已在 POM 中引入 |
| 启动报「没有主清单属性」，jar 只有几 KB | 未绑定 `repackage` | 父 POM 的 `pluginManagement` 已配置 `repackage` 执行 |
| 路由失败返回 HTTP 200 + 业务码 500 | 网关扫描了 `mall-common` 的全局异常处理器 | 启动类不要加 `scanBasePackages`，保持默认扫描 `com.mall.gateway` |
| 服务已启动且日志显示注册成功，控制台列表仍为空 | `namespace` 填了名称，而实际命名空间 ID 是 UUID | 查 `tenant_info.tenant_id`，配置里填 ID；或重建命名空间时把 ID 显式设为 `dev` |
| `/actuator/gateway` 打不开 | 未暴露端点 | 确认 `management.endpoints.web.exposure.include` 含 `gateway` |
| Nacos 注册日志报错 | 注册中心未启动 | 本阶段正常，后续部署 Nacos 后消失 |

---

## 八、完成后

模块和 `application.yml` 建好后告诉我，我补 `GatewayApplication`，并顺手确认依赖树里没有 Spring MVC。
