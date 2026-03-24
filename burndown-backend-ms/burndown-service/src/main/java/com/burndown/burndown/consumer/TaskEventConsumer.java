package com.burndown.burndown.consumer;

import com.burndown.burndown.service.BurndownService;
import com.burndown.common.dto.TaskEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventConsumer {

    private final BurndownService burndownService;

    @RabbitListener(queues = RabbitMQConfig.BURNDOWN_QUEUE)
    public void handleTaskEvent(TaskEventDTO event) {
        log.info("Received task event: type={}, taskId={}, sprintId={}",
                event.getEventType(), event.getTaskId(), event.getSprintId());
        try {
            if (event.getSprintId() != null) {
                burndownService.recordDailyPoint(event.getSprintId());
            }
        } catch (Exception e) {
            log.error("Failed to process task event: {}", e.getMessage(), e);
        }
    }
}
