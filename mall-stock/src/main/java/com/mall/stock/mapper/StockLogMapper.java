package com.mall.stock.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface StockLogMapper {

    @Insert("""
            INSERT IGNORE INTO t_stock_log (biz_type, order_no, product_id, quantity)
            VALUES (#{bizType}, #{orderNo}, #{productId}, #{quantity})
            """)
    int insertIgnore(@Param("bizType") String bizType,
                     @Param("orderNo") String orderNo,
                     @Param("productId") Long productId,
                     @Param("quantity") Integer quantity);
}
