package com.nexaiot.server.firmware;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nexaiot.server.firmware.dto.FirmwarePackageResponse;

import java.security.DigestInputStream;

@Service
public class FirmwarePackageService {
    private static final long MAX_SIZE = 50L * 1024L * 1024L;
    private static final List<String> EXTENSIONS = List.of(".bin", ".hex", ".uf2", ".zip");
    private static final List<String> CONTENT_TYPES = List.of("application/octet-stream", "application/zip", "application/x-zip-compressed");

    private final FirmwarePackageRepository repository;
    private final GridFsTemplate gridFsTemplate;

    public FirmwarePackageService(FirmwarePackageRepository repository, ObjectProvider<GridFsTemplate> gridFsProvider) {
        this.repository = repository;
        this.gridFsTemplate = gridFsProvider.getIfAvailable();
    }

    public List<FirmwarePackageResponse> list() {
        return repository.findAllByOrderByUploadedAtDesc().stream().map(this::response).toList();
    }

    public FirmwarePackageResponse upload(String email, MultipartFile file, String version, String compatibleDeviceTypes) {
        validate(version, compatibleDeviceTypes, file);
        if (gridFsTemplate == null) throw new IllegalStateException("Firmware storage is unavailable");
        String filename = safeFilename(file.getOriginalFilename());
        List<String> deviceTypes = parseDeviceTypes(compatibleDeviceTypes);
        String contentType = normalizeContentType(file.getContentType());
        MessageDigest digest = digest();
        ObjectId storageId;
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            Document metadata = new Document("version", version.trim())
                    .append("uploadedBy", email)
                    .append("contentType", contentType)
                    .append("compatibleDeviceTypes", deviceTypes);
            storageId = gridFsTemplate.store(input, filename, contentType, metadata);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the firmware package");
        }
        FirmwarePackage firmware = new FirmwarePackage();
        firmware.setVersion(version.trim());
        firmware.setCompatibleDeviceTypes(deviceTypes);
        firmware.setOriginalFilename(filename);
        firmware.setContentType(contentType);
        firmware.setSize(file.getSize());
        firmware.setSha256(HexFormat.of().formatHex(digest.digest()));
        firmware.setStorageId(storageId.toHexString());
        firmware.setUploadedAt(Instant.now());
        firmware.setUploadedBy(email);
        try {
            return response(repository.save(firmware));
        } catch (RuntimeException exception) {
            gridFsTemplate.delete(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(storageId)));
            throw exception;
        }
    }

    private void validate(String version, String compatibleDeviceTypes, MultipartFile file) {
        if (version == null || version.isBlank() || version.trim().length() > 80 || version.contains("/") || version.contains("\\")) throw new IllegalArgumentException("Firmware version is required and must be a safe value");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please select a firmware package");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("Firmware package must be 50 MB or smaller");
        String filename = safeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        if (EXTENSIONS.stream().noneMatch(filename.toLowerCase(Locale.ROOT)::endsWith) || !CONTENT_TYPES.contains(contentType)) throw new IllegalArgumentException("Only BIN, HEX, UF2, and ZIP firmware packages are supported");
        parseDeviceTypes(compatibleDeviceTypes);
    }

    private List<String> parseDeviceTypes(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("At least one compatible device type is required");
        List<String> types = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (types.isEmpty() || types.size() > 20 || types.stream().anyMatch(item -> item.length() > 80 || item.contains("/") || item.contains("\\"))) throw new IllegalArgumentException("Compatible device types are invalid");
        return types;
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Firmware filename is invalid");
        return value;
    }

    private String normalizeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.toLowerCase(Locale.ROOT).trim();
    }

    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private FirmwarePackageResponse response(FirmwarePackage firmware) {
        return new FirmwarePackageResponse(firmware.getId(), firmware.getVersion(), firmware.getCompatibleDeviceTypes(), firmware.getOriginalFilename(), firmware.getContentType(), firmware.getSize(), firmware.getSha256(), firmware.getUploadedAt(), firmware.getUploadedBy());
    }
}
