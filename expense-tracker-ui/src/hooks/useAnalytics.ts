import { useState, useEffect, useCallback } from 'react';
import { analyticsApi, type AnalyticsParams } from '../api/analyticsApi';
import type {
  AnalyticsSummary,
  CategorySpend,
  MonthlySpend,
  TeamSpend,
  TeamSummary,
} from '../types/analytics';

interface UseAnalyticsReturn {
  summary: AnalyticsSummary | null;
  categoryData: CategorySpend[];
  monthlyData: MonthlySpend[];
  teamData: TeamSpend[];
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useAnalytics(params?: AnalyticsParams): UseAnalyticsReturn {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [categoryData, setCategoryData] = useState<CategorySpend[]>([]);
  const [monthlyData, setMonthlyData] = useState<MonthlySpend[]>([]);
  const [teamData, setTeamData] = useState<TeamSpend[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const [summaryRes, categoryRes, monthlyRes, teamRes] = await Promise.all([
        analyticsApi.getSummary(params),
        analyticsApi.getByCategory(params),
        analyticsApi.getByMonth(params),
        analyticsApi.getByTeam(params),
      ]);

      setSummary(summaryRes);
      setCategoryData(categoryRes);
      setMonthlyData(monthlyRes);
      setTeamData(teamRes);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to load analytics data';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [params?.startDate, params?.endDate]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { summary, categoryData, monthlyData, teamData, isLoading, error, refetch: fetchData };
}

interface UseTeamAnalyticsReturn {
  summary: TeamSummary | null;
  members: TeamSpend[];
  categoryBreakdown: CategorySpend[];
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useTeamAnalytics(params?: AnalyticsParams): UseTeamAnalyticsReturn {
  const [summary, setSummary] = useState<TeamSummary | null>(null);
  const [members, setMembers] = useState<TeamSpend[]>([]);
  const [categoryBreakdown, setCategoryBreakdown] = useState<CategorySpend[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const data = await analyticsApi.getMyTeam(params);
      setSummary(data.summary);
      setMembers(data.members);
      setCategoryBreakdown(data.categoryBreakdown);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to load team data';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [params?.startDate, params?.endDate]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { summary, members, categoryBreakdown, isLoading, error, refetch: fetchData };
}
