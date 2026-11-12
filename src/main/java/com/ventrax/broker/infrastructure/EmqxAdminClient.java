package com.ventrax.broker.infrastructure;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmqxAdminClient {
    private static final String USERS = "/authentication/password_based%3Abuilt_in_database/users";
    private static final String RULES = "/authorization/sources/built_in_database/rules/users";
    private final String baseUrl;
    private final String authorization;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public EmqxAdminClient(
            @Value("${emqx.api-url:http://localhost:18083/api/v5}") String baseUrl,
            @Value("${emqx.api-key:}") String apiKey,
            @Value("${emqx.api-secret:}") String apiSecret,
            ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                (apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper;
    }

    public void authorize(String deviceId, String password) {
        String username = encode(deviceId);
        updateRules(deviceId, username, List.of(
                Map.of("topic", "#", "permission", "deny", "action", "all")));
        Response user = request("POST", USERS,
                Map.of("user_id", deviceId, "password", password, "is_superuser", false), true);
        if (user.statusCode() == 409) {
            request("PUT", USERS + "/" + username,
                    Map.of("password", password, "is_superuser", false));
        } else {
            require(user);
        }
        updateRules(deviceId, username, List.of(
                Map.of("topic", "smartgn/devices/" + deviceId + "/telemetry",
                        "permission", "allow", "action", "publish", "qos", List.of(1)),
                Map.of("topic", "#", "permission", "deny", "action", "all")));
    }

    public void revoke(String deviceId) {
        String username = encode(deviceId);
        updateRules(deviceId, username, List.of(
                Map.of("topic", "#", "permission", "deny", "action", "all")));
        request("DELETE", USERS + "/" + username, null, true);
        Response sessions = request("GET", "/clients?username=" + username + "&limit=1000&page=1", null, false);
        if (sessions.statusCode() != 404) {
            require(sessions);
            try {
                Map<?, ?> response = mapper.readValue(sessions.body(), Map.class);
                if (response.get("data") instanceof List<?> clients) {
                    for (Object item : clients) {
                        if (item instanceof Map<?, ?> device && device.get("clientid") instanceof String id) {
                            request("DELETE", "/clients/" + encode(id), null, true);
                        }
                    }
                }
            } catch (Exception error) {
                throw unavailable(error);
            }
        }
    }

    public List<String> listDeviceUsers() {
        Response response = request("GET", USERS + "?limit=1000&page=1", null);
        try {
            Map<?, ?> body = mapper.readValue(response.body(), Map.class);
            if (!(body.get("data") instanceof List<?> rows)) throw unavailable(null);
            return rows.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(row -> row.get("user_id"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> {
                        try {
                            java.util.UUID.fromString(value);
                            return true;
                        } catch (IllegalArgumentException ignored) {
                            return false;
                        }
                    })
                    .toList();
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw unavailable(error);
        }
    }

    private Response request(String method, String path, Object body) {
        return request(method, path, body, false);
    }

    private void updateRules(String deviceId, String username, List<Map<String, Object>> rules) {
        Map<String, Object> payload = Map.of("username", deviceId, "rules", rules);
        Response response = request("PUT", RULES + "/" + username, payload, true);
        if (response.statusCode() == 404) {
            request("POST", RULES, List.of(payload));
        }
    }

    private Response request(String method, String path, Object body, boolean allowMissing) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", authorization)
                    .header("Accept", "application/json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (allowMissing && (response.statusCode() == 404 || response.statusCode() == 409)) {
                return new Response(response.statusCode(), response.body());
            }
            return require(response);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw unavailable(error);
        }
    }

    private Response require(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable(null);
        }
        return new Response(response.statusCode(), response.body());
    }

    private Response require(Response response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable(null);
        return response;
    }

    private ResponseStatusException unavailable(Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT broker management is unavailable", cause);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Response(int statusCode, String body) {}
}
