# mall-cloud 项目规划（Spring Cloud 面试复习）

## 一、项目定位

**目标**：用一套业务足够简单、链路足够完整的微服务 Demo，把 Gateway、Nacos、OpenFeign、Kafka、XXL-JOB 全部串起来，做到「跑得通、讲得清、能写进简历」。

**设计原则**

- 业务简单：只做「下单 → 扣库存 → 超时关单」这一条主链路，不追求电商功能完整。
- 组件真实：每个中间件都承担明确的业务职责，而不是为了用而用。
- 阶段可验收：每个阶段都有能亲手验证的检查点，避免一次写完却说不清。
- 面试导向：每个组件都对应一组高频面试题，代码里要留下可讲的细节（幂等、超时、重试、消息不丢）。

**明确不做**：完整商城前端、真实支付对接、复杂权限体系、K8s 部署、Seata 分布式事务（列为可选扩展）。

---

## 二、技术栈与版本矩阵

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 本机已有（`java version "21.0.10"`） |
| Maven | 3.9.15 | 本机已有（`D:\Tools\apache-maven-3.9.15`） |
| Spring Boot | 3.2.4 | 与 SCA 2023.0.1.0 官方适配 |
| Spring Cloud | 2023.0.1 | Leyton |
| Spring Cloud Alibaba | 2023.0.1.0 | 提供 Nacos Discovery / Config |
| Nacos Server | 2.3.x | 注册中心 + 配置中心 |
| Spring Cloud Gateway | 随 Spring Cloud 版本 | 响应式网关，不能引入 `spring-boot-starter-web` |
| OpenFeign | 随 Spring Cloud 版本 | 声明式调用 + LoadBalancer |
| Kafka | 3.7.x（KRaft） | 单节点，去掉 Zookeeper |
| XXL-JOB | 2.4.1 | 调度中心 Docker 部署，执行器内嵌在业务服务 |
| MySQL | 8.0 | WSL 已有容器，端口 3306 |
| Redis | 7.x（可选但建议） | 网关限流 `RequestRateLimiter` 依赖它 |
| MyBatis-Plus | 3.5.x | 减少 CRUD 样板代码 |
| Lombok / Validation | 最新稳定 | 开发效率 |

> 版本策略：所有版本在父 POM 中固定，不追新。SCA 2023.0.1.0 官方对应 Spring Boot 3.2.4 + Spring Cloud 2023.0.1，随意升级容易踩兼容坑。
>
> XXL-JOB 说明：`xxl-job-core` 的执行器走内嵌 Netty，不与 Servlet 容器强绑定，Boot 3 下通常可用；若出现依赖冲突，改用 3.x 版本，或把调度中心继续独立部署（本来也是独立部署）。

---

## 三、业务场景与微服务拆分

### 3.1 业务场景

简化电商下单：

1. 用户登录拿到 JWT。
2. 用户下单：订单服务校验商品、锁定库存、创建订单。
3. 订单创建成功后，通过 Kafka 异步通知库存服务扣减库存。
4. 订单 15 分钟未支付，由 XXL-JOB 定时任务扫描并关单，同时回滚库存。
5. 模拟支付回调，订单状态变更为已支付。

### 3.2 模块与端口

| 模块 | 端口 | 职责 | 数据库 |
| --- | --- | --- | --- |
| `mall-gateway` | 8080 | 统一入口、路由、JWT 鉴权、限流、跨域 | 无 |
| `mall-user` | 8081 | 注册、登录、签发 JWT、用户信息 | `mall_user` |
| `mall-product` | 8082 | 商品列表、详情、上下架 | `mall_product` |
| `mall-stock` | 8083 | 库存查询、预占、扣减、回滚 | `mall_stock` |
| `mall-order` | 8084 | 下单、支付、发货状态、订单查询 | `mall_order` |
| `mall-wms` | 8085 | 模拟仓库出库，确认发货并触发库存扣减 | `mall_wms` |
| `mall-common` | - | 统一响应 `Result`、异常、常量、TraceId 工具 | 无 |
| `mall-api` | - | 各服务的 Feign 接口 + 请求/响应 DTO | 无 |
| `mall-parent` | - | 父 POM，统一依赖与版本 | 无 |

依赖方向：`业务服务 → mall-api / mall-common`；`mall-api → mall-common`。`mall-api` 只放接口和 DTO，不放实现。

### 3.3 架构示意

