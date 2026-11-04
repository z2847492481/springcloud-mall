package com.mall.stock.controller;

import com.mall.api.stock.dto.StockLockRequest;
import com.mall.common.result.Result;
import com.mall.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/lock")
    public Result<Void> lock(@Valid @RequestBody StockLockRequest request) {
        stockService.lock(request);
        return Result.ok();
    }

    @PostMapping("/unlock")
    public Result<Void> unlock(@Valid @RequestBody StockLockRequest request) {
        stockService.unlock(request);
        return Result.ok();
    }
}
