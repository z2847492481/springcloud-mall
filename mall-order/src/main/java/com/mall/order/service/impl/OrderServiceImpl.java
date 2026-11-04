package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.api.order.dto.OrderCreateRequest;
import com.mall.api.order.dto.OrderCreatedEvent;
import com.mall.api.order.dto.OrderDTO;
import com.mall.api.order.dto.OrderItemDTO;
import com.mall.api.order.dto.OrderItemRequest;
import com.mall.api.product.ProductApi;
import com.mall.api.product.dto.ProductDTO;
import com.mall.api.stock.StockApi;
import com.mall.api.stock.dto.StockLockRequest;
import com.mall.common.constant.CommonConstants;
import com.mall.common.enums.ErrorCode;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.order.entity.Order;
import com.mall.order.entity.OrderItem;
import com.mall.order.mapper.OrderItemMapper;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.mq.OrderEventProducer;
import com.mall.order.service.OrderPersistenceService;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_SHIPPED = "SHIPPED";

    private final ProductApi productApi;
    private final StockApi stockApi;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderPersistenceService orderPersistenceService;
    private final OrderEventProducer orderEventProducer;

    @Override
    public OrderDTO create(OrderCreateRequest request) {
        String orderNo = generateOrderNo(request.getUserId());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductDTO product = queryProduct(itemRequest.getProductId());
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setPrice(product.getPrice());
            item.setQuantity(itemRequest.getQuantity());
            items.add(item);
            totalAmount = totalAmount.add(product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }

        int lockedCount = 0;
        try {
            for (OrderItem item : items) {
                lockStock(orderNo, item);
                lockedCount++;
            }
        } catch (RuntimeException e) {
            compensate(orderNo, items, lockedCount);
            throw e;
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setTotalAmount(totalAmount);
        order.setStatus(STATUS_CREATED);
        order.setCreateTime(LocalDateTime.now());

        try {
            orderPersistenceService.save(order, items);
        } catch (RuntimeException e) {
            compensate(orderNo, items, items.size());
            throw e;
        }

        orderEventProducer.sendOrderCreated(new OrderCreatedEvent(
                orderNo, request.getUserId(), totalAmount, toItemDTOs(items)));
        return toDTO(order, items);
    }

    @Override
    public OrderDTO getByOrderNo(String orderNo) {
        Order order = queryOrder(orderNo);
        return toDTO(order, queryItems(order.getId()));
    }

    @Override
    public void pay(String orderNo) {
        Order order = queryOrder(orderNo);
        if (!STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, STATUS_CREATED)
                .set(Order::getStatus, STATUS_PAID)
                .set(Order::getPayTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }

    @Override
    public void ship(String orderNo) {
        Order order = queryOrder(orderNo);
        if (STATUS_SHIPPED.equals(order.getStatus())) {
            return;
        }
        if (!STATUS_PAID.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, STATUS_SHIPPED));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }

    private ProductDTO queryProduct(Long productId) {
        Result<ProductDTO> result = productApi.getById(productId);
        if (result == null || result.getCode() != ErrorCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductDTO product = result.getData();
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        return product;
    }

    private void lockStock(String orderNo, OrderItem item) {
        Result<Void> result = stockApi.lock(
                new StockLockRequest(orderNo, item.getProductId(), item.getQuantity()));
        if (result == null || result.getCode() != ErrorCode.SUCCESS.getCode()) {
            int code = result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.getCode();
            String message = result == null ? "库存服务调用失败" : result.getMessage();
            throw new BusinessException(code, message);
        }
    }

    private void compensate(String orderNo, List<OrderItem> items, int lockedCount) {
        for (int i = 0; i < lockedCount; i++) {
            OrderItem item = items.get(i);
            try {
                stockApi.unlock(new StockLockRequest(
                        orderNo, item.getProductId(), item.getQuantity()));
            } catch (Exception e) {
                log.error("库存补偿失败, orderNo={}, productId={}", orderNo, item.getProductId(), e);
            }
        }
    }

    private Order queryOrder(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private List<OrderItem> queryItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
    }

    private OrderDTO toDTO(Order order, List<OrderItem> items) {
        return new OrderDTO(order.getOrderNo(), order.getUserId(), order.getTotalAmount(),
                order.getStatus(), order.getCreateTime(), order.getPayTime(), toItemDTOs(items));
    }

    private List<OrderItemDTO> toItemDTOs(List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemDTO(item.getProductId(), item.getProductName(),
                        item.getPrice(), item.getQuantity()))
                .toList();
    }

    private String generateOrderNo(Long userId) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + userId
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
