package com.ventrax.broker.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import com.ventrax.broker.infrastructure.MqttEventPublisher;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/v1/broker/messages")
@Tag(name = "Service events", description = "Publish service events and inspect recent acknowledgments.")
public class MqttTopicPublishController {

    public record PublishMqttMessageRequest(String topic, UUID eventId, int qos, Map<String, Object> payload) {}
    public record MqttMessageEnvelope(
            UUID messageId,
            UUID eventId,
            String protocolVersion,
            String topic,
            int qos,
            boolean tlsEncrypted,
            Map<String, Object> payload,
            Instant publishedAt
    ) {}

    private final List<MqttMessageEnvelope> messageLog = Collections.synchronizedList(new ArrayList<>());
    private final MqttEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final String serviceToken;

    public MqttTopicPublishController(
            MqttEventPublisher publisher,
            ObjectMapper objectMapper,
            @Value("${mqtt.broker.topic-prefix:smartgn/events/broker/}") String topicPrefix,
            @Value("${smartgn.internal-token:}") String serviceToken) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.topicPrefix = topicPrefix;
        this.serviceToken = serviceToken;
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish a service event",
            description = "Sends a service event to an allowed MQTT topic with the requested QoS.")
    @SecurityRequirement(name = "serviceAuth")
    public ResponseEntity<MqttMessageEnvelope> publishMessage(
            @RequestBody PublishMqttMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        if (request == null || request.topic() == null || !request.topic().startsWith(topicPrefix)
                || request.topic().length() <= topicPrefix.length()
                || request.topic().contains("+") || request.topic().contains("#")
                || request.payload() == null || request.payload().isEmpty()
                || request.qos() < 0 || request.qos() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid broker event request");
        }
        UUID eventId = request.eventId() != null ? request.eventId() : UUID.randomUUID();
        try {
            publisher.publish(request.topic(), objectMapper.writeValueAsString(request.payload()), request.qos());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event payload", exception);
        }
        MqttMessageEnvelope envelope = new MqttMessageEnvelope(
                UUID.randomUUID(),
                eventId,
                "MQTT 5.0",
                request.topic(),
                request.qos(),
                false,
                request.payload(),
                Instant.now()
        );
        messageLog.add(envelope);
        synchronized (messageLog) {
            while (messageLog.size() > 500) messageLog.removeFirst();
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope);
    }

    @GetMapping
    @Operation(summary = "List recent published events",
            description = "Lists events acknowledged by the adapter during its current process lifetime.")
    @SecurityRequirement(name = "serviceAuth")
    public ResponseEntity<List<MqttMessageEnvelope>> getRecentMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        synchronized (messageLog) {
            return ResponseEntity.ok(List.copyOf(messageLog));
        }
    }

    private void requireInternalToken(String authorization) {
        String expected = "Bearer " + serviceToken;
        if (serviceToken.isBlank() || authorization == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
    }
}
