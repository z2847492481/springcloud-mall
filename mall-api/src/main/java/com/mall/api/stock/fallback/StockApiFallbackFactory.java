package com.mall.api.stock.fallback;

import com.mall.api.stock.StockApi;
import com.mall.api.stock.dto.StockLockRequest;
import com.mall.common.enums.ErrorCode;
import com.mall.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockApiFallbackFactory implements FallbackFactory<StockApi> {

    @Override
    public StockApi create(Throwable cause) {
        log.error("[fallback] mall-stock 调用失败，返回降级结果", cause);
        return new StockApi() {
            @Override
            public Result<Void> lock(StockLockRequest request) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<Void> unlock(StockLockRequest request) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
