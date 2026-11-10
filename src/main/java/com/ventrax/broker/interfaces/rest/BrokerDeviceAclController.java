package com.ventrax.broker.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/broker/devices")
public class BrokerDeviceAclController {

    public record AuthorizeDeviceRequest(UUID deviceId, String hardwareSerial, UUID supplyPointId, Long associationVersion) {}
    public record DeviceBrokerCredential(
            UUID credentialId,
            UUID deviceId,
            String hardwareSerial,
            String allowedTopic,
            String mqttUsername,
            String status,
            Long associationVersion,
            Instant updatedAt
    ) {}

    private final Map<UUID, DeviceBrokerCredential> credentials = new ConcurrentHashMap<>();

    @PostMapping("/authorize")
    public ResponseEntity<DeviceBrokerCredential> authorizeDevice(@RequestBody AuthorizeDeviceRequest request) {
        DeviceBrokerCredential cred = new DeviceBrokerCredential(
                UUID.randomUUID(),
                request.deviceId(),
                request.hardwareSerial(),
                "smartgn/v1/telemetry/devices/" + request.deviceId(),
                "iot-" + request.hardwareSerial().toLowerCase(),
                "AUTHORIZED",
                request.associationVersion(),
                Instant.now()
        );
        credentials.put(request.deviceId(), cred);
        return ResponseEntity.status(HttpStatus.CREATED).body(cred);
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<DeviceBrokerCredential> revokeDevice(@PathVariable UUID deviceId) {
        DeviceBrokerCredential existing = credentials.get(deviceId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        DeviceBrokerCredential revoked = new DeviceBrokerCredential(
                existing.credentialId(),
                existing.deviceId(),
                existing.hardwareSerial(),
                existing.allowedTopic(),
                existing.mqttUsername(),
                "REVOKED",
                existing.associationVersion(),
                Instant.now()
        );
        credentials.put(deviceId, revoked);
        return ResponseEntity.ok(revoked);
    }

    @GetMapping
    public ResponseEntity<Collection<DeviceBrokerCredential>> listAuthorizedDevices() {
        return ResponseEntity.ok(credentials.values());
    }
}