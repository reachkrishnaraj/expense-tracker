import { useState } from 'react';
import type { TeamSpend } from '../../types/analytics';

interface TeamSpendTableProps {
  data: TeamSpend[];
}

type SortKey = 'teamMember' | 'totalAmount' | 'expenseCount' | 'pendingCount';

export function TeamSpendTable({ data }: TeamSpendTableProps) {
  const [sortKey, setSortKey] = useState<SortKey>('totalAmount');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  };

  const sorted = [...data].sort((a, b) => {
    const aVal = a[sortKey];
    const bVal = b[sortKey];
    const multiplier = sortDir === 'asc' ? 1 : -1;

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return aVal.localeCompare(bVal) * multiplier;
    }
    return ((aVal as number) - (bVal as number)) * multiplier;
  });

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const SortIcon = ({ column }: { column: SortKey }) => {
    if (sortKey !== column) {
      return <span className="ml-1 text-gray-400">&#8597;</span>;
    }
    return (
      <span className="ml-1 text-indigo-600">
        {sortDir === 'asc' ? '\u2191' : '\u2193'}
      </span>
    );
  };

  if (data.length === 0) {
    return (
      <div className="flex h-32 items-center justify-center rounded-lg border border-gray-200 bg-white p-6">
        <p className="text-sm text-gray-500">No team data available.</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white">
      <div className="px-6 py-4">
        <h3 className="text-sm font-semibold text-gray-900">
          Team Spend Breakdown
        </h3>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th
                className="cursor-pointer px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 hover:text-gray-700"
                onClick={() => handleSort('teamMember')}
              >
                Team Member <SortIcon column="teamMember" />
              </th>
              <th
                className="cursor-pointer px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 hover:text-gray-700"
                onClick={() => handleSort('totalAmount')}
              >
                Total Spend <SortIcon column="totalAmount" />
              </th>
              <th
                className="cursor-pointer px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 hover:text-gray-700"
                onClick={() => handleSort('expenseCount')}
              >
                Expenses <SortIcon column="expenseCount" />
              </th>
              <th
                className="cursor-pointer px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 hover:text-gray-700"
                onClick={() => handleSort('pendingCount')}
              >
                Pending <SortIcon column="pendingCount" />
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {sorted.map((member) => (
              <tr key={member.email} className="hover:bg-gray-50">
                <td className="whitespace-nowrap px-6 py-3">
                  <div className="text-sm font-medium text-gray-900">
                    {member.teamMember}
                  </div>
                  <div className="text-xs text-gray-500">{member.email}</div>
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
  );
}
