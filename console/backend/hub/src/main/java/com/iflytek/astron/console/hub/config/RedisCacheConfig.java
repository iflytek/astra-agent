package com.iflytek.astron.console.hub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class RedisCacheConfig implements CachingConfigurer {

    private final ObjectMapper objectMapper;

    public RedisCacheConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    @Primary
    public CacheManager cacheManagerDefault(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = createBaseCacheConfiguration(Duration.ofMinutes(5));
        return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
    }

    @Bean("cacheManager10s")
    public CacheManager cacheManager10s(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = createBaseCacheConfiguration(Duration.ofSeconds(10));
        return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
    }

    @Bean("cacheManager5min")
    public CacheManager cacheManager5min(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = createBaseCacheConfiguration(Duration.ofMinutes(5));
        return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
    }

    @Bean("cacheManager30min")
    public CacheManager cacheManager30min(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = createBaseCacheConfiguration(Duration.ofMinutes(30));
        return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
    }

    @Bean("cacheManager1h")
    public CacheManager cacheManager1h(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = createBaseCacheConfiguration(Duration.ofHours(1));
        return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
    }

    private RedisCacheConfiguration createBaseCacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }

    /**
     * Create a serializer that includes type information
     */
    private GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer() {
        // Create a new ObjectMapper instance, or clone existing one, to avoid polluting the global
        // ObjectMapper
        ObjectMapper redisObjectMapper = objectMapper.copy();

        // Enable default type handling
        // Add a "@class" attribute to the serialized JSON to specify the actual type of the object
        // LaissezFaireSubTypeValidator.instance is a safe validator that allows all types
        // ObjectMapper.DefaultTyping.NON_FINAL indicates that type information is included for all
        // non-final types
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                if (exception instanceof SerializationException) {
                    log.warn("Cache [{}] deserialization failed (key={}), will execute real logic and refresh cache", cache.getName(), key, exception);
                    return;
                }
                log.error("Cache [{}] read failed (key={})", cache.getName(), key);
                throw exception;
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.error("Cache [{}] write failed (key={})", cache.getName(), key);
                throw exception;
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("Cache [{}] evict failed (key={})", cache.getName(), key);
                throw exception;
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Cache [{}] clear failed", cache.getName());
                throw exception;
            }
        };
    }
}
