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

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {

    private final StockReleaseService stockReleaseService;

    @KafkaListener(topics = CommonConstants.TOPIC_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event, Acknowledgment ack) {
        log.info("收到 order-cancelled, orderNo={}", event.getOrderNo());
        // 幂等键是 (RELEASE, orderNo, productId)，同一商品多行明细必须先合并数量
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItemDTO item : event.getItems()) {
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        quantityByProduct.forEach((productId, quantity) ->
                stockReleaseService.release(event.getOrderNo(), productId, quantity));
        ack.acknowledge();
    }
}
