# P1-08 创建 mall-wms 服务

> 定位：模拟仓库系统，订单支付后由仓库确认出库，触发库存从「预占」变成「真正扣减」。
> 范围：建模块 → 建 `mall_wms` 库 → 配置 Feign、Kafka 生产端 → 启动注册。
> 本步骤只提供一个接口：`POST /wms/outbound/{orderNo}`。业务代码由我后续补。

---

## 一、模块信息

| 项 | 内容 |
| --- | --- |
| 端口 | 8085 |
| 服务名 | `mall-wms` |
| 数据库 | `mall_wms` |
| 命名空间 | `dev` |
| 依赖服务 | `mall-order`（Feign） |
| Kafka | 生产 `order-shipped`，`localhost:9094` |

---

## 二、建库建表

在 WSL 终端执行：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

SQL 存成 `/tmp/mall-wms-init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS mall_wms
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_wms;

CREATE TABLE IF NOT EXISTS t_outbound (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no    VARCHAR(64)     NOT NULL COMMENT '订单号',
  status      VARCHAR(20)     NOT NULL DEFAULT 'SHIPPED' COMMENT '出库状态',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '出库单';

GRANT ALL PRIVILEGES ON mall_wms.* TO 'mall'@'%';
FLUSH PRIVILEGES;
```

导入：

```bash
docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /tmp/mall-wms-init.sql
```

`uk_order_no` 是出库幂等键：同一订单只能确认出库一次。

---

## 三、IDEA 创建模块

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**，ArtifactId 填 `mall-wms`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 选 `mall-cloud`。
3. 确认父 POM 的 `<modules>` 里出现 `mall-wms`。
4. 用第四节 XML 替换 POM。
5. 新建 `src/main/resources/application.yml`，内容见第五节。
6. 业务代码与启动类不用写。

---

## 四、`mall-wms/pom.xml`

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

    <artifactId>mall-wms</artifactId>
    <packaging>jar</packaging>
    <name>mall-wms</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <!-- OrderApi：查订单、更新发货状态 -->
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
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

        <!-- 生产 order-shipped -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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
  port: 8085

spring:
  application:
    name: mall-wms
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 5000
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_wms?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: mall
    password: mall123456
  kafka:
    bootstrap-servers: localhost:9094
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true

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

---

## 六、确认出库接口设计

`POST /wms/outbound/{orderNo}`

处理流程：

```
1. Feign 调 OrderApi.getByOrderNo(orderNo)
   - 订单不存在 → ORDER_NOT_FOUND
   - 状态不是 PAID / SHIPPED → ORDER_STATUS_INVALID
2. 已经是 SHIPPED：直接返回成功（幂等）
3. 写 t_outbound（INSERT IGNORE，order_no 唯一）
4. Feign 调 OrderApi.ship：PAID → SHIPPED
5. 发送 Kafka order-shipped（key = orderNo，携带订单明细）
6. stock-service 消费后 locked - quantity
```

代码补齐后的包结构：

```
mall-wms/src/main/java/com/mall/wms/
├── WmsApplication.java      ← 我补
├── controller/              ← WmsController
├── service/                 ← OutboundService
├── mapper/                  ← OutboundMapper
├── entity/                  ← Outbound
└── mq/                      ← OrderShippedProducer
```

启动类约定：

- `com.mall.wms`
- `@SpringBootApplication(scanBasePackages = "com.mall")`
- `@EnableDiscoveryClient`
- `@EnableFeignClients(clients = OrderApi.class)`
- `@MapperScan("com.mall.wms.mapper")`

---

## 七、验收标准

- [ ] `mvn -q clean package -DskipTests` 成功。
- [ ] 启动后 `http://localhost:8085/actuator/health` 返回 `UP`。
- [ ] Nacos `dev` 命名空间出现 `mall-wms`。
- [ ] 能查到 `t_outbound` 表。
- [ ] 订单未支付时确认出库返回 `ORDER_STATUS_INVALID`。
- [ ] 订单支付后确认出库：订单变 `SHIPPED`，库存 `locked` 变为 0。

---

## 八、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 确认出库报订单状态不允许 | 订单还是 CREATED | 先调用支付接口 |
| 出库记录重复 | 没做唯一索引或没处理重复插入 | `uk_order_no` + `INSERT IGNORE` |
| 库存没有变化 | `order-shipped` 未发出或消费端未启动 | 查 Kafka topic 与 stock 日志 |
| Nacos 看不到服务 | namespace 不一致 | 统一 `dev` |
| 端口冲突 | 8085 被占用 | 换端口 |
| `mall` 账号无权限 | 未授权 `mall_wms` 库 | 补 `GRANT` |
