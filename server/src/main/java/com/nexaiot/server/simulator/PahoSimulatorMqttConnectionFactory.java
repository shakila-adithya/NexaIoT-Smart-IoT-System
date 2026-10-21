package com.nexaiot.server.simulator;

import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.function.Consumer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/** Real Paho transport. Construction is explicit; no connection starts automatically. */
public final class PahoSimulatorMqttConnectionFactory implements SimulatorMqttConnectionFactory {

    private final int connectionTimeoutSeconds;
    private final int keepAliveSeconds;

    public PahoSimulatorMqttConnectionFactory() {
        this(10, 30);
    }

    PahoSimulatorMqttConnectionFactory(int connectionTimeoutSeconds, int keepAliveSeconds) {
        if (connectionTimeoutSeconds <= 0 || keepAliveSeconds <= 0) {
            throw new IllegalArgumentException("MQTT timing values must be positive.");
        }
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.keepAliveSeconds = keepAliveSeconds;
    }

    @Override
    public SimulatorMqttConnection create(
            SimulatorDeviceIdentity identity,
            SimulatorMqttConfiguration configuration,
            String username,
            String clientId,
            char[] password,
            Consumer<SimulatorMqttFailure> disconnectListener
    ) throws SimulatorMqttException {
        if (identity == null || configuration == null
                || !identity.clientId().equals(clientId)
                || !identity.clientId().equals(username)
                || username == null || username.isBlank()
                || password == null || password.length == 0) {
            throw new SimulatorMqttException(
                    SimulatorMqttFailure.INVALID_DEVICE_IDENTITY,
                    "Simulator MQTT identity or credentials are invalid.");
        }
        try {
            MqttAsyncClient client = new MqttAsyncClient(
                    configuration.brokerUri().toString(), clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.clone());
            options.setSocketFactory(trustStore(configuration));
            options.setHttpsHostnameVerificationEnabled(true);
            HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
            options.setSSLHostnameVerifier(verifier);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(connectionTimeoutSeconds);
            options.setKeepAliveInterval(keepAliveSeconds);
            return new PahoSimulatorMqttConnection(client, options, disconnectListener);
        } catch (Exception exception) {
            throw new SimulatorMqttException(
                    SimulatorMqttFailure.TLS_VALIDATION_FAILURE,
                    "Simulator MQTT TLS setup failed.", exception);
        }
    }

    private javax.net.SocketFactory trustStore(SimulatorMqttConfiguration configuration)
            throws Exception {
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream input = Files.newInputStream(configuration.caCertificate())) {
            Certificate certificate = certificates.generateCertificate(input);
            trustStore.setCertificateEntry("nexaiot-simulator-ca", certificate);
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private static final class PahoSimulatorMqttConnection implements SimulatorMqttConnection {

        private final MqttAsyncClient client;

        PahoSimulatorMqttConnection(
                MqttAsyncClient client,
                MqttConnectOptions options,
                Consumer<SimulatorMqttFailure> disconnectListener
        ) {
            this.client = client;
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    // Lifecycle state is owned by SimulatorMqttClient.
                }

                @Override
                public void connectionLost(Throwable cause) {
                    disconnectListener.accept(classify(cause));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Per-topic listeners are registered by subscribe().
                }

                @Override
                public void deliveryComplete(
                        org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                    // No delivery secret or payload is logged.
                }
            });
            this.options = options;
        }

        private final MqttConnectOptions options;

        @Override
        public void connect() throws SimulatorMqttException {
            try {
                client.connect(options).waitForCompletion(
                        options.getConnectionTimeout() * 1000L);
            } catch (MqttException exception) {
                throw new SimulatorMqttException(classify(exception),
                        "Simulator MQTT connection failed.", exception);
            }
        }

        @Override
        public void subscribe(
                String exactTopic, java.util.function.BiConsumer<String, byte[]> consumer)
                throws SimulatorMqttException {
            if (exactTopic == null || exactTopic.isBlank() || exactTopic.contains("#")
                    || exactTopic.contains("+")) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.INVALID_DEVICE_IDENTITY,
                        "Simulator subscriptions must use an exact topic.");
            }
            try {
                IMqttMessageListener listener = (topic, message) ->
                        consumer.accept(topic, message.getPayload().clone());
                client.subscribe(exactTopic, 1, listener).waitForCompletion();
            } catch (MqttException exception) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.BROKER_UNAVAILABLE,
                        "Simulator MQTT subscription failed.", exception);
            }
        }

        @Override
        public void publish(String exactTopic, byte[] payload, int qos)
                throws SimulatorMqttException {
            if (exactTopic == null || exactTopic.isBlank() || exactTopic.contains("#")
                    || exactTopic.contains("+")) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.INVALID_DEVICE_IDENTITY,
                        "Simulator publications must use an exact topic.");
            }
            try {
                client.publish(exactTopic, payload.clone(), qos, false).waitForCompletion();
            } catch (MqttException exception) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.BROKER_UNAVAILABLE,
                        "Simulator MQTT publication failed.", exception);
            }
        }

        @Override
        public void disconnect() throws SimulatorMqttException {
            try {
                if (client.isConnected()) {
                    client.disconnect().waitForCompletion();
                }
            } catch (MqttException exception) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.UNEXPECTED_DISCONNECT,
                        "Simulator MQTT disconnect failed.", exception);
            }
        }

        @Override
        public boolean isConnected() {
            return client.isConnected();
        }

        @Override
        public void close() throws SimulatorMqttException {
            try {
                client.close();
            } catch (MqttException exception) {
                throw new SimulatorMqttException(
                        SimulatorMqttFailure.SHUTDOWN,
                        "Simulator MQTT shutdown failed.", exception);
            }
        }

        private static SimulatorMqttFailure classify(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof javax.net.ssl.SSLException
                        || current instanceof java.security.cert.CertificateException) {
                    return SimulatorMqttFailure.TLS_VALIDATION_FAILURE;
                }
                current = current.getCause();
            }
            if (throwable instanceof MqttException mqtt
                    ) {
                return switch (mqtt.getReasonCode()) {
                    case MqttException.REASON_CODE_NOT_AUTHORIZED,
                            MqttException.REASON_CODE_FAILED_AUTHENTICATION ->
                            SimulatorMqttFailure.INVALID_CREDENTIALS;
                    case MqttException.REASON_CODE_INVALID_CLIENT_ID ->
                            SimulatorMqttFailure.INVALID_CLIENT_ID;
                    case MqttException.REASON_CODE_CLIENT_TIMEOUT ->
                            SimulatorMqttFailure.CONNECTION_TIMEOUT;
                    case MqttException.REASON_CODE_SSL_CONFIG_ERROR ->
                            SimulatorMqttFailure.TLS_VALIDATION_FAILURE;
                    case MqttException.REASON_CODE_BROKER_UNAVAILABLE,
                            MqttException.REASON_CODE_SERVER_CONNECT_ERROR ->
                            SimulatorMqttFailure.BROKER_UNAVAILABLE;
                    default -> SimulatorMqttFailure.BROKER_UNAVAILABLE;
                };
            }
            return SimulatorMqttFailure.BROKER_UNAVAILABLE;
        }
    }
}
