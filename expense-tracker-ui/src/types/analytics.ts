export interface AnalyticsSummary {
  totalPending: number;
  totalApproved: number;
  totalRejected: number;
  totalSubmitted: number;
  totalApprovedAmount: number;
  currency: string;
  // aliases from different response shapes
  pendingCount?: number;
  approvedAmount?: number;
  rejectedCount?: number;
  thisMonthAmount?: number;
}

export interface CategorySpend {
  category: string;
  amount: number;
  count: number;
}

export interface MonthlySpend {
  month: string;
  amount: number;
  count: number;
}

export interface TeamSpend {
  teamMember: string;
  email: string;
  totalAmount: number;
  expenseCount: number;
  pendingCount: number;
}

export interface TeamSummary {
  totalMembers: number;
  totalSpend: number;
  averagePerMember: number;
  topCategory: string;
  currency: string;
}
