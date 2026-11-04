package com.mall.order.service;

import com.mall.api.order.dto.OrderCreateRequest;
import com.mall.api.order.dto.OrderDTO;

public interface OrderService {

    OrderDTO create(OrderCreateRequest request);

    OrderDTO getByOrderNo(String orderNo);

    void pay(String orderNo);
}
