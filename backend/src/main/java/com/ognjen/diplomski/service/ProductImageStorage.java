package com.ognjen.diplomski.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class ProductImageStorage {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final Path storageDirectory = Path.of("uploads", "products").toAbsolutePath().normalize();

    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) return "products/placeholder.webp";
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slika može imati najviše 5 MB.");
        }

        try {
            byte[] content = image.getBytes();
            String extension = detectExtension(content);
            Files.createDirectories(storageDirectory);
            String fileName = UUID.randomUUID() + extension;
            Path target = storageDirectory.resolve(fileName).normalize();
            if (!target.getParent().equals(storageDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Naziv slike nije ispravan.");
            }
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return "products/" + fileName;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Slika nije sačuvana.");
        }
    }

    public void deleteIfUploaded(String pictureUrl) {
        if (pictureUrl == null || "products/placeholder.webp".equals(pictureUrl)
                || !pictureUrl.startsWith("products/")) return;
        Path target = storageDirectory.resolve(pictureUrl.substring("products/".length())).normalize();
        if (!target.getParent().equals(storageDirectory)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // A failed cleanup must not hide the original database error.
        }
    }

    private static String detectExtension(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47) return ".png";
        if (content.length >= 3
                && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) return ".jpg";
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return ".webp";
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Slika mora biti u JPEG, PNG ili WebP formatu."
        );
    }
}
