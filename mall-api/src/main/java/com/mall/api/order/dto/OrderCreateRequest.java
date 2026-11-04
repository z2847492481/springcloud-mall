package com.mall.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @Valid
    @NotEmpty(message = "订单明细不能为空")
    private List<OrderItemRequest> items;
}
