package com.nexaiot.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;

@Component
@Profile("local")
@ConditionalOnProperty(name = "admin.bootstrap.enabled", havingValue = "true")
public class LocalAdminBootstrapRunner implements CommandLineRunner {

    private static final String STATE_ID = "local-admin-bootstrap-v1";
    private static final String AUTHORIZATION = "I_AM_AUTHORIZED_TO_CHANGE_THIS_LOCAL_USER";
    private static final String CONFIRMATION_PREFIX = "I_UNDERSTAND_THIS_IS_LOCAL_ONLY:";

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final String mongoUri;
    private final String email;
    private final String authorization;
    private final String confirmation;

    public LocalAdminBootstrapRunner(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            @Value("${spring.mongodb.uri}") String mongoUri,
            @Value("${admin.bootstrap.email}") String email,
            @Value("${admin.bootstrap.authorization:}") String authorization,
            @Value("${admin.bootstrap.confirmation:}") String confirmation
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.mongoUri = mongoUri;
        this.email = email;
        this.authorization = authorization;
        this.confirmation = confirmation;
    }

    @Override
    public void run(String... args) {
        requireLocalMongo();
        String normalizedEmail = normalize(email);
        if (!AUTHORIZATION.equals(authorization)) {
            throw new IllegalStateException("Admin bootstrap authorization was not confirmed.");
        }
        if (!(CONFIRMATION_PREFIX + normalizedEmail).equals(confirmation)) {
            throw new IllegalStateException("Admin bootstrap confirmation did not match the target email.");
        }

        AdminBootstrapState previous = mongoTemplate.findById(STATE_ID, AdminBootstrapState.class);
        if (previous != null) {
            throw new IllegalStateException("The local admin bootstrap has already been used or claimed.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("The target local user does not exist."));
        if (!"USER".equals(user.getRole())) {
            throw new IllegalStateException("Only an existing USER account can be designated as admin.");
        }

        Instant now = Instant.now();
        AdminBootstrapState state = new AdminBootstrapState(STATE_ID, normalizedEmail, "CLAIMED", now);
        mongoTemplate.insert(state);

        user.setRole("ADMIN");
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userRepository.save(user);

        state.setStatus("COMPLETED");
        state.setCompletedAt(Instant.now());
        mongoTemplate.save(state);
        System.out.println("Local admin bootstrap completed for " + normalizedEmail);
    }

    private void requireLocalMongo() {
        URI uri;
        try {
            uri = URI.create(mongoUri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Admin bootstrap requires a local MongoDB URI.", exception);
        }
        String host = uri.getHost();
        if (host == null || !(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1"))) {
            throw new IllegalStateException("Admin bootstrap is restricted to localhost MongoDB.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
