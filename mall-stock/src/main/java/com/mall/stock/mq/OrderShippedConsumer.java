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

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShippedConsumer {

    private final StockConfirmService stockConfirmService;

    @KafkaListener(topics = CommonConstants.TOPIC_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event, Acknowledgment ack) {
        log.info("收到 order-shipped, orderNo={}", event.getOrderNo());
        // 幂等键是 (CONFIRM, orderNo, productId)，同一商品多行明细必须先合并数量
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItemDTO item : event.getItems()) {
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        quantityByProduct.forEach((productId, quantity) ->
                stockConfirmService.confirm(event.getOrderNo(), productId, quantity));
        ack.acknowledge();
    }
}
