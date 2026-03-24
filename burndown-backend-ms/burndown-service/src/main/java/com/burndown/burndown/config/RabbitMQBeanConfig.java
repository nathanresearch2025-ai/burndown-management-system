package com.burndown.burndown.config;

import com.burndown.burndown.consumer.RabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQBeanConfig {

    @Bean
    public Queue burndownQueue() {
        return new Queue(RabbitMQConfig.BURNDOWN_QUEUE, true);
    }

    @Bean
    public TopicExchange taskEventsExchange() {
        return new TopicExchange(RabbitMQConfig.TASK_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Binding burndownBinding(Queue burndownQueue, TopicExchange taskEventsExchange) {
        return BindingBuilder.bind(burndownQueue)
                .to(taskEventsExchange)
                .with(RabbitMQConfig.ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
