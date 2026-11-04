package com.mall.wms.controller;

import com.mall.common.result.Result;
import com.mall.wms.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wms")
@RequiredArgsConstructor
public class WmsController {

    private final OutboundService outboundService;

    @PostMapping("/outbound/{orderNo}")
    public Result<Void> confirmOutbound(@PathVariable("orderNo") String orderNo) {
        outboundService.confirmOutbound(orderNo);
        return Result.ok();
    }
}
