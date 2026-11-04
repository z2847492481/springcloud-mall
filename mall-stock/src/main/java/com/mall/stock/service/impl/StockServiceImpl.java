package com.mall.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.api.stock.dto.StockLockRequest;
import com.mall.common.enums.ErrorCode;
import com.mall.common.exception.BusinessException;
import com.mall.stock.entity.Stock;
import com.mall.stock.mapper.StockMapper;
import com.mall.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final StockMapper stockMapper;

    @Value("${server.port}")
    private String serverPort;

    @Override
    public void lock(StockLockRequest request) {
        log.info("预占库存, port={}, orderNo={}, productId={}, quantity={}",
                serverPort, request.getOrderNo(), request.getProductId(), request.getQuantity());
        int rows = stockMapper.lockStock(request.getProductId(), request.getQuantity());
        if (rows == 0) {
            checkStockExists(request.getProductId());
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
    }

    @Override
    public void unlock(StockLockRequest request) {
        log.info("释放预占库存, port={}, orderNo={}, productId={}, quantity={}",
                serverPort, request.getOrderNo(), request.getProductId(), request.getQuantity());
        int rows = stockMapper.unlockStock(request.getProductId(), request.getQuantity());
        if (rows == 0) {
            checkStockExists(request.getProductId());
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "可释放的预占库存不足");
        }
    }

    private void checkStockExists(Long productId) {
        long count = stockMapper.selectCount(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getProductId, productId));
        if (count == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
