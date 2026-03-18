package com.expensetracker.service;

import com.expensetracker.dto.response.CategoryDto;
import com.expensetracker.model.ExpenseCategory;
import com.expensetracker.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ExpenseCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDto> listActive(UUID tenantId) {
        return categoryRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .map(CategoryDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryDto create(UUID tenantId, String name) {
        if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category with name '" + name + "' already exists");
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .tenantId(tenantId)
                .name(name)
                .isActive(true)
                .build();

        category = categoryRepository.save(category);
        return CategoryDto.from(category);
    }

    @Transactional
    public CategoryDto rename(UUID tenantId, UUID categoryId, String newName) {
        ExpenseCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Category not found"));

        if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category with name '" + newName + "' already exists");
        }

        category.setName(newName);
        category = categoryRepository.save(category);
        return CategoryDto.from(category);
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID categoryId) {
        ExpenseCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Category not found"));

        category.setIsActive(false);
        categoryRepository.save(category);
    }
}
