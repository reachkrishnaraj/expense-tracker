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
    return axiosInstance
      .get<AnalyticsSummary>('/analytics/summary', { params })
      .then((res) => res.data);
  },

  getByCategory(params?: AnalyticsParams): Promise<CategorySpend[]> {
    return axiosInstance
      .get<CategorySpend[]>('/analytics/by-category', { params })
      .then((res) => res.data);
  },

  getByMonth(params?: AnalyticsParams): Promise<MonthlySpend[]> {
    return axiosInstance
      .get<MonthlySpend[]>('/analytics/by-month', { params })
      .then((res) => res.data);
  },

  getByTeam(params?: AnalyticsParams): Promise<TeamSpend[]> {
    return axiosInstance
      .get<TeamSpend[]>('/analytics/by-team', { params })
      .then((res) => res.data);
  },

  getMyTeam(params?: AnalyticsParams): Promise<{
    summary: TeamSummary;
    members: TeamSpend[];
    categoryBreakdown: CategorySpend[];
  }> {
    return axiosInstance
      .get('/analytics/my-team', { params })
      .then((res) => res.data);
  },
};
