# P4-01 接入 XXL-JOB：超时关单

> 前置：先按 [P0-04 XXL-JOB 部署](P0-04-xxl-job-docker.md) 启动调度中心并导入 `xxl_job` 库。
> 目标：订单创建后 15 分钟未支付，由 XXL-JOB 自动关单，并通过 Kafka 通知库存释放预占。

---

## 一、链路

```
XXL-JOB（每 2 分钟）
  └─ mall-order 执行器 closeTimeoutOrder
        ├─ 扫描 status=CREATED 且 create_time < now-15min
        ├─ 原子更新 CREATED → CLOSED
        └─ 发送 order-cancelled（key=orderNo）
              │
              ▼
        mall-stock 消费
              ├─ t_stock_log 写入 RELEASE（幂等）
              └─ locked - n，available + n
```

因为方案 B 的确认扣减发生在发货节点，未支付订单的库存仍在 `locked`，所以超时取消可以直接 `unlock`。

---

## 二、`mall-order` 增加 XXL-JOB 执行器

### 2.1 POM

```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
</dependency>
```

版本由父 POM 统一管理（2.4.1）。

### 2.2 `application.yml`

```yaml
xxl:
  job:
    admin:
      addresses: http://localhost:8088/xxl-job-admin
    accessToken: default_token
    executor:
      appname: mall-order-executor
      address: http://127.0.0.1:9999/
      ip:
      port: 9999
      logpath: ./logs/xxl-job
      logretentiondays: 30
```

关键点：

- `address` 必须带协议，例如 `http://127.0.0.1:9999/`；auto 模式可能选中 VMware 网卡（`192.168.40.1`），导致容器回调失败。
- 当前环境用 host 网络的调度中心 + `127.0.0.1` 执行器地址，因为 WSL 访问 Windows 局域网 IP 会被防火墙拦截；详见 P0-04。
- `accessToken` 要与调度中心一致；2.4.1 镜像默认 `default_token`，如果改过管理端配置，这里同步改。
- `appname` 是执行器的注册名，调度中心「执行器管理」里要能自动出现。
- `logpath` 是执行器本地日志目录，属于开发产物，不提交仓库。

### 2.3 执行器配置类

```java
@Configuration
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(...) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
```

所有值通过 `@Value("${xxl.job....}")` 注入。

### 2.4 任务处理器

```java
@XxlJob("closeTimeoutOrder")
public void closeTimeoutOrder() {
    LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);

    List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getStatus, "CREATED")
            .le(Order::getCreateTime, deadline)
            .orderByAsc(Order::getId)
            .last("LIMIT 100"));

    for (Order order : orders) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "CREATED")
                .set(Order::getStatus, "CLOSED"));
        if (rows == 0) {
            continue;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        orderEventProducer.sendCancelled(new OrderCancelledEvent(
                order.getOrderNo(), order.getUserId(), order.getTotalAmount(), toItemDTOs(items)));
    }
    XxlJobHelper.log("关闭超时订单 {} 笔", orders.size());
}
```

要点：

- 先查后改，但更新时带 `status=CREATED` 条件，重复调度不会重复发消息。
- 每批限制 100 笔，避免一次调度处理过多订单、超时。
- 只处理 `CREATED`，`PAID / SHIPPED` 不会被误关。
- 发送消息失败只影响该订单，下次任务不会重发，需要日志告警或后续补偿（本项目先记录日志）。

### 2.5 调度中心配置

1. 「执行器管理」确认 `mall-order-executor` 自动注册成功。
2. 「任务管理 → 新增」：
   - 执行器：`mall-order-executor`
   - JobHandler：`closeTimeoutOrder`
   - Cron：`0 */2 * * * ?`
   - 路由策略：第一个
   - 阻塞处理策略：单机串行
   - 失败重试次数：1
   - 任务超时：120 秒
3. 保存后点「执行一次」做首次验证。

---

## 三、库存侧消费 `order-cancelled`

新增 `OrderCancelledEvent`（字段与 `OrderShippedEvent` 一致）和消费者：

```java
@KafkaListener(topics = CommonConstants.TOPIC_ORDER_CANCELLED)
public void onOrderCancelled(OrderCancelledEvent event, Acknowledgment ack) {
    for (OrderItemDTO item : event.getItems()) {
        stockReleaseService.release(event.getOrderNo(), item.getProductId(), item.getQuantity());
    }
    ack.acknowledge();
}
```

释放逻辑：

```java
@Transactional
public boolean release(String orderNo, Long productId, Integer quantity) {
    int inserted = stockLogMapper.insertIgnore("RELEASE", orderNo, productId, quantity);
    if (inserted == 0) {
        return false;
    }
    int rows = stockMapper.unlockStock(productId, quantity);
    if (rows == 0) {
        throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "可释放的预占库存不足");
    }
    return true;
}
```

幂等键从 `CONFIRM` 换成 `RELEASE`，同一订单不会重复释放。

---

## 四、验证步骤

### 4.1 造一笔超时订单

1. 调用下单接口，但不支付；
2. 用 SQL 把创建时间改到 20 分钟前：

```sql
UPDATE mall_order.t_order
SET create_time = NOW() - INTERVAL 20 MINUTE
WHERE order_no = '你的订单号';
```

3. 在调度中心点「执行一次」`closeTimeoutOrder`。

### 4.2 检查结果

- 订单状态：`CREATED → CLOSED`
- 库存：`locked - n`，`available + n`
- `t_stock_log`：新增 `RELEASE` 记录
- Kafka：`order-cancelled` 有消息，库存消费者日志显示释放成功
- 再点一次「执行一次」：不会重复关单、不会重复释放

---

## 五、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 调度中心看不到执行器 | 回调网络不通 / appname 不一致 | 按 P0-04 验证 9999，检查 yml |
| 执行器注册 IP 是 192.168.40.x | JDK 自动选了 VMware 网卡 | 显式配置 `xxl.job.executor.address` |
| 任务执行报 401 | accessToken 不一致 | 执行器与调度中心统一 |
| 任务没有触发 | cron 写错 / 任务未启动 | 用「执行一次」先验证，再看调度日志 |
| 订单被重复关单 | 更新没带状态条件 | `UPDATE ... WHERE status='CREATED'` |
| 库存被重复释放 | 没有 RELEASE 幂等记录 | `t_stock_log` 的 `biz_type` 唯一键 |
| 已支付订单被误关 | 扫描条件少了 status | 只查 `CREATED` |
| 执行时间差 8 小时 | 时区不一致 | 容器与 JDBC 都设 `Asia/Shanghai` |