```
                      ┌──────────────────────────────┐
  浏览器 / curl  ───▶ │  mall-gateway :8080          │
                      │  路由 / JWT / 限流 / CORS     │
                      └──────────────┬───────────────┘
                                     │ Nacos 服务发现
        ┌────────────────────────────┼────────────────────────────┐
        ▼                            ▼                            ▼
 ┌─────────────┐              ┌─────────────┐              ┌─────────────┐
 │ mall-user   │              │mall-product │              │ mall-order  │
 │ :8081       │◀── OpenFeign │ :8082       │◀── OpenFeign │ :8084       │
 └─────────────┘              └─────────────┘              └──────┬──────┘
                                                                  │ 发消息
                                                                  ▼
                                                          ┌─────────────┐
                                                          │ Kafka       │
                                                          │ :9092/9094  │
                                                          └──────┬──────┘
                                                                 │ 消费
                                                                 ▼
                                                          ┌─────────────┐
                                                          │ mall-stock  │
                                                          │ :8083       │
                                                          └─────────────┘

  XXL-JOB 调度中心 :8088 ──调度──▶ mall-order 内嵌执行器 :9999
  Nacos :8848/9848/9849     MySQL :3306     Redis :6379
```

### 3.4 表结构（最小可用）

| 库 | 表 | 关键字段 |
| --- | --- | --- |
| `mall_user` | `t_user` | id, username, password, phone, status, create_time |
| `mall_product` | `t_product` | id, name, price, stock_flag, status, create_time |
| `mall_stock` | `t_stock` | id, product_id, available, locked, version |
| `mall_order` | `t_order` | id, order_no, user_id, total_amount, status, create_time, pay_time |
| `mall_wms` | `t_outbound` | id, order_no, status, create_time |
| `mall_order` | `t_order_item` | id, order_id, product_id, price, quantity |

- 每个服务独立库，符合「数据库按服务拆分」的面试讲法。
- `t_stock` 用 `available / locked` 两字段表达预占与扣减，`version` 字段预留乐观锁。
- 订单状态：`CREATED / PAID / CANCELLED / CLOSED`。

---

## 四、核心链路设计

### 4.1 下单链路（同步 OpenFeign）

```
POST /api/order/create
  1. Gateway 校验 JWT，解析 userId 放入 Header
  2. order-service 本地校验参数、生成 orderNo
  3. Feign 调 product-service：查商品与价格（校验上架状态）
  4. Feign 调 stock-service：预占库存（available - n, locked + n）
  5. order-service 本地事务写入 t_order + t_order_item
  6. 发送 Kafka 消息 order-created（key = orderNo）
  7. 返回订单号
```

要练的点：Feign 接口定义在 `mall-api`、超时与重试配置、降级兜底、调用链 TraceId 透传。

### 4.2 出库确认扣减（异步 Kafka）

```
1. 订单支付成功后状态为 PAID
2. WMS 调用 order 的 ship 接口：PAID → SHIPPED
3. WMS 发送 Kafka 消息 order-shipped（key = orderNo）
4. stock-service 消费 order-shipped：
   - t_stock_log 按 (CONFIRM, orderNo, productId) 幂等
   - locked - quantity，真正扣减
   - 手动 ack
```

预占库存会从下单一直保留到仓库发货，符合「长预占」模型。

要练的点：消费组、分区与顺序、幂等、手动 ack、消息重复与丢失。

### 4.3 超时关单（XXL-JOB）

```
每 2 分钟执行「关闭超时订单」任务
  1. 扫描 status=CREATED 且 create_time < now-15min 的订单
  2. 更新为 CLOSED（以状态条件保证幂等）
  3. 发送 order-cancelled 消息，stock-service 释放预占：
     locked - n，available + n
```

因为确认扣减发生在发货节点，超时取消时 `locked` 仍在，直接 `unlock` 即可。
若订单已经发货，则需要走「退回库存」而不是释放预占，这是后续扩展点。

### 4.4 网关鉴权与限流

```
/api/user/login、/api/product/**   → 白名单，直接放行
其他 /api/**                       → 全局过滤器校验 JWT
                                   → 解析 userId 写入 X-User-Id
                                   → RedisRateLimiter 限流
```

要练的点：路由断言（Path、Method、Header）、全局过滤器 vs 局部过滤器、限流算法与 Redis 令牌桶、跨域配置、网关不能引 Web MVC。

---

## 五、分阶段实施计划

### P0 环境准备（先解决阻塞项）

1. ~~修复 `JAVA_HOME`~~：已完成，系统变量为 `D:\Tools\Java\21`，`mvn 3.9.15` + `Java 21.0.10` 验证通过。
2. 在 WSL 启动 Nacos（MySQL 外置存储），当前环境无 compose 插件，直接使用 `docker run`。→ 操作指南：[P0-02 Nacos 启动](docs/P0-02-nacos-docker-mysql.md)
3. 初始化数据库：`mall_user`、`mall_product`、`mall_stock`、`mall_order`、`nacos_config`、`xxl_job`。
4. 开放端口：8848/9848/9849、9092/9094、6379、8088、9999。
5. Kafka 部署（KRaft 单节点 + 双 listener）→ 指南：[P0-03 Kafka](docs/P0-03-kafka-docker.md)

