package com.nexaiot.server.controller;

import com.nexaiot.server.dto.ChangePasswordRequest;
import com.nexaiot.server.dto.UpdatePreferencesRequest;
import com.nexaiot.server.dto.UpdateProfileRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.service.UserService;
import com.nexaiot.server.service.ProfileImageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;

    public UserController(UserService userService, ProfileImageService profileImageService) {
        this.userService = userService;
        this.profileImageService = profileImageService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {

        return userService.getCurrentUser(authentication.getName());
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/me/preferences")
    public UserResponse updatePreferences(
            Authentication authentication,
            @RequestBody UpdatePreferencesRequest request
    ) {
        return userService.updatePreferences(authentication.getName(), request);
    }

    @PostMapping("/me/change-password")
    public UserResponse changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return userService.changePassword(authentication.getName(), request);
    }

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadProfileImage(
            Authentication authentication,
            @RequestPart("image") MultipartFile image
    ) {
        return profileImageService.upload(authentication.getName(), image);
    }

    @GetMapping("/me/profile-image")
    public ResponseEntity<Resource> getProfileImage(Authentication authentication) {
        ProfileImageService.ProfileImageData image = profileImageService.get(authentication.getName());
        if (image == null) return ResponseEntity.notFound().build();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (image.contentType() != null) mediaType = MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok().contentType(mediaType).body(image.resource());
    }

    @DeleteMapping("/me/profile-image")
    public UserResponse deleteProfileImage(Authentication authentication) {
        return profileImageService.delete(authentication.getName());
    }
}
