package com.mall.api.product.fallback;

import com.mall.api.product.ProductApi;
import com.mall.api.product.dto.ProductDTO;
import com.mall.api.product.dto.ProductQuery;
import com.mall.common.enums.ErrorCode;
import com.mall.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProductApiFallbackFactory implements FallbackFactory<ProductApi> {

    @Override
    public ProductApi create(Throwable cause) {
        log.error("[fallback] mall-product 调用失败，返回降级结果", cause);
        return new ProductApi() {
            @Override
            public Result<ProductDTO> getById(Long id) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<ProductDTO>> list(ProductQuery query) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
