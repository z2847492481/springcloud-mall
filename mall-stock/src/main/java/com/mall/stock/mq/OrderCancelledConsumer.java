package com.mall.stock.mq;

import com.mall.api.order.dto.OrderCancelledEvent;
import com.mall.api.order.dto.OrderItemDTO;
import com.mall.common.constant.CommonConstants;
import com.mall.stock.service.StockReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {

    private final StockReleaseService stockReleaseService;

    @KafkaListener(topics = CommonConstants.TOPIC_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event, Acknowledgment ack) {
        log.info("收到 order-cancelled, orderNo={}", event.getOrderNo());
        for (OrderItemDTO item : event.getItems()) {
            stockReleaseService.release(event.getOrderNo(), item.getProductId(), item.getQuantity());
        }
        ack.acknowledge();
    }
}
