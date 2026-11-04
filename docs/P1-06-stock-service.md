# P1-06 创建 mall-stock 服务

> 范围：建模块 → 建 `mall_stock` 库与 `t_stock` 表 → 配置连接与 Nacos → 启动注册。
> 本步骤不写业务代码；启动类、Entity、Mapper、Service、Controller 由我后续补。

---

## 一、模块信息

| 项 | 内容 |
| --- | --- |
| 端口 | 8083 |
| 服务名 | `mall-stock` |
| 数据库 | `mall_stock` |
| 命名空间 | `dev` |
| 对外接口（后续补） | `POST /stock/lock`、`POST /stock/unlock` |

这两个接口与 `mall-api` 里的 `StockApi` 契约一致，请求体为 `StockLockRequest`。

---

## 二、建库建表

在 WSL 终端执行：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

把下面 SQL 存成 `/tmp/mall-stock-init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS mall_stock
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_stock;

CREATE TABLE IF NOT EXISTS t_stock (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id  BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
  available   INT             NOT NULL DEFAULT 0 COMMENT '可售库存',
  locked      INT             NOT NULL DEFAULT 0 COMMENT '预占库存',
  version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存表';

-- product_id 必须与 mall_product 里已有的商品对应（当前是 1、2）
INSERT INTO t_stock (product_id, available, locked, version) VALUES
  (1, 100, 0, 0),
  (2, 50, 0, 0)
ON DUPLICATE KEY UPDATE product_id = product_id;

-- 追加库存库权限
GRANT ALL PRIVILEGES ON mall_stock.* TO 'mall'@'%';
FLUSH PRIVILEGES;
```

导入：

```bash
docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /tmp/mall-stock-init.sql
```

验证：

```bash
docker exec mysql sh -c 'mysql -umall -pmall123456 -D mall_stock -e "SELECT product_id, available, locked FROM t_stock;"'
```

### 为什么有两个库存字段

| 字段 | 含义 |
| --- | --- |
| `available` | 还能卖的库存 |
| `locked` | 下单后预占、尚未真正扣减的库存 |

下单时 `available - n`、`locked + n`；支付成功后把 `locked - n`；订单取消则 `locked - n`、`available + n`。这样在「预占」和「真实扣减」之间留出中间状态，避免超卖。

---

## 三、IDEA 创建模块

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**，ArtifactId 填 `mall-stock`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 选 `mall-cloud`。
3. 确认父 POM 的 `<modules>` 里出现 `mall-stock`。
4. 用第四节 XML 替换 POM。
5. 新建 `src/main/resources/application.yml`，内容见第五节。
6. 业务代码与启动类不用写。

---

## 四、`mall-stock/pom.xml`

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

    <artifactId>mall-stock</artifactId>
    <packaging>jar</packaging>
    <name>mall-stock</name>

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

        <!-- 复用 StockLockRequest 与 StockApi 契约 -->
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
  port: 8083

spring:
  application:
    name: mall-stock
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_stock?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
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

与商品服务的差异只有端口、服务名、数据库名。

---

## 六、包结构

```
mall-stock/src/main/java/com/mall/stock/
├── StockApplication.java       ← 我补
├── controller/                 ← 我补 StockController
├── service/                    ← 我补 StockService / StockServiceImpl
├── mapper/                     ← 我补 StockMapper
└── entity/                     ← 我补 Stock
```

启动类约定：`com.mall.stock` + `scanBasePackages = "com.mall"` + `@MapperScan("com.mall.stock.mapper")` + `@EnableDiscoveryClient`。

---

## 七、验收标准

- [ ] `mvn -q clean package -DskipTests` 成功。
- [ ] 启动后 `http://localhost:8083/actuator/health` 返回 `UP`。
- [ ] Nacos `dev` 命名空间出现 `mall-stock`。
- [ ] 用 `mall` 账号能查到两条库存记录。
- [ ] 代码补齐后，`POST /stock/lock` 能把 `available` 转为 `locked`。

---

## 八、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动后查询报 `Access denied` | `mall` 账号未授权 `mall_stock` 库 | 补 `GRANT ALL PRIVILEGES ON mall_stock.* TO 'mall'@'%'` |
| 库存记录与商品对不上 | `product_id` 写错 | 先查 `mall_product.t_product` 的 id 再插入 |
| 扣减后库存变负数 | 没有 `available >= n` 条件 | 业务代码用条件更新，不要先查再改 |
| 并发扣减互相覆盖 | 读-改-写非原子 | 用 `UPDATE ... WHERE available >= ?` 或乐观锁 `version` |
| Nacos 看不到服务 | namespace 不一致 | 统一 `dev` |
| 端口冲突 | 8083 被占用 | 换端口或停掉占用进程 |

---

## 九、完成后

建好模块、导完 SQL、写好 `application.yml` 后告诉我。我补启动类、实体、Mapper、Service、Controller，实现 `lock` / `unlock`，并验证库存流转。
