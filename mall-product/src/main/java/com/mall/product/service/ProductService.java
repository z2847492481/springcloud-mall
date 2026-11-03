package com.mall.product.service;

import com.mall.api.product.dto.ProductDTO;

import java.util.List;

public interface ProductService {

    ProductDTO getById(Long id);

    List<ProductDTO> list(String keyword, long pageNum, long pageSize);
}
