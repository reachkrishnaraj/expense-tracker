import axiosInstance from './axiosInstance';
import type {
  AnalyticsSummary,
  CategorySpend,
  MonthlySpend,
  TeamSpend,
  TeamSummary,
} from '../types/analytics';

export interface AnalyticsParams {
  startDate?: string;
  endDate?: string;
}

export const analyticsApi = {
  getSummary(params?: AnalyticsParams): Promise<AnalyticsSummary> {
    const p = params ? { fromDate: params.startDate, toDate: params.endDate } : {};
    return axiosInstance
      .get<AnalyticsSummary>('/analytics/summary', { params: p })
      .then((res) => res.data);
  },

  getByCategory(params?: AnalyticsParams): Promise<CategorySpend[]> {
    const p = params ? { fromDate: params.startDate, toDate: params.endDate } : {};
    return axiosInstance
      .get('/analytics/by-category', { params: p })
      .then((res) => {
        const data = res.data as any[];
        return data.map((d: any) => ({
          category: d.categoryName ?? d.category ?? 'Unknown',
          amount: d.totalAmount ?? d.amount ?? 0,
          count: d.expenseCount ?? d.count ?? 0,
        }));
      });
  },

  getByMonth(params?: AnalyticsParams): Promise<MonthlySpend[]> {
    const p = params ? { fromDate: params.startDate, toDate: params.endDate } : { months: 6 };
    return axiosInstance
      .get('/analytics/by-month', { params: p })
      .then((res) => {
        const data = res.data as any[];
        return data.map((d: any) => ({
          month: d.month,
          amount: d.totalAmount ?? d.amount ?? 0,
          count: d.expenseCount ?? d.count ?? 0,
        }));
      });
  },

  getByTeam(params?: AnalyticsParams): Promise<TeamSpend[]> {
    const p = params ? { fromDate: params.startDate, toDate: params.endDate } : {};
    return axiosInstance
      .get('/analytics/by-team', { params: p })
      .then((res) => {
        const data = res.data as any[];
        return data.map((d: any, idx: number) => ({
          teamMember: d.managerName ?? d.teamMember ?? `Manager ${idx + 1}`,
          email: d.managerId ?? d.email ?? `manager-${idx}`,
          totalAmount: d.totalAmount ?? 0,
          expenseCount: d.expenseCount ?? 0,
          pendingCount: d.pendingCount ?? 0,
        }));
      });
  },

  getMyTeam(params?: AnalyticsParams): Promise<{
    summary: TeamSummary;
    members: TeamSpend[];
    categoryBreakdown: CategorySpend[];
  }> {
    const p = params ? { fromDate: params.startDate, toDate: params.endDate } : {};
    return axiosInstance
      .get('/analytics/my-team', { params: p })
      .then((res) => {
        const data = res.data;
        // my-team returns CategorySpend[] directly (category breakdown for manager's team)
        if (Array.isArray(data)) {
          return {
            summary: { totalMembers: 0, totalSpend: 0, averagePerMember: 0, topCategory: '', currency: 'USD' },
            members: [],
            categoryBreakdown: data.map((d: any) => ({
              category: d.categoryName ?? d.category ?? 'Unknown',
              amount: d.totalAmount ?? d.amount ?? 0,
              count: d.expenseCount ?? d.count ?? 0,
            })),
          };
        }
        return data;
      });
  },
};