**验收**：`mvn -v` 正常；Nacos 控制台能打开；Windows 侧可用 Kafka 客户端连接 `localhost:9094`；MySQL 四个库建好。

### P1 工程骨架 + 服务注册

1. 建父 POM 与 8 个模块，统一依赖版本。→ 操作指南：[P1-01 父 POM](docs/P1-01-parent-pom-guide.md)、[P1-02 公共模块](docs/P1-02-common-api-modules.md)
2. 四个业务服务接入 Nacos Discovery，暴露 `/actuator/health`。→ 指南：[P1-04 mall-user](docs/P1-04-user-service.md)、[P1-05 mall-product](docs/P1-05-product-service.md)、[P1-06 mall-stock](docs/P1-06-stock-service.md)、[P1-07 mall-order](docs/P1-07-order-service.md)、[P1-08 mall-wms](docs/P1-08-wms-service.md)
3. Gateway 接入 Nacos，配置按服务名路由。→ 操作指南：[P1-03 Gateway](docs/P1-03-gateway.md)

**验收**：Nacos 服务列表出现 5 个服务；`http://localhost:8080/api/product/list` 能路由到商品服务；停掉某服务后 Nacos 实例消失。

### P2 同步调用链路（OpenFeign）

1. `mall-api` 定义 Feign 接口与 DTO。
2. 下单接口串起 order → product → stock。
3. 配置 Feign 超时、重试、日志级别、`fallbackFactory`。
4. 启动两个 user-service 实例（8090/8091）验证负载均衡。

**验收**：下单成功落库；手动停掉 product-service 后调用返回降级结果而不是 500；日志能看到 Feign 选择了不同实例。

### P3 异步链路（Kafka）

1. 创建 topic：`order-created`、`order-shipped`、`order-cancelled`（分区数 3）。
2. order-service 生产 `order-created`，WMS 生产 `order-shipped`（key=orderNo）。
3. stock-service 消费 `order-shipped` 并幂等扣减，手动 ack。→ 操作指南：[P3-01 Kafka 消费端](docs/P3-01-kafka-consumer.md)、[P3-02 出库扣减改造](docs/P3-02-ship-confirm.md)
4. 故意重复投递一条消息，验证不会重复扣减。

**验收**：发货后库存异步变化；重复消息只生效一次；消费者重启后消息不丢。

### P4 定时调度（XXL-JOB）

1. 部署调度中心，初始化 `xxl_job` 库。
2. order-service 内嵌执行器，注册到调度中心。
3. 编写「关闭超时订单」任务，cron 每 2 分钟，分片广播。
4. 配置失败重试与阻塞策略，观察调度日志。

**验收**：手工造一条 20 分钟前的 CREATED 订单，任务执行后订单变 CLOSED、库存回滚；调度中心能看到执行日志与耗时。

### P5 网关增强 + 配置中心 + 可观测

1. Gateway 全局 JWT 过滤器 + Redis 限流 + 跨域。
2. Nacos Config：抽取 `mall-common.yaml` 共享配置，演示修改后动态刷新（`@RefreshScope`）。
3. 统一 Result、全局异常处理、TraceId（MDC + Feign 拦截器 + Kafka header）。
4. 关键接口用 `curl` 或 JMeter 做一次压测，记录 QPS 与瓶颈。

**验收**：无 Token 请求被 401 拦截；超阈值返回 429；Nacos 改配置后服务日志即时生效；一次下单能按 traceId 串起全部日志。

### P6 面试复盘（可选扩展）

- 加 Sentinel：熔断降级、热点参数限流（替代手写 fallback）。
- 加 Seata：对比 AT 模式与「本地消息表 + Kafka」最终一致方案。
- 加链路追踪：Micrometer Tracing + Zipkin。
- 整理一份「项目难点与取舍」文档，作为面试讲述稿。

---

## 六、WSL 部署设计要点

### 6.1 Compose 服务与端口

| 服务 | 镜像（示例） | 端口映射 | 备注 |
| --- | --- | --- | --- |
| mysql | `mysql:8.0` | 3306 | 已存在，复用 |
| nacos | `nacos/nacos-server:v2.3.2` | 8848, 9848, 9849 | `MODE=standalone`，2.x 必须映射 gRPC 端口 |
| kafka | `bitnami/kafka:3.7` | 9094 | KRaft 单节点 |
| redis | `redis:7-alpine` | 6379 | 网关限流用 |
| xxl-job-admin | `xuxueli/xxl-job-admin:2.4.1` | 8088 | 连 WSL 内 MySQL |

### 6.2 Kafka 双 Listener（关键坑）

Windows 上的 Spring Boot 应用与 WSL 容器访问 Kafka 的地址不同，必须配置两个 listener：

