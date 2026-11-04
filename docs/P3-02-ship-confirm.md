# P3-02 改为「发货时确认扣减」

> 背景：原实现是消费 `order-created` 就扣库存，属于「下单即扣」。现调整为长预占模型：
> 下单只预占，仓库发货（WMS 确认出库）时才真正扣减。

---

## 一、调整后的链路

```
下单            order-service：Feign lock → available-2, locked+2
支付            order-service：CREATED → PAID
仓库确认出库     mall-wms：OrderApi.ship → PAID → SHIPPED
                mall-wms：发送 order-shipped（key=orderNo）
库存消费        stock-service：locked-2 → 真正扣减
取消/超时        order-service/XXL-JOB：发送 order-cancelled
                stock-service：locked-2, available+2（unlock）
```

库存状态：

| 阶段 | available | locked | 说明 |
| --- | --- | --- | --- |
| 初始 | 100 | 0 | - |
| 下单预占 | 98 | 2 | 预占 |
| 发货确认 | 98 | 0 | 真正售出 |
| 发货前取消 | 100 | 0 | 释放预占 |

因为确认扣减发生在发货节点，超时取消时 `locked` 仍然存在，`unlock` 可以正常工作。

---

## 二、已调整的代码

| 位置 | 变化 |
| --- | --- |
| `CommonConstants` | 新增 `TOPIC_ORDER_SHIPPED = "order-shipped"` |
| `mall-api` | 新增 `OrderApi`（查订单、发货）和 `OrderShippedEvent` |
| `mall-order` | 新增 `POST /order/{orderNo}/ship`，`PAID → SHIPPED`，重复调用幂等 |
| `mall-stock` | 消费 topic 由 `order-created` 改为 `order-shipped`；消费者类改为 `OrderShippedConsumer` |
| `t_stock_log` | 增加 `biz_type`，唯一键改为 `(biz_type, order_no, product_id)` |
| `mall-gateway` | 新增 `/api/wms/**` 路由 |
| Kafka | 新建 topic `order-shipped`（3 分区） |

`order-created` 仍然由订单服务生产，预留给通知、统计等下游，不再驱动库存扣减。

---

## 三、幂等表迁移

原表只有 `(order_no, product_id)` 唯一键，无法区分「确认」和「释放」。测试数据可直接重建：

```sql
DROP TABLE IF EXISTS mall_stock.t_stock_log;
CREATE TABLE mall_stock.t_stock_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  biz_type    VARCHAR(20)     NOT NULL COMMENT 'CONFIRM/RELEASE/RETURN',
  order_no    VARCHAR(64)     NOT NULL,
  product_id  BIGINT UNSIGNED NOT NULL,
  quantity    INT             NOT NULL,
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_order_product (biz_type, order_no, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

业务码规划：

| biz_type | 场景 |
| --- | --- |
| `CONFIRM` | 发货确认扣减 |
| `RELEASE` | 发货前取消，释放预占 |
| `RETURN` | 已发货后退货入库 |

---

## 四、验证

### 4.1 库存消费端（不依赖 WMS）

1. 启动 `mall-stock`；
2. 调 `POST /stock/lock` 预占 2 件，库存变为 `98/2`；
3. 手工发送一条 `order-shipped` 消息：

```json
{
  "orderNo": "TEST-SHIP-001",
  "userId": 1,
  "totalAmount": 798.00,
  "items": [
    {"productId": 1, "productName": "机械键盘", "price": 399.00, "quantity": 2}
  ]
}
```

4. 查询库存应为 `98/0`，`t_stock_log` 有一条 `CONFIRM` 记录；
5. 重复发送同一消息，库存与日志条数不变。

### 4.2 完整链路（WMS 模块建好后）

1. 下单 → `CREATED`，库存 `98/2`
2. 支付 → `PAID`
3. 调 `POST /wms/outbound/{orderNo}` → 订单 `SHIPPED`
4. 等待消费 → 库存 `98/0`
5. 重复调用出库接口 → 返回成功，库存不再变化

---

## 五、注意事项

- 订单未支付不能出库，`ship` 只允许 `PAID → SHIPPED`。
- WMS 先更新订单状态、再写出库单、再发消息；如果消息发送失败，需要靠重试或后续 outbox 方案补偿。
- 已发货订单如果再取消，不能调用 `unlock`（`locked` 已为 0），应走 `RETURN` 退回 `available`。
- 同一订单的 `order-shipped` 消息可能重复，库存侧依靠 `CONFIRM` 幂等键兜底。
