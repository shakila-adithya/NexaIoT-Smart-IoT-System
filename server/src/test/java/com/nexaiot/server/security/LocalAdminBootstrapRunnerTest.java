package com.nexaiot.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LocalAdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void promotesOnlyTheExactExistingUserAndInvalidatesTokens() {
        User user = new User("Owner", "owner@example.com", "hash", "USER");
        user.setTokenVersion(4);
        when(mongoTemplate.findById("local-admin-bootstrap-v1", AdminBootstrapState.class))
                .thenReturn(null);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));

        runner("owner@example.com", "owner@example.com").run();

        assertEquals("ADMIN", user.getRole());
        assertEquals(5, user.getTokenVersion());
        verify(userRepository).save(user);
        ArgumentCaptor<AdminBootstrapState> stateCaptor = ArgumentCaptor.forClass(AdminBootstrapState.class);
        verify(mongoTemplate).save(stateCaptor.capture());
        assertEquals("COMPLETED", stateCaptor.getValue().getStatus());
    }

    @Test
    void rejectsAConfirmationForAnotherEmail() {
        assertThrows(IllegalStateException.class,
                () -> runner("owner@example.com", "other@example.com").run());

        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void rejectsAClaimedBootstrapWithoutChangingTheUser() {
        when(mongoTemplate.findById("local-admin-bootstrap-v1", AdminBootstrapState.class))
                .thenReturn(new AdminBootstrapState(
                        "local-admin-bootstrap-v1", "owner@example.com", "CLAIMED", java.time.Instant.now()));

        assertThrows(IllegalStateException.class,
                () -> runner("owner@example.com", "owner@example.com").run());

        verify(userRepository, org.mockito.Mockito.never()).findByEmail("owner@example.com");
    }

    @Test
    void rejectsNonLocalMongo() {
        assertThrows(IllegalStateException.class,
                () -> new LocalAdminBootstrapRunner(
                        userRepository,
                        mongoTemplate,
                        "mongodb://db.example.com:27017/nexaiot",
                        "owner@example.com",
                        "I_AM_AUTHORIZED_TO_CHANGE_THIS_LOCAL_USER",
                        "I_UNDERSTAND_THIS_IS_LOCAL_ONLY:owner@example.com").run());
    }

    private LocalAdminBootstrapRunner runner(String email, String confirmationEmail) {
        return new LocalAdminBootstrapRunner(
                userRepository,
                mongoTemplate,
                "mongodb://127.0.0.1:27017/nexaiot",
                email,
                "I_AM_AUTHORIZED_TO_CHANGE_THIS_LOCAL_USER",
                "I_UNDERSTAND_THIS_IS_LOCAL_ONLY:" + confirmationEmail);
    }
}
