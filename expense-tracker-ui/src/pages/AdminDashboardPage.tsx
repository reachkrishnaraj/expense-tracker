import { useState } from 'react';
import { useAnalytics } from '../hooks/useAnalytics';
import { SummaryCards } from '../components/dashboard/SummaryCards';
import { CategoryBarChart } from '../components/dashboard/CategoryBarChart';
import { MonthlyTrendLineChart } from '../components/dashboard/MonthlyTrendLineChart';
import { TeamSpendTable } from '../components/dashboard/TeamSpendTable';
import { DateRangePicker } from '../components/dashboard/DateRangePicker';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export function AdminDashboardPage() {
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const params =
    startDate && endDate ? { startDate, endDate } : undefined;

  const { summary, categoryData, monthlyData, teamData, isLoading, error, refetch } =
    useAnalytics(params);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center">
        <p className="text-sm text-red-600">{error}</p>
        <button
          type="button"
          onClick={refetch}
          className="mt-3 text-sm font-medium text-red-700 underline hover:text-red-800"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Admin Dashboard</h1>
          <p className="mt-1 text-sm text-gray-500">
            Organization-wide expense analytics and insights.
          </p>
        </div>
        <DateRangePicker
          startDate={startDate}
          endDate={endDate}
          onStartDateChange={setStartDate}
          onEndDateChange={setEndDate}
        />
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="mb-6">
          <SummaryCards summary={summary} />
        </div>
      )}

      {/* Charts Grid */}
      <div className="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <CategoryBarChart data={categoryData} />
        <MonthlyTrendLineChart data={monthlyData} />
      </div>

      {/* Team Spend Table */}
      <TeamSpendTable data={teamData} />
    </div>
  );
}
