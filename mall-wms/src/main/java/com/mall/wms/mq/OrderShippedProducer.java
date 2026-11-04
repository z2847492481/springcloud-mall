package com.mall.wms.mq;

import com.mall.api.order.dto.OrderShippedEvent;
import com.mall.common.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShippedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(OrderShippedEvent event) {
        kafkaTemplate.send(CommonConstants.TOPIC_ORDER_SHIPPED, event.getOrderNo(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("发送 order-shipped 失败, orderNo={}", event.getOrderNo(), ex);
                        return;
                    }
                    log.info("发送 order-shipped 成功, orderNo={}, partition={}, offset={}",
                            event.getOrderNo(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }
}
