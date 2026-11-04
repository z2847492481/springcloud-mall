package com.mall.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_outbound")
public class Outbound {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
