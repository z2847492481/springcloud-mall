package com.mall.api.order;

import com.mall.api.order.dto.OrderDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "mall-order", path = "/order")
public interface OrderApi {

    @GetMapping("/{orderNo}")
    Result<OrderDTO> getByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/{orderNo}/ship")
    Result<Void> ship(@PathVariable("orderNo") String orderNo);
}
