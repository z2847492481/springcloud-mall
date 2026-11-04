package com.mall.order.service;

import com.mall.order.entity.Order;
import com.mall.order.entity.OrderItem;
import com.mall.order.mapper.OrderItemMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public void save(Order order, List<OrderItem> items) {
        orderMapper.insert(order);
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
    }
}
