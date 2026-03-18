package com.expensetracker.dto.response;

import java.math.BigDecimal;

public class CategorySpendDto {

    private String categoryName;
    private BigDecimal totalAmount;
    private long expenseCount;

    public CategorySpendDto() {}

    public CategorySpendDto(String categoryName, BigDecimal totalAmount, long expenseCount) {
        this.categoryName = categoryName;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
    }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public long getExpenseCount() { return expenseCount; }
    public void setExpenseCount(long expenseCount) { this.expenseCount = expenseCount; }

    public static CategorySpendDtoBuilder builder() { return new CategorySpendDtoBuilder(); }

    public static class CategorySpendDtoBuilder {
        private String categoryName;
        private BigDecimal totalAmount;
        private long expenseCount;

        public CategorySpendDtoBuilder categoryName(String categoryName) { this.categoryName = categoryName; return this; }
        public CategorySpendDtoBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public CategorySpendDtoBuilder expenseCount(long expenseCount) { this.expenseCount = expenseCount; return this; }

        public CategorySpendDto build() {
            return new CategorySpendDto(categoryName, totalAmount, expenseCount);
        }
    }
}
