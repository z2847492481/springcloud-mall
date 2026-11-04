package com.mall.wms.service.impl;

import com.mall.api.order.OrderApi;
import com.mall.api.order.dto.OrderDTO;
import com.mall.api.order.dto.OrderShippedEvent;
import com.mall.common.enums.ErrorCode;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.wms.mapper.OutboundMapper;
import com.mall.wms.mq.OrderShippedProducer;
import com.mall.wms.service.OutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundServiceImpl implements OutboundService {

    private final OrderApi orderApi;
    private final OutboundMapper outboundMapper;
    private final OrderShippedProducer orderShippedProducer;

    @Override
    public void confirmOutbound(String orderNo) {
        Result<OrderDTO> orderResult = orderApi.getByOrderNo(orderNo);
        if (orderResult == null || orderResult.getCode() != ErrorCode.SUCCESS.getCode()
                || orderResult.getData() == null) {
            throw new BusinessException(orderResult == null ? ErrorCode.INTERNAL_ERROR.getCode()
                    : orderResult.getCode(),
                    orderResult == null ? "订单服务调用失败" : orderResult.getMessage());
        }
        OrderDTO order = orderResult.getData();

        // PAID -> SHIPPED；已经 SHIPPED 时 ship 接口自身幂等
        Result<Void> shipResult = orderApi.ship(orderNo);
        if (shipResult == null || shipResult.getCode() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(shipResult == null ? ErrorCode.INTERNAL_ERROR.getCode()
                    : shipResult.getCode(),
                    shipResult == null ? "订单发货失败" : shipResult.getMessage());
        }

        int inserted = outboundMapper.insertIgnore(orderNo);
        if (inserted == 0) {
            log.info("出库单已存在，按幂等处理, orderNo={}", orderNo);
        }

        orderShippedProducer.send(new OrderShippedEvent(
                order.getOrderNo(), order.getUserId(), order.getTotalAmount(), order.getItems()));
    }
}
