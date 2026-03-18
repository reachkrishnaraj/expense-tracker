package com.expensetracker.dto.response;

import java.math.BigDecimal;

public class AnalyticsSummaryDto {

    private long totalSubmitted;
    private long totalApproved;
    private long totalRejected;
    private long totalPending;
    private BigDecimal totalApprovedAmount;
    private String currency;

    public AnalyticsSummaryDto() {}

    public AnalyticsSummaryDto(long totalSubmitted, long totalApproved, long totalRejected,
                                long totalPending, BigDecimal totalApprovedAmount, String currency) {
        this.totalSubmitted = totalSubmitted;
        this.totalApproved = totalApproved;
        this.totalRejected = totalRejected;
        this.totalPending = totalPending;
        this.totalApprovedAmount = totalApprovedAmount;
        this.currency = currency;
    }

    public long getTotalSubmitted() { return totalSubmitted; }
    public void setTotalSubmitted(long totalSubmitted) { this.totalSubmitted = totalSubmitted; }
    public long getTotalApproved() { return totalApproved; }
    public void setTotalApproved(long totalApproved) { this.totalApproved = totalApproved; }
    public long getTotalRejected() { return totalRejected; }
    public void setTotalRejected(long totalRejected) { this.totalRejected = totalRejected; }
    public long getTotalPending() { return totalPending; }
    public void setTotalPending(long totalPending) { this.totalPending = totalPending; }
    public BigDecimal getTotalApprovedAmount() { return totalApprovedAmount; }
    public void setTotalApprovedAmount(BigDecimal totalApprovedAmount) { this.totalApprovedAmount = totalApprovedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public static AnalyticsSummaryDtoBuilder builder() { return new AnalyticsSummaryDtoBuilder(); }

    public static class AnalyticsSummaryDtoBuilder {
        private long totalSubmitted;
        private long totalApproved;
        private long totalRejected;
        private long totalPending;
        private BigDecimal totalApprovedAmount;
        private String currency;

        public AnalyticsSummaryDtoBuilder totalSubmitted(long totalSubmitted) { this.totalSubmitted = totalSubmitted; return this; }
        public AnalyticsSummaryDtoBuilder totalApproved(long totalApproved) { this.totalApproved = totalApproved; return this; }
        public AnalyticsSummaryDtoBuilder totalRejected(long totalRejected) { this.totalRejected = totalRejected; return this; }
        public AnalyticsSummaryDtoBuilder totalPending(long totalPending) { this.totalPending = totalPending; return this; }
        public AnalyticsSummaryDtoBuilder totalApprovedAmount(BigDecimal totalApprovedAmount) { this.totalApprovedAmount = totalApprovedAmount; return this; }
        public AnalyticsSummaryDtoBuilder currency(String currency) { this.currency = currency; return this; }

        public AnalyticsSummaryDto build() {
            return new AnalyticsSummaryDto(totalSubmitted, totalApproved, totalRejected, totalPending, totalApprovedAmount, currency);
        }
    }
}
