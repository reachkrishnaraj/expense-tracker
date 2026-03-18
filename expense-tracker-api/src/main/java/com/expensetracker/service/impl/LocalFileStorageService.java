package com.expensetracker.service.impl;

import com.expensetracker.exception.FileStorageException;
import com.expensetracker.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path baseDir;

    public LocalFileStorageService(@Value("${app.uploads.base-dir:./uploads}") String baseDirPath) {
        this.baseDir = Paths.get(baseDirPath).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID tenantId, UUID expenseId, MultipartFile file) {
        try {
            Path targetDir = baseDir.resolve(tenantId.toString()).resolve(expenseId.toString());
            Files.createDirectories(targetDir);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String storedFilename = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(storedFilename).normalize();

            // Path traversal prevention
            if (!targetPath.startsWith(baseDir)) {
                throw new FileStorageException("Path traversal attempt detected", null);
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path from baseDir
            String relativePath = tenantId + "/" + expenseId + "/" + storedFilename;
            log.info("Stored file at: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file", e);
        }
    }

    @Override
    public Resource load(String storagePath) {
        Path filePath = baseDir.resolve(storagePath).normalize();

        // Path traversal prevention
        if (!filePath.startsWith(baseDir)) {
            throw new FileStorageException("Path traversal attempt detected", null);
        }

        if (!Files.exists(filePath)) {
            throw new FileStorageException("File not found: " + storagePath, null);
        }

        return new FileSystemResource(filePath);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path filePath = baseDir.resolve(storagePath).normalize();

            // Path traversal prevention
            if (!filePath.startsWith(baseDir)) {
                throw new FileStorageException("Path traversal attempt detected", null);
            }

            Files.deleteIfExists(filePath);
            log.info("Deleted file at: {}", storagePath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file: " + storagePath, e);
        }
    }

    @Override
    public void deleteAllForExpense(UUID tenantId, UUID expenseId) {
        try {
            Path expenseDir = baseDir.resolve(tenantId.toString()).resolve(expenseId.toString()).normalize();

            // Path traversal prevention
            if (!expenseDir.startsWith(baseDir)) {
                throw new FileStorageException("Path traversal attempt detected", null);
            }

            if (Files.exists(expenseDir)) {
                try (Stream<Path> paths = Files.walk(expenseDir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete: {}", path, e);
                                }
                            });
                }
                log.info("Deleted all files for expense {} in tenant {}", expenseId, tenantId);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete files for expense: " + expenseId, e);
        }
    }
}
