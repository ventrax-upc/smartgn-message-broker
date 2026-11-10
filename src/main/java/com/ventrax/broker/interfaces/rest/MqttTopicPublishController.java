package com.ventrax.broker.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/broker/messages")
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

    @PostMapping("/publish")
    public ResponseEntity<MqttMessageEnvelope> publishMessage(@RequestBody PublishMqttMessageRequest request) {
        MqttMessageEnvelope envelope = new MqttMessageEnvelope(
                UUID.randomUUID(),
                request.eventId() != null ? request.eventId() : UUID.randomUUID(),
                "MQTT 5.0",
                request.topic(),
                request.qos() > 0 ? request.qos() : 1,
                true,
                request.payload(),
                Instant.now()
        );
        messageLog.add(envelope);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope);
    }

    @GetMapping
    public ResponseEntity<List<MqttMessageEnvelope>> getRecentMessages() {
        return ResponseEntity.ok(messageLog);
    }
}