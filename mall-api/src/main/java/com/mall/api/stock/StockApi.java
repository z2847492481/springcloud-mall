package com.mall.api.stock;

import com.mall.api.stock.dto.StockLockRequest;
import com.mall.api.stock.fallback.StockApiFallbackFactory;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-stock", path = "/stock",
        fallbackFactory = StockApiFallbackFactory.class)
public interface StockApi {

    @PostMapping("/lock")
    Result<Void> lock(@RequestBody StockLockRequest request);

    @PostMapping("/unlock")
    Result<Void> unlock(@RequestBody StockLockRequest request);
}
