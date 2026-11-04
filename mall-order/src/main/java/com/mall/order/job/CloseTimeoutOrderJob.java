package com.mall.order.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.api.order.dto.OrderCancelledEvent;
import com.mall.api.order.dto.OrderItemDTO;
import com.mall.order.entity.Order;
import com.mall.order.entity.OrderItem;
import com.mall.order.mapper.OrderItemMapper;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.mq.OrderEventProducer;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloseTimeoutOrderJob {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final int TIMEOUT_MINUTES = 15;
    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderEventProducer orderEventProducer;

    @XxlJob("closeTimeoutOrder")
    public void execute() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, STATUS_CREATED)
                .le(Order::getCreateTime, deadline)
                .orderByAsc(Order::getId)
                .last("LIMIT " + BATCH_SIZE));

        int closedCount = 0;
        for (Order order : orders) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, STATUS_CREATED)
                    .set(Order::getStatus, STATUS_CLOSED));
            if (rows == 0) {
                continue;
            }
            closedCount++;

            List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId()));
            orderEventProducer.sendOrderCancelled(new OrderCancelledEvent(
                    order.getOrderNo(), order.getUserId(), order.getTotalAmount(),
                    items.stream()
                            .map(item -> new OrderItemDTO(item.getProductId(), item.getProductName(),
                                    item.getPrice(), item.getQuantity()))
                            .toList()));
        }

        XxlJobHelper.log("关闭超时订单 {} 笔，扫描 {} 笔", closedCount, orders.size());
        log.info("关闭超时订单完成, closed={}, scanned={}", closedCount, orders.size());
    }
}
