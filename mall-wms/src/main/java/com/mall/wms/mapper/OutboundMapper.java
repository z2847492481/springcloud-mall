package com.mall.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.wms.entity.Outbound;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface OutboundMapper extends BaseMapper<Outbound> {

    @Insert("""
            INSERT IGNORE INTO t_outbound (order_no, status)
            VALUES (#{orderNo}, 'SHIPPED')
            """)
    int insertIgnore(@Param("orderNo") String orderNo);
}