```yaml
KAFKA_CFG_PROCESS_ROLES: broker,controller
KAFKA_CFG_LISTENERS: INTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,EXTERNAL://0.0.0.0:9094
KAFKA_CFG_ADVERTISED_LISTENERS: INTERNAL://kafka:9092,EXTERNAL://localhost:9094
KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT
KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
```

- Windows 侧应用连 `localhost:9094`。
- WSL 内其他容器连 `kafka:9092`。
- 只映射 9094 到宿主机即可。

### 6.3 Nacos 注意点

- 客户端在 Windows，服务端在 WSL：`localhost:8848` 可用，但 gRPC 的 9848/9849 必须一起映射，否则注册失败且报错不明显。
- 客户端与服务端版本不要跨大版本，2.3.x 客户端配 2.3.x 服务端。
- Boot 3 下推荐 `spring.config.import=nacos:xxx.yaml`，不要再用 `bootstrap.yml` 那套。

### 6.4 XXL-JOB 回调网络（最容易卡住的点）

调度中心在容器里，执行器在 Windows 上（业务服务内嵌 9999 端口），调度中心需要主动回调执行器：

- 执行器注册地址必须用「容器能访问到的 Windows 主机 IP」，不能用 `127.0.0.1`。
- 若 WSL 使用默认 NAT 网络，容器访问 Windows 宿主机通常需要额外处理（如 `--add-host=host.docker.internal:host-gateway`，或直接填写 Windows 局域网 IP）。
- 兜底方案：调度中心改为本地 `java -jar` 启动，直接连 WSL 的 MySQL，彻底绕开跨网络回调。
- 先做一次最小验证：调度中心「执行一次」能收到执行器回调，再写业务任务。

---

## 七、踩坑清单

| 风险 | 现象 | 处理 |
| --- | --- | --- |
| Gateway 引入 Web MVC | 启动报 `Spring MVC found on classpath` | Gateway 模块不要依赖 `spring-boot-starter-web` |
| `JAVA_HOME` 指向 bin | `mvn` 报 `JAVA_HOME is not defined correctly` | 已修复，指向 `D:\Tools\Java\21` |
| Nacos 缺少 9848/9849 | 服务注册失败或一直重连 | compose 中同时映射两个端口 |
| Kafka advertised listeners 配错 | Windows 能连上但发消息超时 | 用双 listener + `localhost:9094` |
| Feign 默认超时过短 | 偶发 `Read timed out` | 显式配置 connect/read 超时 |
| Feign 自动重试导致重复下单 | 同一订单生成两条记录 | 关闭重试或加业务幂等键 |
| Kafka 消费重复 | 库存被扣两次 | 业务幂等表 / Redis SETNX，手动 ack |
| XXL-JOB 分片任务不幂等 | 多实例重复关单 | 状态机 + `update ... where status=CREATED` |
| Nacos 配置不刷新 | 改了配置不生效 | `@RefreshScope` + 确认 dataId/group 匹配 |

---

## 八、面试题映射

| 组件 | 必须能讲清楚的问题 |
| --- | --- |
| Nacos | 注册中心选型、AP/CP 模式、临时实例与持久实例、心跳与健康检查、配置动态刷新原理、与 Eureka 的区别 |
| Gateway | 为什么用响应式、路由断言与过滤器执行顺序、全局 vs 局部过滤器、限流算法（令牌桶）、与 Zuul 1 的区别 |
| OpenFeign | 动态代理调用流程、与 LoadBalancer 配合、超时与重试、熔断降级、日志级别、如何传递 Header |
| Kafka | 为什么快（顺序写、页缓存、零拷贝）、消息不丢（acks/重试/手动提交）、不重复（幂等 + 业务幂等）、顺序性、消费组与重平衡 |
| XXL-JOB | 调度中心与执行器通信、cron 触发、分片广播、故障转移、阻塞策略、任务幂等与失败重试 |
| 综合 | 分布式事务方案取舍（Seata AT vs 本地消息表 vs 事务消息）、链路追踪、服务雪崩与熔断 |

---

## 九、交付物

1. 可一键启动的 `docker-compose.yml`（WSL）。
2. 7 个 Maven 模块的完整工程，含 SQL 初始化脚本。
3. 一份接口测试集合（curl 脚本或 HTTP 文件），覆盖下单、支付、超时关单、限流、鉴权。
4. 一份「项目难点与取舍」说明，作为面试自述稿。

---

## 十、下一步

规划确认后，按 P0 → P5 顺序落地，每个阶段完成即验收，不并行堆代码。可先调整的点：

- 业务场景是否换成更贴近目标岗位的（如内容社区、外卖、票务）。
- 是否需要把 Redis / Sentinel / Seata 提前纳入核心阶段。
- 是否需要在项目里补一个简单前端（Vue3 + Element Plus）用于简历展示。
