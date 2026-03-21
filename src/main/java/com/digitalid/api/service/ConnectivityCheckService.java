package com.digitalid.api.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Checks connectivity to MySQL, Redis, and Kafka. Used by the testing API.
 */
@Service
public class ConnectivityCheckService {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityCheckService.class);

    private final DataSource dataSource;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final JavaMailSender mailSender;

    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    public ConnectivityCheckService(DataSource dataSource,
                                   ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
                                   ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                                   JavaMailSender mailSender) {
        this.dataSource = dataSource;
        this.redisTemplateProvider = redisTemplateProvider;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.mailSender = mailSender;
    }

    public Map<String, Map<String, Object>> checkAll() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        result.put("mysql", checkMysql());
        result.put("redis", checkRedis());
        result.put("kafka", checkKafka());
        result.put("smtp", checkSmtp());
        return result;
    }

    private Map<String, Object> checkMysql() {
        Map<String, Object> out = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            out.put("status", valid ? "UP" : "DOWN");
            out.put("message", valid ? "Connected" : "Connection invalid");
        } catch (Exception e) {
            log.debug("MySQL connectivity check failed", e);
            out.put("status", "DOWN");
            out.put("message", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
        return out;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> out = new HashMap<>();
        RedisTemplate<String, Object> redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            out.put("status", "DISABLED");
            out.put("message", "Redis not configured (app.redis.enabled=false)");
            return out;
        }
        try(RedisConnection conn = redis.getConnectionFactory().getConnection()) {
            conn.ping();
            out.put("status", "UP");
            out.put("message", "PONG");
        } catch (Exception e) {
            log.debug("Redis connectivity check failed", e);
            out.put("status", "DOWN");
            out.put("message", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
        return out;
    }

    private Map<String, Object> checkKafka() {
        Map<String, Object> out = new HashMap<>();
        KafkaTemplate<String, String> kafka = kafkaTemplateProvider.getIfAvailable();
        if (kafka == null) {
            out.put("status", "DISABLED");
            out.put("message", "Kafka not configured (app.kafka.enabled=false)");
            return out;
        }
        if (kafkaBootstrapServers == null || kafkaBootstrapServers.isBlank()) {
            out.put("status", "DOWN");
            out.put("message", "No bootstrap servers configured");
            return out;
        }
        try (AdminClient admin = adminClient()) {
            admin.listTopics().listings().get(3, TimeUnit.SECONDS); // 3s not 5s
            out.put("status", "UP");
            out.put("message", "Broker reachable");
        } catch (Exception e) {
            log.debug("Kafka connectivity check failed", e);
            out.put("status", "DOWN");
            out.put("message", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
        return out;
    }

    public Map<String, Object> checkSmtp() {
        Map<String, Object> out = new HashMap<>();
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            out.put("status", "UNKNOWN");
            out.put("message", "Mail sender is not a JavaMailSenderImpl — cannot test connection");
            return out;
        }
        out.put("host", impl.getHost());
        out.put("port", impl.getPort());
        out.put("username", impl.getUsername());
        try {
            impl.testConnection();
            out.put("status", "UP");
            out.put("message", "SMTP authentication successful");
        } catch (Exception e) {
            log.debug("SMTP connectivity check failed", e);
            out.put("status", "DOWN");
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            out.put("message", cause.getMessage() != null ? cause.getMessage() : e.getMessage());
        }
        return out;
    }

    private AdminClient adminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 5000);
        return AdminClient.create(props);
    }
}
