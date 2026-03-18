package com.expensetracker.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Value("${app.uploads.base-dir:./uploads}")
    private String baseDirPath;

    @PostConstruct
    public void init() throws IOException {
        Path baseDir = Paths.get(baseDirPath).toAbsolutePath().normalize();
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
            log.info("Created uploads base directory: {}", baseDir);
        } else {
            log.info("Uploads base directory already exists: {}", baseDir);
        }
    }
}
