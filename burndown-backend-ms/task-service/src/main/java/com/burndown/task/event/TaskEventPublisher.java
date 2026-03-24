package com.burndown.task.event;

import com.burndown.common.dto.TaskEventDTO;
import com.burndown.task.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public static final String EXCHANGE = "task.events";
    public static final String ROUTING_KEY_STATUS = "task.status.changed";
    public static final String ROUTING_KEY_CREATED = "task.created";

    public void publishTaskCreated(Task task) {
        TaskEventDTO event = new TaskEventDTO();
        event.setEventType(TaskEventDTO.EventType.TASK_CREATED);
        event.setTaskId(task.getId());
        event.setTaskKey(task.getTaskKey());
        event.setSprintId(task.getSprintId());
        event.setProjectId(task.getProjectId());
        event.setNewStatus(task.getStatus());
        event.setStoryPoints(task.getStoryPoints());
        publish(ROUTING_KEY_CREATED, event);
    }

    public void publishStatusChanged(Task task, String oldStatus) {
        TaskEventDTO event = new TaskEventDTO();
        event.setEventType(TaskEventDTO.EventType.TASK_STATUS_CHANGED);
        event.setTaskId(task.getId());
        event.setTaskKey(task.getTaskKey());
        event.setSprintId(task.getSprintId());
        event.setProjectId(task.getProjectId());
        event.setOldStatus(oldStatus);
        event.setNewStatus(task.getStatus());
        event.setStoryPoints(task.getStoryPoints());
        publish(ROUTING_KEY_STATUS, event);
    }

    private void publish(String routingKey, TaskEventDTO event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, event);
            log.debug("Published event {} for task {}", event.getEventType(), event.getTaskId());
        } catch (Exception e) {
            log.error("Failed to publish task event: {}", e.getMessage());
            // Non-fatal: task operation succeeds even if event publish fails
        }
    }
}
