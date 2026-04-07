package com.tunegocio.turnosapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.data.redis.host")
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer())
                );

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        // Servicios cambian poco → TTL largo
        caches.put("servicios",        defaultConfig.entryTtl(Duration.ofHours(1)));
        // Staff cambia poco
        caches.put("staff",            defaultConfig.entryTtl(Duration.ofHours(1)));
        // Disponibilidad puede cambiar con frecuencia media
        caches.put("disponibilidad",   defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // Slots cambian rápido (nuevos turnos los invalidan)
        caches.put("slots",            defaultConfig.entryTtl(Duration.ofMinutes(2)));
        // Datos públicos del tenant (logo, color, nombre)
        caches.put("tenant-publico",   defaultConfig.entryTtl(Duration.ofHours(6)));
        // Reportes dashboard
        caches.put("dashboard",        defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(caches)
                .build();
    }
}
