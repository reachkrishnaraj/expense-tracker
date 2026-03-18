import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { useTeamAnalytics } from '../hooks/useAnalytics';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export function TeamStatsPage() {
  const { summary, members, categoryBreakdown, isLoading, error, refetch } =
    useTeamAnalytics();

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: summary?.currency || 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  };

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
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Team Stats</h1>
        <p className="mt-1 text-sm text-gray-500">
          Overview of your team's expense activity and spend.
        </p>
      </div>

      {/* Summary Stats */}
      {summary && (
        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-lg border border-blue-200 bg-blue-50 p-5">
            <p className="text-sm font-medium text-gray-600">Team Members</p>
            <p className="mt-1 text-2xl font-bold text-blue-600">
              {summary.totalMembers}
            </p>
          </div>
          <div className="rounded-lg border border-green-200 bg-green-50 p-5">
            <p className="text-sm font-medium text-gray-600">Total Spend</p>
            <p className="mt-1 text-2xl font-bold text-green-600">
              {formatCurrency(summary.totalSpend)}
            </p>
          </div>
          <div className="rounded-lg border border-purple-200 bg-purple-50 p-5">
            <p className="text-sm font-medium text-gray-600">Avg Per Member</p>
            <p className="mt-1 text-2xl font-bold text-purple-600">
              {formatCurrency(summary.averagePerMember)}
            </p>
          </div>
          <div className="rounded-lg border border-indigo-200 bg-indigo-50 p-5">
            <p className="text-sm font-medium text-gray-600">Top Category</p>
            <p className="mt-1 text-2xl font-bold text-indigo-600">
              {summary.topCategory || '--'}
            </p>
          </div>
        </div>
      )}

      {/* Category Bar Chart */}
      {categoryBreakdown.length > 0 && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-sm font-semibold text-gray-900">
            Team Spend by Category
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={categoryBreakdown}
              margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
            >
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="category" fontSize={12} />
              <YAxis tickFormatter={(v: number) => formatCurrency(v)} fontSize={12} />
              <Tooltip
                formatter={(value: number) => [
                  formatCurrency(value),
                  'Amount',
                ]}
                contentStyle={{
                  borderRadius: '8px',
                  border: '1px solid #e5e7eb',
                  boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                }}
              />
              <Bar
                dataKey="amount"
                fill="#6366f1"
                radius={[4, 4, 0, 0]}
                barSize={40}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Team Members Table */}
      {members.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="px-6 py-4">
            <h3 className="text-sm font-semibold text-gray-900">
              Team Members
            </h3>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Member
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                    Total Spend
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                    Expenses
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                    Pending
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {members.map((member) => (
                  <tr key={member.email} className="hover:bg-gray-50">
                    <td className="whitespace-nowrap px-6 py-3">
                      <div className="text-sm font-medium text-gray-900">
                        {member.teamMember}
                      </div>
                      <div className="text-xs text-gray-500">
                        {member.email}
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-6 py-3 text-right text-sm font-semibold text-gray-900">
                      {formatCurrency(member.totalAmount)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3 text-right text-sm text-gray-600">
                      {member.expenseCount}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3 text-right">
                      {member.pendingCount > 0 ? (
                        <span className="inline-flex rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800">
                          {member.pendingCount}
                        </span>
                      ) : (
                        <span className="text-sm text-gray-400">0</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!summary && members.length === 0 && categoryBreakdown.length === 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
          <p className="text-sm text-gray-500">
            No team data available yet.
          </p>
        </div>
      )}
    </div>
  );
}
