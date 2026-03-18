export interface AnalyticsSummary {
  pendingCount: number;
  approvedAmount: number;
  rejectedCount: number;
  thisMonthAmount: number;
  currency: string;
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
