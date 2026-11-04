package com.mall.stock.service;

import com.mall.common.enums.ErrorCode;
import com.mall.common.exception.BusinessException;
import com.mall.stock.mapper.StockLogMapper;
import com.mall.stock.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReleaseService {

    private static final String BIZ_RELEASE = "RELEASE";

    private final StockLogMapper stockLogMapper;
    private final StockMapper stockMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean release(String orderNo, Long productId, Integer quantity) {
        int inserted = stockLogMapper.insertIgnore(BIZ_RELEASE, orderNo, productId, quantity);
        if (inserted == 0) {
            log.info("库存释放重复消息，跳过, orderNo={}, productId={}", orderNo, productId);
            return false;
        }

        int rows = stockMapper.unlockStock(productId, quantity);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "可释放的预占库存不足");
        }
        log.info("库存预占释放成功, orderNo={}, productId={}, quantity={}", orderNo, productId, quantity);
        return true;
    }
}
