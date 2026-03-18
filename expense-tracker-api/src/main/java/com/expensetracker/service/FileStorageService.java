package com.expensetracker.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileStorageService {

    String store(UUID tenantId, UUID expenseId, MultipartFile file);

    Resource load(String storagePath);

    void delete(String storagePath);

    void deleteAllForExpense(UUID tenantId, UUID expenseId);
}
