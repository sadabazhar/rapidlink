package com.rapidlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {


    /**
     * For simple String key-value operations.
     * Primary use case: shortCode → originalUrl mapping
     * Uses StringRedisSerializer on both key and value — fastest, human-readable.
     */
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * For structured object storage.
     * Use cases: metadata, session data, analytics, click tracking etc.
     * - String keys: human-readable in redis-cli
     * - JSON values: avoids Java binary serialization, type-safe, debuggable
     * - Hash serializers: supports future HSET/HGET operations
     */
    @Bean
    public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // Connection: links Spring app to Redis server
        template.setConnectionFactory(connectionFactory);

        // Serialize key (to stored as plain strings) and value (to stored as JSON)
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer();

        // Key-Value: shortCode -> URL (main use case)
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);

        // Hash: supports structured data (e.g., clickCount, metadata)
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        // Initialize and validate configuration
        template.afterPropertiesSet();
        return template;
    }
}
