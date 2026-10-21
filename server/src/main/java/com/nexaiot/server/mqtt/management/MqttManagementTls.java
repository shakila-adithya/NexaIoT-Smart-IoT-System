package com.nexaiot.server.mqtt.management;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

final class MqttManagementTls {

    private MqttManagementTls() {
    }

    static SocketFactory socketFactory(String caCertificatePath) {
        if (caCertificatePath == null || caCertificatePath.isBlank()) {
            return null;
        }
        try (InputStream input = Files.newInputStream(Path.of(caCertificatePath))) {
            Certificate certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("nexaiot-management-ca", certificate);

            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "MQTT management CA certificate could not be loaded.", exception);
        }
    }
}
