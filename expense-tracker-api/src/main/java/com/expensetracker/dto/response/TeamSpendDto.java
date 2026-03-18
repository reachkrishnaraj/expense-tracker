package com.expensetracker.dto.response;

import java.math.BigDecimal;

public class TeamSpendDto {

    private String managerName;
    private BigDecimal totalAmount;
    private long expenseCount;

    public TeamSpendDto() {}

    public TeamSpendDto(String managerName, BigDecimal totalAmount, long expenseCount) {
        this.managerName = managerName;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
    }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public long getExpenseCount() { return expenseCount; }
    public void setExpenseCount(long expenseCount) { this.expenseCount = expenseCount; }

    public static TeamSpendDtoBuilder builder() { return new TeamSpendDtoBuilder(); }

    public static class TeamSpendDtoBuilder {
        private String managerName;
        private BigDecimal totalAmount;
        private long expenseCount;

        public TeamSpendDtoBuilder managerName(String managerName) { this.managerName = managerName; return this; }
        public TeamSpendDtoBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public TeamSpendDtoBuilder expenseCount(long expenseCount) { this.expenseCount = expenseCount; return this; }

        public TeamSpendDto build() {
            return new TeamSpendDto(managerName, totalAmount, expenseCount);
        }
    }
}
