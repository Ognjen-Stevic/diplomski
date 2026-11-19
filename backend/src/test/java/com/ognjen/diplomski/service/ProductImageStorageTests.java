package com.ognjen.diplomski.service;

import com.ognjen.diplomski.storage.ProductImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductImageStorageTests {

    private final ProductImageStorage storage = new ProductImageStorage();

    @Test
    void storesAValidatedImageUnderAGeneratedName() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        String pictureUrl = storage.store(new MockMultipartFile(
                "image", "article.png", "image/png", png));

        assertTrue(pictureUrl.startsWith("products/"));
        assertTrue(pictureUrl.endsWith(".png"));
        Path storedFile = Path.of("uploads", pictureUrl).toAbsolutePath().normalize();
        assertTrue(Files.exists(storedFile));

        storage.deleteIfUploaded(pictureUrl);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void rejectsContentThatIsNotARealSupportedImage() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> storage.store(new MockMultipartFile(
                        "image", "fake.png", "image/png", "not-an-image".getBytes())));

        assertEquals(400, exception.getStatusCode().value());
    }
}
