package com.ventrax.broker.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import com.ventrax.broker.infrastructure.EmqxAdminClient;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/broker/devices")
@Tag(name = "Device access", description = "Manage device credentials and telemetry topic permissions in EMQX.")
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
            Instant updatedAt,
            String oneTimeCredential
    ) {}

    private final Map<UUID, DeviceBrokerCredential> credentials = new ConcurrentHashMap<>();
    private final EmqxAdminClient emqx;
    private final String serviceToken;

    public BrokerDeviceAclController(
            EmqxAdminClient emqx,
            @Value("${smartgn.internal-token:}") String serviceToken) {
        this.emqx = emqx;
        this.serviceToken = serviceToken;
    }

    @PostMapping("/authorize")
    @Operation(summary = "Authorize a device",
            description = "Creates device credentials and grants MQTT publish access to its telemetry topic.")
    @SecurityRequirement(name = "serviceAuth")
    public ResponseEntity<DeviceBrokerCredential> authorizeDevice(
            @RequestBody AuthorizeDeviceRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        if (request == null || request.deviceId() == null || request.associationVersion() == null
                || request.associationVersion() < 0 || request.hardwareSerial() == null
                || request.hardwareSerial().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid device authorization request");
        }
        String password = UUID.randomUUID() + UUID.randomUUID().toString();
        emqx.authorize(request.deviceId().toString(), password);
        DeviceBrokerCredential cred = new DeviceBrokerCredential(
                UUID.randomUUID(),
                request.deviceId(),
                request.hardwareSerial(),
                "smartgn/devices/" + request.deviceId() + "/telemetry",
                request.deviceId().toString(),
                "AUTHORIZED",
                request.associationVersion(),
                Instant.now(),
                password
        );
        credentials.put(request.deviceId(), cred);
        return ResponseEntity.status(HttpStatus.CREATED).body(cred);
    }

    @PostMapping("/{deviceId}/revoke")
    @Operation(summary = "Revoke device access",
            description = "Removes the device credentials and revokes its MQTT access.")
    @SecurityRequirement(name = "serviceAuth")
    public ResponseEntity<DeviceBrokerCredential> revokeDevice(
            @PathVariable UUID deviceId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        DeviceBrokerCredential existing = credentials.get(deviceId);
        emqx.revoke(deviceId.toString());
        DeviceBrokerCredential revoked = new DeviceBrokerCredential(
                existing == null ? deviceId : existing.credentialId(),
                deviceId,
                existing == null ? "" : existing.hardwareSerial(),
                "smartgn/devices/" + deviceId + "/telemetry",
                deviceId.toString(),
                "REVOKED",
                existing == null ? null : existing.associationVersion(),
                Instant.now(),
                null
        );
        credentials.put(deviceId, revoked);
        return ResponseEntity.ok(revoked);
    }

    @GetMapping
    @Operation(summary = "List authorized devices",
            description = "Lists device credentials tracked by EMQX without returning their secrets.")
    @SecurityRequirement(name = "serviceAuth")
    public ResponseEntity<Collection<DeviceBrokerCredential>> listAuthorizedDevices(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        var current = emqx.listDeviceUsers().stream()
                .map(value -> UUID.fromString(value))
                .map(deviceId -> {
                    DeviceBrokerCredential saved = credentials.get(deviceId);
                    Instant updatedAt = saved == null ? null : saved.updatedAt();
                    return new DeviceBrokerCredential(
                            saved == null ? deviceId : saved.credentialId(),
                            deviceId,
                            saved == null ? "" : saved.hardwareSerial(),
                            "smartgn/devices/" + deviceId + "/telemetry",
                            deviceId.toString(),
                            "AUTHORIZED",
                            saved == null ? null : saved.associationVersion(),
                            updatedAt,
                            null);
                })
                .toList();
        return ResponseEntity.ok(current);
    }

    private void requireInternalToken(String authorization) {
        String expected = "Bearer " + serviceToken;
        if (serviceToken.isBlank() || authorization == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
    }
}
