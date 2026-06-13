package com.tainted.diary.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    /**
     * 브로커에 토픽이 없을 때 자동 생성. 이미 있으면 멱등적으로 무시.
     */
    @Bean
    public NewTopic diaryCreatedTopic() {
        return TopicBuilder.name("diary.created")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
