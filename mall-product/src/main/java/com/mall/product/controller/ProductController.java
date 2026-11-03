package com.mall.product.controller;

import com.mall.api.product.dto.ProductDTO;
import com.mall.common.result.Result;
import com.mall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public Result<ProductDTO> getById(@PathVariable("id") Long id) {
        return Result.ok(productService.getById(id));
    }

    @GetMapping("/list")
    public Result<List<ProductDTO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return Result.ok(productService.list(keyword, pageNum, pageSize));
    }
}
