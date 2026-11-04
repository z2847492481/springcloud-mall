package com.mall.order.mq;

import com.mall.api.order.dto.OrderCancelledEvent;
import com.mall.api.order.dto.OrderCreatedEvent;
import com.mall.common.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderCreated(OrderCreatedEvent event) {
        send(CommonConstants.TOPIC_ORDER_CREATED, event.getOrderNo(), event);
    }

    public void sendOrderCancelled(OrderCancelledEvent event) {
        send(CommonConstants.TOPIC_ORDER_CANCELLED, event.getOrderNo(), event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("发送 {} 失败, key={}", topic, key, ex);
                        return;
                    }
                    log.info("发送 {} 成功, key={}, partition={}, offset={}",
                            topic, key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }
}
