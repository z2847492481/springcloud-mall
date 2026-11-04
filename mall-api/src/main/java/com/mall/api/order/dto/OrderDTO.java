package com.mall.api.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private List<OrderItemDTO> items;
}
