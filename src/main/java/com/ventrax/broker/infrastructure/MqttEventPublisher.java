package com.ventrax.broker.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MqttEventPublisher {
    private final String brokerUrl;
    private final String username;
    private final String password;
    private final int timeoutSeconds;

    public MqttEventPublisher(
            @Value("${mqtt.broker.host:localhost}") String host,
            @Value("${mqtt.broker.port:1883}") int port,
            @Value("${mqtt.broker.username:broker-service}") String username,
            @Value("${mqtt.broker.password:}") String password,
            @Value("${mqtt.broker.timeout-seconds:5}") int timeoutSeconds) {
        this.brokerUrl = "tcp://" + host + ":" + port;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void publish(String topic, String json, int qos) {
        MqttAsyncClient client = null;
        try {
            client = new MqttAsyncClient(
                    brokerUrl,
                    "smartgn-message-broker-" + UUID.randomUUID(),
                    new MemoryPersistence());
            var options = new MqttConnectionOptions();
            options.setCleanStart(true);
            options.setUserName(username);
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            client.connect(options).waitForCompletion(TimeUnit.SECONDS.toMillis(timeoutSeconds));

            var message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            client.publish(topic, message).waitForCompletion(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "MQTT broker is unavailable", exception);
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect().waitForCompletion(TimeUnit.SECONDS.toMillis(timeoutSeconds));
                    }
                    client.close();
                } catch (Exception ignored) {
                    // A confirmed publish is not undone when closing its client fails.
                }
            }
        }
    }
}
