package com.mall.api.product;

import com.mall.api.product.dto.ProductDTO;
import com.mall.api.product.dto.ProductQuery;
import com.mall.api.product.fallback.ProductApiFallbackFactory;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "mall-product", path = "/product",
        fallbackFactory = ProductApiFallbackFactory.class)
public interface ProductApi {

    @GetMapping("/{id}")
    Result<ProductDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/list")
    Result<List<ProductDTO>> list(@SpringQueryMap ProductQuery query);
}
