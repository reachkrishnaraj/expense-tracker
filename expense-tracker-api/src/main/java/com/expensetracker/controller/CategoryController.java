package com.expensetracker.controller;

import com.expensetracker.dto.request.CreateCategoryRequest;
import com.expensetracker.dto.request.RenameCategoryRequest;
import com.expensetracker.dto.response.CategoryDto;
import com.expensetracker.security.SecurityUtils;
import com.expensetracker.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listCategories() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<CategoryDto> categories = categoryService.listActive(tenantId);
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CategoryDto category = categoryService.create(tenantId, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> renameCategory(@PathVariable UUID id,
                                                       @Valid @RequestBody RenameCategoryRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CategoryDto category = categoryService.rename(tenantId, id, request.getName());
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        categoryService.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
