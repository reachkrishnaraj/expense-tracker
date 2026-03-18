package com.expensetracker.dto.response;

import com.expensetracker.model.ExpenseCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {

    private UUID id;
    private String name;
    private Boolean isActive;

    public static CategoryDto from(ExpenseCategory category) {
        if (category == null) {
            return null;
        }
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .isActive(category.getIsActive())
                .build();
    }
}
