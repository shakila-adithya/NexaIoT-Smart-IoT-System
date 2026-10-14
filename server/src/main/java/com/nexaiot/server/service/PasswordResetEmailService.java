package com.nexaiot.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;
    private final long expiryMinutes;

    public PasswordResetEmailService(JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.password-reset-expiry-minutes:30}") long expiryMinutes) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.expiryMinutes = expiryMinutes;
    }

    public void send(String recipient, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("NexaIoT password reset");
        message.setText("NexaIoT password reset\n\n"
                + "Use the link below to choose a new password:\n"
                + frontendUrl.replaceAll("/$", "") + "/reset-password?token=" + token + "\n\n"
                + "This link expires in " + expiryMinutes + " minutes and can be used only once.\n\n"
                + "If you did not request this password reset, you can ignore this email.");
        mailSender.send(message);
    }
}
