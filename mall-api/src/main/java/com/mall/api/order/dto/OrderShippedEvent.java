package com.mall.api.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderShippedEvent {

    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> items;
}
