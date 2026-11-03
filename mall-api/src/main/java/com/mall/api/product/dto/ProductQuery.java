package com.mall.api.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQuery {

    private String keyword;
    private long pageNum = 1;
    private long pageSize = 10;
}
