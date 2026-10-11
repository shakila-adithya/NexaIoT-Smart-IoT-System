package com.nexaiot.server.service;

import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.exception.UserNotFoundException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ProfileImageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final UserRepository userRepository;
    private final GridFsTemplate gridFsTemplate;

    @Autowired
    public ProfileImageService(UserRepository userRepository, ObjectProvider<GridFsTemplate> gridFsTemplateProvider) {
        this.userRepository = userRepository;
        this.gridFsTemplate = gridFsTemplateProvider.getIfAvailable();
    }

    public ProfileImageService(UserRepository userRepository, GridFsTemplate gridFsTemplate) {
        this.userRepository = userRepository;
        this.gridFsTemplate = gridFsTemplate;
    }

    public UserResponse upload(String email, MultipartFile image) {
        validate(image);
        requireGridFs();
        User user = findUser(email);
        String previousImageId = user.getProfileImageId();
        ObjectId newImageId;

        try (InputStream inputStream = image.getInputStream()) {
            Document metadata = new Document("contentType", image.getContentType())
                    .append("ownerEmail", user.getEmail());
            newImageId = gridFsTemplate.store(inputStream, "profile-image", image.getContentType(), metadata);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the profile image");
        }

        try {
            user.setProfileImageId(newImageId.toHexString());
            User savedUser = userRepository.save(user);
            deleteGridFsImage(previousImageId);
            return UserResponse.from(savedUser);
        } catch (RuntimeException exception) {
            deleteGridFsImage(newImageId.toHexString());
            throw exception;
        }
    }

    public ProfileImageData get(String email) {
        User user = findUser(email);
        String imageId = user.getProfileImageId();
        if (imageId == null || imageId.isBlank()) return null;

        ObjectId objectId = parseObjectId(imageId);
        requireGridFs();
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(objectId)));
        if (file == null) return null;

        GridFsResource resource = gridFsTemplate.getResource(file);
        String contentType = file.getMetadata() == null ? null : file.getMetadata().getString("contentType");
        return new ProfileImageData(resource, contentType);
    }

    public UserResponse delete(String email) {
        User user = findUser(email);
        String imageId = user.getProfileImageId();
        user.setProfileImageId(null);
        User savedUser = userRepository.save(user);
        deleteGridFsImage(imageId);
        return UserResponse.from(savedUser);
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Please select a profile image");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Profile image must be 5 MB or smaller");
        }

        String contentType = image.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Only JPEG and PNG images are supported");
        }

        try (InputStream inputStream = image.getInputStream()) {
            BufferedImage decodedImage = ImageIO.read(inputStream);
            if (decodedImage == null) {
                throw new IllegalArgumentException("The selected file is not a valid image");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The selected file could not be read");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private ObjectId parseObjectId(String imageId) {
        try {
            return new ObjectId(imageId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Profile image is unavailable");
        }
    }

    private void deleteGridFsImage(String imageId) {
        if (imageId == null || imageId.isBlank() || gridFsTemplate == null) return;
        try {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(imageId))));
        } catch (IllegalArgumentException ignored) {
            // Do not fail a profile update because an old orphaned id is malformed.
        }
    }

    private void requireGridFs() {
        if (gridFsTemplate == null) {
            throw new IllegalStateException("Profile image storage is unavailable");
        }
    }

    public record ProfileImageData(Resource resource, String contentType) {
    }
}
