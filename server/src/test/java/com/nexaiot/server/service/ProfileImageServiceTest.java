package com.nexaiot.server.service;

import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
class ProfileImageServiceTest {

    @Test
    void rejectsUnsupportedMimeTypeAndOversizedFiles() {
        User user = user();
        ProfileImageService service = new ProfileImageService(repositoryFor(user), (GridFsTemplate) null);

        MockMultipartFile svg = new MockMultipartFile("image", "avatar.svg", "image/svg+xml", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.upload("owner@example.com", svg));

        MockMultipartFile oversized = new MockMultipartFile(
                "image", "avatar.png", "image/png", new byte[5 * 1024 * 1024 + 1]
        );
        assertThrows(IllegalArgumentException.class, () -> service.upload("owner@example.com", oversized));
    }

    @Test
    void usersWithoutImagesAreSafe() {
        User user = user();
        assertFalse(com.nexaiot.server.dto.UserResponse.from(user).isHasProfileImage());
        assertNull(user.getProfileImageId());
    }

    @Test
    void validPngIsDecodedBeforeStorageIsRequired() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MockMultipartFile png = new MockMultipartFile("image", "avatar.png", "image/png", bytes.toByteArray());
        ProfileImageService service = new ProfileImageService(repositoryFor(user()), (GridFsTemplate) null);

        assertThrows(IllegalStateException.class, () -> service.upload("owner@example.com", png));
    }

    @Test
    void declaredMimeMustMatchDecodedImageFormat() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MockMultipartFile mislabeled = new MockMultipartFile("image", "avatar.jpg", "image/jpeg", bytes.toByteArray());
        ProfileImageService service = new ProfileImageService(repositoryFor(user()), (GridFsTemplate) null);

        assertThrows(IllegalArgumentException.class, () -> service.upload("owner@example.com", mislabeled));
    }

    private User user() {
        User user = new User("Profile Owner", "owner@example.com", "hash", "USER");
        user.setId("user-1");
        return user;
    }

    private UserRepository repositoryFor(User user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) return Optional.of(user);
                    if (method.getName().equals("save")) return args[0];
                    if (method.getName().equals("toString")) return "test-user-repository";
                    if (method.getName().equals("hashCode")) return 1;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

}
