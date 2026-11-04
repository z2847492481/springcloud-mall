package com.mall.order.controller;

import com.mall.api.order.dto.OrderCreateRequest;
import com.mall.api.order.dto.OrderDTO;
import com.mall.common.result.Result;
import com.mall.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<OrderDTO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.ok(orderService.create(request));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDTO> getByOrderNo(@PathVariable("orderNo") String orderNo) {
        return Result.ok(orderService.getByOrderNo(orderNo));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@PathVariable("orderNo") String orderNo) {
        orderService.pay(orderNo);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable("orderNo") String orderNo) {
        orderService.ship(orderNo);
        return Result.ok();
    }
}
