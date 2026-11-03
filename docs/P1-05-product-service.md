# P1-05 创建 mall-product 服务

> 范围：建模块 → 建 `mall_product` 库与 `t_product` 表 → 配数据源与 Nacos → 启动注册。
> 本步骤不写业务代码；Entity / Mapper / Service / Controller 你建好模块后我补。

---

## 一、模块信息

| 项 | 内容 |
| --- | --- |
| 端口 | 8082 |
| 服务名 | `mall-product` |
| 数据库 | `mall_product` |
| 命名空间 | `dev` |
| 对外接口（后续补） | `GET /product/{id}`、`GET /product/list` |

---

## 二、建库建表

在 WSL 终端执行：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

把下面 SQL 存成 `/tmp/mall-product-init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS mall_product
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_product;

CREATE TABLE IF NOT EXISTS t_product (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(64)    NOT NULL COMMENT '商品名称',
  price       DECIMAL(10, 2) NOT NULL COMMENT '单价',
  status      TINYINT        NOT NULL DEFAULT 1 COMMENT '1 上架，0 下架',
  create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

INSERT INTO t_product (name, price, status) VALUES
  ('机械键盘', 399.00, 1),
  ('无线鼠标', 129.00, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 复用已有的 mall 账号，只追加本库的权限
GRANT ALL PRIVILEGES ON mall_product.* TO 'mall'@'%';
FLUSH PRIVILEGES;
```

导入：

```bash
docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /tmp/mall-product-init.sql
```

验证：

```bash
docker exec mysql sh -c 'mysql -umall -pmall123456 -D mall_product -e "SELECT id, name, price, status FROM t_product;"'
```

> 注意：`mall` 账号上一步只授权了 `mall_user`，这里必须补 `GRANT`，否则服务启动后查询会报 `Access denied`。

---

## 三、IDEA 创建模块

1. 右键根工程 `mall-cloud` → **New → Module**。
2. 左侧选 **Maven**，ArtifactId 填 `mall-product`，GroupId `com.mall`，Version `1.0.0-SNAPSHOT`，Parent 选 `mall-cloud`。
3. 确认父 POM 的 `<modules>` 里出现 `mall-product`。
4. 用第四节 XML 替换 `mall-product/pom.xml`。
5. 新建 `src/main/resources/application.yml`，内容见第五节。
6. 业务代码与启动类不用写。

---

## 四、`mall-product/pom.xml`

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

    <artifactId>mall-product</artifactId>
    <packaging>jar</packaging>
    <name>mall-product</name>

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

        <!-- 复用 ProductDTO / ProductApi 契约 -->
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
  port: 8082

spring:
  application:
    name: mall-product
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_product?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
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

与 `mall-user` 的差异只有三处：`port`、`application.name`、数据库名。

---

## 六、包结构

```
mall-product/src/main/java/com/mall/product/
├── ProductApplication.java     ← 我补
├── controller/                 ← 我补 ProductController
├── service/                    ← 我补 ProductService / ProductServiceImpl
├── mapper/                     ← 我补 ProductMapper
├── entity/                     ← 我补 Product
└── config/                     ← 我补 MybatisPlusConfig（分页插件）
```

启动类约定：`com.mall.product` + `scanBasePackages = "com.mall"` + `@MapperScan("com.mall.product.mapper")` + `@EnableDiscoveryClient`。

---

## 七、验收标准

- [ ] `mvn -q clean package -DskipTests` 成功。
- [ ] 启动后 `http://localhost:8082/actuator/health` 返回 `UP`。
- [ ] Nacos `dev` 命名空间出现 `mall-product`。
- [ ] 用 `mall` 账号能查到两条种子商品数据。
- [ ] 网关启动时，`http://localhost:8080/api/product/1` 能返回商品（代码补齐后验证）。

---

## 八、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动后查询报 `Access denied` | `mall` 账号未授权 `mall_product` 库 | 补 `GRANT ALL PRIVILEGES ON mall_product.* TO 'mall'@'%'` |
| 价格字段精度丢失 | 用了 `double` | 实体用 `BigDecimal`，表用 `DECIMAL(10,2)` |
| 列表接口参数为空 | Feign 的 GET 请求直接传对象 | 调用方用 `@SpringQueryMap`（`mall-api` 已写好） |
| Nacos 看不到服务 | namespace 与网关不一致 | 统一 `dev` |
| 端口冲突 | 8082 被占用 | 换端口或停掉占用进程 |
| Mapper 注入失败 | 没加 `@MapperScan` | 启动类补注解 |
| 分页不生效，`pageSize` 没起作用 | 缺分页插件 | 已在 `config/MybatisPlusConfig` 注册 `PaginationInnerInterceptor` |

---

## 九、完成后

建好模块、导完 SQL、写好 `application.yml` 后告诉我。我补启动类、实体、Mapper、Service、Controller，并验证直连和走网关两种调用。
