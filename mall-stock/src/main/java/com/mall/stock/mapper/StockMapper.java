package com.mall.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.stock.entity.Stock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StockMapper extends BaseMapper<Stock> {

    @Update("""
            UPDATE t_stock
            SET available = available - #{quantity},
                locked = locked + #{quantity}
            WHERE product_id = #{productId}
              AND available >= #{quantity}
            """)
    int lockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE t_stock
            SET locked = locked - #{quantity},
                available = available + #{quantity}
            WHERE product_id = #{productId}
              AND locked >= #{quantity}
            """)
    int unlockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE t_stock
            SET locked = locked - #{quantity}
            WHERE product_id = #{productId}
              AND locked >= #{quantity}
            """)
    int confirmStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
