package com.burndown.burndown.consumer;

public class RabbitMQConfig {
    public static final String BURNDOWN_QUEUE = "burndown.task.events";
    public static final String TASK_EVENTS_EXCHANGE = "task.events";
    public static final String ROUTING_KEY = "task.#";
}
