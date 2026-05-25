package com.novahub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("nova_hub")
            .withUsername("novahub")
            .withPassword("root123")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/sql/001_initial_schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001_initial_schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/sql/002_recommend_schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/002_recommend_schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/sql/003_demo_data.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/003_demo_data.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.dynamic.datasource.master.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.master.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.master.password", MYSQL::getPassword);
        registry.add("spring.datasource.dynamic.datasource.slave.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.slave.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.slave.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:19092");
        registry.add("elasticsearch.host", () -> "127.0.0.1");
        registry.add("elasticsearch.port", () -> "19200");
        registry.add("outbox.dispatch.enabled", () -> "false");
        registry.add("xxl-job.enabled", () -> "false");
    }

    @Test
    void coreUserContentInteractionSearchAndHotrankFlow() {
        Map<String, Object> login = post("/api/auth/login", Map.of("username", "demo_user", "password", "123456"), null);
        String token = stringAt(login, "data", "token");
        assertNotNull(token);

        Map<String, Object> me = get("/api/users/me", token);
        assertEquals("demo_user", stringAt(me, "data", "username"));

        Map<String, Object> contents = get("/api/contents?page=1&pageSize=5", token);
        List<?> records = listAt(contents, "data", "records");
        assertFalse(records.isEmpty());
        Number contentId = (Number) ((Map<?, ?>) records.get(0)).get("id");

        Map<String, Object> detail = get("/api/contents/" + contentId, token);
        assertEquals(contentId.longValue(), ((Number) ((Map<?, ?>) detail.get("data")).get("id")).longValue());

        post("/api/contents/" + contentId + "/like", Map.of(), token);
        post("/api/contents/" + contentId + "/comments", Map.of("content", "Testcontainers 核心链路评论"), token);

        Map<String, Object> search = get("/api/search?keyword=NovaHub&page=1&pageSize=5", token);
        assertEquals(200, ((Number) search.get("code")).intValue());

        Map<String, Object> hotrank = get("/api/hotrank/all?limit=5", null);
        assertFalse(((List<?>) hotrank.get("data")).isEmpty());
    }

    private Map<String, Object> get(String path, String token) {
        HttpHeaders headers = headers(token);
        ResponseEntity<Map> response = restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(), path);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Object body, String token) {
        HttpHeaders headers = headers(token);
        ResponseEntity<Map> response = restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(), path + " -> " + response.getStatusCode());
        return response.getBody();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @SuppressWarnings("unchecked")
    private String stringAt(Map<String, Object> map, String first, String second) {
        return String.valueOf(((Map<String, Object>) map.get(first)).get(second));
    }

    @SuppressWarnings("unchecked")
    private List<?> listAt(Map<String, Object> map, String first, String second) {
        return (List<?>) ((Map<String, Object>) map.get(first)).get(second);
    }
}
