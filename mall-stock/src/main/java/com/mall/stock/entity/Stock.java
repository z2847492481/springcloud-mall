package com.mall.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Integer available;
    private Integer locked;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
