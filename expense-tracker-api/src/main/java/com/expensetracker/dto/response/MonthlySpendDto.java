package com.expensetracker.dto.response;

import java.math.BigDecimal;

public class MonthlySpendDto {

    private String month;
    private BigDecimal totalAmount;
    private long expenseCount;

    public MonthlySpendDto() {}

    public MonthlySpendDto(String month, BigDecimal totalAmount, long expenseCount) {
        this.month = month;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public long getExpenseCount() { return expenseCount; }
    public void setExpenseCount(long expenseCount) { this.expenseCount = expenseCount; }

    public static MonthlySpendDtoBuilder builder() { return new MonthlySpendDtoBuilder(); }

    public static class MonthlySpendDtoBuilder {
        private String month;
        private BigDecimal totalAmount;
        private long expenseCount;

        public MonthlySpendDtoBuilder month(String month) { this.month = month; return this; }
        public MonthlySpendDtoBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public MonthlySpendDtoBuilder expenseCount(long expenseCount) { this.expenseCount = expenseCount; return this; }

        public MonthlySpendDto build() {
            return new MonthlySpendDto(month, totalAmount, expenseCount);
        }
    }
}
