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
public class StockConfirmService {

    private static final String BIZ_CONFIRM = "CONFIRM";

    private final StockLogMapper stockLogMapper;
    private final StockMapper stockMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(String orderNo, Long productId, Integer quantity) {
        int inserted = stockLogMapper.insertIgnore(BIZ_CONFIRM, orderNo, productId, quantity);
        if (inserted == 0) {
            log.info("库存确认重复消息，跳过, orderNo={}, productId={}", orderNo, productId);
            return false;
        }

        int rows = stockMapper.confirmStock(productId, quantity);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "可确认扣减的预占库存不足");
        }
        log.info("库存确认扣减成功, orderNo={}, productId={}, quantity={}", orderNo, productId, quantity);
        return true;
    }
}
