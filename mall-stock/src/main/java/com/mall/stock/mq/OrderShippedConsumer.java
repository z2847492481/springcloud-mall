package com.mall.stock.mq;

import com.mall.api.order.dto.OrderItemDTO;
import com.mall.api.order.dto.OrderShippedEvent;
import com.mall.common.constant.CommonConstants;
import com.mall.stock.service.StockConfirmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShippedConsumer {

    private final StockConfirmService stockConfirmService;

    @KafkaListener(topics = CommonConstants.TOPIC_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event, Acknowledgment ack) {
        log.info("收到 order-shipped, orderNo={}", event.getOrderNo());
        for (OrderItemDTO item : event.getItems()) {
            stockConfirmService.confirm(event.getOrderNo(), item.getProductId(), item.getQuantity());
        }
        ack.acknowledge();
    }
}
