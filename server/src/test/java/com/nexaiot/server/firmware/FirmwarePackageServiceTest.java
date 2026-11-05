package com.nexaiot.server.firmware;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

class FirmwarePackageServiceTest {
    @Test
    void rejectsUnsafeNamesUnsupportedTypesAndMissingDeviceTypes() {
        FirmwarePackageService service = new FirmwarePackageService(repository(), provider());
        assertThrows(IllegalArgumentException.class, () -> service.upload("admin@example.com", file("../firmware.bin", "application/octet-stream"), "1.0.0", "temperature-sensor"));
        assertThrows(IllegalArgumentException.class, () -> service.upload("admin@example.com", file("firmware.exe", "application/octet-stream"), "1.0.0", "temperature-sensor"));
        assertThrows(IllegalArgumentException.class, () -> service.upload("admin@example.com", file("firmware.bin", "application/octet-stream"), "1.0.0", ""));
    }

    private MockMultipartFile file(String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, new byte[]{1, 2, 3});
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<GridFsTemplate> provider() {
        return (ObjectProvider<GridFsTemplate>) Proxy.newProxyInstance(ObjectProvider.class.getClassLoader(), new Class[]{ObjectProvider.class}, (proxy, method, args) -> null);
    }

    private FirmwarePackageRepository repository() {
        return (FirmwarePackageRepository) Proxy.newProxyInstance(FirmwarePackageRepository.class.getClassLoader(), new Class[]{FirmwarePackageRepository.class}, (proxy, method, args) -> switch (method.getName()) {
            case "findAllByOrderByUploadedAtDesc" -> List.of();
            case "toString" -> "firmware";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
