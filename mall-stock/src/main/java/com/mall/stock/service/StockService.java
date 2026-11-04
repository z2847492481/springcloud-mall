package com.mall.stock.service;

import com.mall.api.stock.dto.StockLockRequest;

public interface StockService {

    void lock(StockLockRequest request);

    void unlock(StockLockRequest request);
}
