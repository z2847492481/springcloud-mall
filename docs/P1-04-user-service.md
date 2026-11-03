# P1-04 创建 mall-user 服务

> 范围：只建模块骨架 → 建库建表 → 配置连接 → 启动并注册到 Nacos。
> 本步骤不写登录、JWT、业务接口；Entity / Mapper / Service / Controller 这些样板代码，你建好模块后我直接补。

---

## 一、模块信息

| 项 | 内容 |
| --- | --- |
| 端口 | 8081 |
| 服务名 | `mall-user` |
| 依赖 | Web、Nacos Discovery、MyBatis-Plus、MySQL、Actuator、mall-api |
| 数据库 | `mall_user` |
| 命名空间 | `dev` |

---

## 二、先建库建表

在 WSL 终端执行（root 密码仍用之前的方式取）：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

### 2.1 建库、建表、建应用账号

把下面 SQL 存成 `/tmp/mall-user-init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS mall_user
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_user;

CREATE TABLE IF NOT EXISTS t_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(32)  NOT NULL COMMENT '用户名',
  password    VARCHAR(64)  NOT NULL COMMENT '密码',
  phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

INSERT INTO t_user (username, password, phone, status)
VALUES ('demo', '123456', '13800000000', 1)
ON DUPLICATE KEY UPDATE username = username;

-- 本地学习用的应用账号，真实项目不要提交密码
CREATE USER IF NOT EXISTS 'mall'@'%' IDENTIFIED BY 'mall123456';
GRANT ALL PRIVILEGES ON mall_user.* TO 'mall'@'%';
FLUSH PRIVILEGES;
```

导入：

```bash
docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /tmp/mall-user-init.sql
```

验证：

```bash
docker exec mysql sh -c 'mysql -umall -pmall123456 -D mall_user -e "SELECT id, username, status FROM t_user;"'
```

> 为什么用 `mall` 账号而不是 root：避免把 root 密码写进仓库；应用账号只授权 `mall_user` 库，后续每个服务各自授权。

---

## 三、IDEA 创建模块

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**，ArtifactId 填 `mall-user`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 选 `mall-cloud`。
3. 确认父 POM 的 `<modules>` 里出现 `mall-user`。
4. 用下面的 XML 替换 `mall-user/pom.xml`。
5. 新建 `src/main/resources/application.yml`，内容见第五节。
6. 启动类与业务代码不用写，我后续补。

---

## 四、`mall-user/pom.xml`

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

    <artifactId>mall-user</artifactId>
    <packaging>jar</packaging>
    <name>mall-user</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- 复用 LoginRequest / LoginResponse / UserDTO -->
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 五、`application.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: mall-user
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_user?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: mall
    password: mall123456

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
      table-prefix: t_

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

要点：

- `namespace: dev` 是命名空间 ID，必须与网关一致。
- 连接串四个参数别删：`useSSL=false`、`allowPublicKeyRetrieval=true`（MySQL 8 认证）、`serverTimezone=Asia/Shanghai`、`characterEncoding=utf8`。
- `table-prefix: t_` 表示实体 `User` 映射到 `t_user`。

---

## 六、包结构

```
mall-user/src/main/java/com/mall/user/
├── UserApplication.java        ← 我补
├── controller/                 ← 我补 UserController
├── service/                    ← 我补 UserService / UserServiceImpl
├── mapper/                     ← 我补 UserMapper
└── entity/                     ← 我补 User
```

启动类约定：

- 包名 `com.mall.user`
- `@SpringBootApplication(scanBasePackages = "com.mall")`，否则扫不到公共异常处理器
- `@MapperScan("com.mall.user.mapper")`
- `@EnableDiscoveryClient`

---

## 七、验收标准

- [ ] `mvn -q clean package -DskipTests` 成功，jar 约几十 MB（不是几 KB）。
- [ ] 启动日志显示 `Tomcat started on port 8081`，无数据源连接错误。
- [ ] `http://localhost:8081/actuator/health` 返回 `UP`。
- [ ] Nacos `dev` 命名空间的服务列表出现 `mall-user`。
- [ ] 数据库能用 `mall` 账号查到 `demo` 用户。

---

## 八、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `Access denied for user 'mall'@'...'` | 账号未创建或授权库不对 | 重新执行第二节 SQL，确认 `'mall'@'%'` |
| 报 `Public Key Retrieval is not allowed` | MySQL 8 认证插件 | 连接串加 `allowPublicKeyRetrieval=true` |
| 报时区错误 | 未指定时区 | 加 `serverTimezone=Asia/Shanghai` |
| Nacos 里看不到服务 | namespace 填了名称而不是 ID | 统一用 `dev` 这个命名空间 ID |
| 端口冲突 | 8081 被占用 | 换端口或停掉占用进程 |
| 打出来的 jar 很小 | 未走 Boot repackage | 父 POM 已统一配置，确认子模块声明了插件 |
| Mapper 注入失败 | 没加 `@MapperScan` | 启动类补注解 |
| 公共异常处理不生效 | 没加 `scanBasePackages` | 业务服务启动类加 `scanBasePackages = "com.mall"` |
| `@PathVariable` 报参数名找不到 | 未开启 `-parameters` 且注解没写名称 | 父 POM 已开启；注解建议写成 `@PathVariable("id")` |

---

## 九、完成后

建好模块、导完 SQL、`application.yml` 写好后告诉我。我补启动类、实体、Mapper、Service、Controller，并做一次启动验证。
