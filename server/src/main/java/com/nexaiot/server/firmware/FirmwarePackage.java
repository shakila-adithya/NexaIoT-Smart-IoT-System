package com.nexaiot.server.firmware;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "firmware_packages")
public class FirmwarePackage {
    @Id
    private String id;
    private String version;
    private List<String> compatibleDeviceTypes;
    private String originalFilename;
    private String contentType;
    private long size;
    private String sha256;
    private String storageId;
    private Instant uploadedAt;
    private String uploadedBy;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getVersion() { return version; }
    public void setVersion(String value) { version = value; }
    public List<String> getCompatibleDeviceTypes() { return compatibleDeviceTypes; }
    public void setCompatibleDeviceTypes(List<String> value) { compatibleDeviceTypes = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getSize() { return size; }
    public void setSize(long value) { size = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public String getStorageId() { return storageId; }
    public void setStorageId(String value) { storageId = value; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant value) { uploadedAt = value; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String value) { uploadedBy = value; }
}
