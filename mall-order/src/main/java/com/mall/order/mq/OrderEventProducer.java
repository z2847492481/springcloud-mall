package com.mall.order.mq;

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
        kafkaTemplate.send(CommonConstants.TOPIC_ORDER_CREATED, event.getOrderNo(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("发送 order-created 失败, orderNo={}", event.getOrderNo(), ex);
                        return;
                    }
                    log.info("发送 order-created 成功, orderNo={}, partition={}, offset={}",
                            event.getOrderNo(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }
}
