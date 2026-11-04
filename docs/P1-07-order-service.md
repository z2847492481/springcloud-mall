# P1-07 创建 mall-order 服务

> 前置：先按 [P0-03 Kafka 部署](P0-03-kafka-docker.md) 启动 Kafka，并创建 `order-created`、`order-cancelled` 两个 topic。
> 范围：建模块 → 建 `mall_order` 库与订单表 → 配置数据源、Feign、Kafka → 启动注册。
> 本步骤不写业务代码；启动类、实体、Mapper、Service、Controller、消息生产由我后续补。

---

## 一、模块信息

| 项 | 内容 |
| --- | --- |
| 端口 | 8084 |
| 服务名 | `mall-order` |
| 数据库 | `mall_order` |
| 命名空间 | `dev` |
| Kafka | `localhost:9094`（生产端） |
| 依赖服务 | `mall-product`、`mall-stock`（OpenFeign） |

后续接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/order/create` | 下单 |
| GET | `/order/{orderNo}` | 查询订单 |
| POST | `/order/{orderNo}/pay` | 模拟支付回调 |

---

## 二、建库建表

在 WSL 终端执行：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

把下面 SQL 存成 `/tmp/mall-order-init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS mall_order
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_order;

CREATE TABLE IF NOT EXISTS t_order (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no     VARCHAR(64)     NOT NULL COMMENT '订单号',
  user_id      BIGINT UNSIGNED NOT NULL COMMENT '下单用户',
  total_amount DECIMAL(10, 2)  NOT NULL COMMENT '订单总额',
  status       VARCHAR(20)     NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CANCELLED/CLOSED',
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pay_time     DATETIME        DEFAULT NULL,
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_id (user_id),
  KEY idx_status_create_time (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';

CREATE TABLE IF NOT EXISTS t_order_item (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id     BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
  product_id   BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
  product_name VARCHAR(64)     NOT NULL COMMENT '商品名称快照',
  price        DECIMAL(10, 2)  NOT NULL COMMENT '成交单价',
  quantity     INT             NOT NULL COMMENT '数量',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表';

-- 追加订单库权限
GRANT ALL PRIVILEGES ON mall_order.* TO 'mall'@'%';
FLUSH PRIVILEGES;
```

导入：

```bash
docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /tmp/mall-order-init.sql
```

验证：

```bash
docker exec mysql sh -c 'mysql -umall -pmall123456 -D mall_order -e "SHOW TABLES;"'
```

### 表设计要点

- `order_no` 唯一索引：既是业务单号，也是后续 Kafka 消费的幂等键。
- `status` 用字符串而不是数字：状态流转更直观，面试时也方便讲。
- `t_order_item` 冗余 `product_name`、`price`：订单要保存下单时的商品快照，不能依赖商品后续改价。
- `idx_status_create_time`：XXL-JOB 扫描超时订单会用到。

---

## 三、IDEA 创建模块

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**，ArtifactId 填 `mall-order`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 选 `mall-cloud`。
3. 确认父 POM 的 `<modules>` 里出现 `mall-order`。
4. 用第四节 XML 替换 POM。
5. 新建 `src/main/resources/application.yml`，内容见第五节。
6. 业务代码与启动类不用写。

---

## 四、`mall-order/pom.xml`

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

    <artifactId>mall-order</artifactId>
    <packaging>jar</packaging>
    <name>mall-order</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <!-- Feign 调用 product / stock，mall-api 已带 openfeign -->
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-api</artifactId>
        </dependency>

        <!-- lb:// 负载均衡（显式声明，避免只靠传递依赖） -->
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

        <!-- Kafka 生产端 -->
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
  port: 8084

spring:
  application:
    name: mall-order
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_order?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
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

要点：

- `bootstrap-servers` 必须写 `localhost:9094`（Windows 侧），不是 `9092`。
- `key-serializer` 用 String：消息 key 固定用 `orderNo`，保证同一订单进入同一分区。
- `acks: all` + `enable.idempotence: true`：生产端不丢、不重的基础配置。
- 消费端配置等写 Kafka 消费者时再加。

---

## 六、包结构

```
mall-order/src/main/java/com/mall/order/
├── OrderApplication.java       ← 我补
├── controller/                 ← 我补 OrderController
├── service/                    ← 我补 OrderService / OrderServiceImpl
├── mapper/                     ← 我补 OrderMapper / OrderItemMapper
├── entity/                     ← 我补 Order / OrderItem
├── feign/                      ← 我补 Feign 调用封装与降级
└── mq/                         ← 我补 OrderEventProducer
```

启动类约定：

- `com.mall.order`
- `@SpringBootApplication(scanBasePackages = "com.mall")`
- `@EnableDiscoveryClient`
- `@EnableFeignClients(basePackages = "com.mall.api")`
- `@MapperScan("com.mall.order.mapper")`

---

## 七、下单链路（代码补齐后实现）

```
POST /order/create
  1. 网关解析 JWT，把 userId 放入请求头（当前阶段先用请求体里的 userId）
  2. Feign 调 mall-product 查商品与价格
  3. Feign 调 mall-stock 预占库存
  4. 本地事务写 t_order + t_order_item
  5. 事务提交后发送 Kafka order-created（key = orderNo）
  6. 返回订单号
```

关键点：

- 第 5 步必须在事务提交后发送，否则事务回滚了消息却已经发出去。
- 第 3 步成功、第 4 步失败时，需要调用 `unlock` 做补偿；代码里会处理。
- `orderNo` 用「时间 + 用户 + 随机」生成，并依赖数据库唯一索引兜底。

---

## 八、验收标准

- [ ] `mvn -q clean package -DskipTests` 成功。
- [ ] 启动后 `http://localhost:8084/actuator/health` 返回 `UP`。
- [ ] Nacos `dev` 命名空间出现 `mall-order`。
- [ ] 用 `mall` 账号能查到 `t_order`、`t_order_item` 两张表。
- [ ] `docker exec kafka ... --list` 能看到 `order-created`、`order-cancelled`。
- [ ] Windows 侧 `Test-NetConnection localhost -Port 9094` 通过。

---

## 九、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动正常但发消息超时 | Kafka 未启动或 advertised listeners 配错 | 先按 P0-03 验证 9094 可访问 |
| 消费端收不到消息 | 消息 key 为空导致分区随机 | key 固定用 `orderNo` |
| 事务回滚但消息已发 | 在事务内直接 `kafkaTemplate.send` | 用 `TransactionSynchronization` 或 `@TransactionalEventListener` 在提交后发送 |
| 重复下单 | 客户端重试 / Feign 重试 | `orderNo` 唯一索引 + 接口幂等 |
| Feign 调用偶发超时 | 默认超时太短 | 后续在 yml 配置 connect/read timeout |
| 库存预占成功但订单写库失败 | 本地事务异常 | 捕获异常后调 `unlock` 补偿 |
| 时间字段为空 | 实体没包含 `createTime` / 未依赖数据库默认值 | 插入时不要显式传 null，交给数据库默认值 |

---

## 十、完成后

建好模块、导完 SQL、写好 `application.yml` 后告诉我。我补启动类、订单实体、Mapper、Service、Controller、Feign 调用与 Kafka 生产端，并跑通一次完整下单。
