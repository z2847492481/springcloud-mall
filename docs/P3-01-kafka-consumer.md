# P3-01 库存服务消费 order-created

> **已被 [P3-02 出库扣减改造](P3-02-ship-confirm.md) 取代。**
> 当前实现改为消费 `order-shipped`，在仓库发货时确认扣减；本文保留作为 Kafka 消费端配置参考。

> 目标：订单创建成功后，库存服务通过 Kafka 异步把 `locked` 库存真正扣掉，并保证重复消息不重复扣减。

---

## 一、链路

```
order-service
  └─ 本地事务提交后发送 order-created（key = orderNo）
        │
        ▼
Kafka topic: order-created（3 分区）
        │
        ▼
stock-service：mall-stock 消费组
  ├─ t_stock_log 唯一索引做幂等
  ├─ locked - quantity
  └─ 处理完成后手动 ack
```

对应的库存变化：

```
下单前：available=100, locked=0
预占后：available=98,  locked=2
消费后：available=98,  locked=0   ← 真正扣减完成
```

---

## 二、幂等表 `t_stock_log`

```sql
CREATE TABLE IF NOT EXISTS t_stock_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(64)     NOT NULL,
  product_id  BIGINT UNSIGNED NOT NULL,
  quantity    INT             NOT NULL,
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_product (order_no, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存确认幂等日志';
```

`uk_order_product` 是幂等的核心：同一订单的同一商品只允许成功插入一次。

处理逻辑：

1. `INSERT IGNORE` 写日志；
2. 返回 0 表示已处理过，直接跳过；
3. 返回 1 才执行 `locked - quantity`；
4. 日志插入与库存更新在同一个事务里，库存更新失败会回滚日志。

对应代码：

- `StockLogMapper.insertIgnore`
- `StockMapper.confirmStock`
- `StockConfirmService.confirm`

---

## 三、消费端配置

`mall-stock/application.yml`：

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9094
    consumer:
      group-id: mall-stock
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.mall.api.order.dto
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.mall.api.order.dto.OrderCreatedEvent
    listener:
      ack-mode: manual_immediate
```

| 配置 | 作用 |
| --- | --- |
| `group-id` | 消费组名；同一组内多实例会分摊分区 |
| `enable-auto-commit: false` | 关闭自动提交，改成手动 ack |
| `ack-mode: manual_immediate` | 调用 `ack.acknowledge()` 后立即提交 offset |
| `JsonDeserializer` | 把消息体反序列化为 `OrderCreatedEvent` |
| `trusted.packages` | 反序列化白名单，防止任意类型反序列化 |
| `use.type.headers: false` + `default.type` | 不依赖消息头，手工投递纯 JSON 也能消费 |

消费方法：

```java
@KafkaListener(topics = CommonConstants.TOPIC_ORDER_CREATED)
public void onOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
    for (OrderItemDTO item : event.getItems()) {
        stockConfirmService.confirm(event.getOrderNo(), item.getProductId(), item.getQuantity());
    }
    ack.acknowledge();
}
```

所有商品处理完才 ack；中途抛异常则不 ack，Kafka 会重新投递。

---

## 四、验证步骤

### 4.1 重置测试数据

```bash
# 删除并重建 topic，避免旧的纯文本测试消息污染消费者
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic order-created
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --topic order-created \
  --partitions 3 --replication-factor 1

# 重置库存与幂等日志
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
TRUNCATE TABLE mall_stock.t_stock_log;
UPDATE mall_stock.t_stock SET available=100, locked=0 WHERE product_id=1;"'
```

### 4.2 启动服务并下单

依次启动 Nacos（已在运行）、`mall-product`、`mall-stock`、`mall-order`，然后调用：

```http
POST http://localhost:8084/order/create
Content-Type: application/json

{"userId":1,"items":[{"productId":1,"quantity":2}]}
```

### 4.3 检查结果

```bash
docker exec mysql sh -c 'mysql -umall -pmall123456 -D mall_stock -e "
SELECT available, locked FROM t_stock WHERE product_id=1;
SELECT order_no, product_id, quantity FROM t_stock_log;"'
```

预期：

- `available=98`、`locked=0`
- `t_stock_log` 有一条新记录

### 4.4 重复投递验证幂等

用 Kafka UI 或命令行把同一订单的消息再发一次，然后重新查询：

- `available` 仍是 `98`
- `locked` 仍是 `0`
- `t_stock_log` 仍只有一条

说明重复消息被 `INSERT IGNORE` 拦住了。

---

## 五、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 消费报反序列化失败 | 消息体不是 `OrderCreatedEvent`，或类不在白名单 | 清空 topic；确认 `trusted.packages` 与类路径 |
| 一直重复消费同一条消息 | 处理异常且没有 ack | 查看库存/日志异常，修复后重新投递 |
| 库存扣成负数 | 确认扣减没加 `locked >= ?` 条件 | SQL 已带条件，检查是否被改 |
| 重复消息扣两次 | 幂等表缺唯一索引，或没在一个事务里 | 检查 `uk_order_product` |
| 启动时连接 Kafka 失败 | Kafka 未启动或端口写错 | Windows 用 `localhost:9094` |
| 多实例消费重复 | 每个实例用不同 group-id | 同一服务实例必须共用 `mall-stock` |

---

## 六、注意：下单即扣减带来的后续影响

当前设计是「消费 `order-created` 就确认扣减」，也就是下单成功即扣库存。

因此订单超时取消时，不能再调用 `unlock`（`locked` 已经是 0），而应该执行「退回库存」：

```sql
UPDATE t_stock SET available = available + ? WHERE product_id = ?;
```

XXL-JOB 超时关单阶段会补这个接口，并配独立的幂等键（比如 `order_no + cancel`），避免重复退回。
