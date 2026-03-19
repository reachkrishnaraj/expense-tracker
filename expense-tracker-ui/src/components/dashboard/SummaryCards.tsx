import type { AnalyticsSummary } from '../../types/analytics';

interface SummaryCardsProps {
  summary: AnalyticsSummary;
}

interface CardConfig {
  label: string;
  value: string;
  color: string;
  bgColor: string;
  icon: JSX.Element;
}

export function SummaryCards({ summary }: SummaryCardsProps) {
  if (!summary) return null;

  const pending = summary.totalPending ?? summary.pendingCount ?? 0;
  const approved = summary.totalApprovedAmount ?? summary.approvedAmount ?? 0;
  const rejected = summary.totalRejected ?? summary.rejectedCount ?? 0;
  const thisMonth = summary.thisMonthAmount ?? summary.totalApprovedAmount ?? 0;

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: summary.currency || 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const cards: CardConfig[] = [
    {
      label: 'Pending Approvals',
      value: pending.toString(),
      color: 'text-blue-600',
      bgColor: 'bg-blue-50 border-blue-200',
      icon: (
        <svg className="h-6 w-6 text-blue-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
    },
    {
      label: 'Total Approved',
      value: formatCurrency(approved),
      color: 'text-green-600',
      bgColor: 'bg-green-50 border-green-200',
      icon: (
        <svg className="h-6 w-6 text-green-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
    },
    {
      label: 'Rejected',
      value: rejected.toString(),
      color: 'text-red-600',
      bgColor: 'bg-red-50 border-red-200',
      icon: (
        <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
    },
    {
      label: 'This Month',
      value: formatCurrency(thisMonth),
      color: 'text-purple-600',
      bgColor: 'bg-purple-50 border-purple-200',
      icon: (
        <svg className="h-6 w-6 text-purple-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
        </svg>
      ),
    },
  ];

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <div
          key={card.label}
          className={`rounded-lg border p-5 ${card.bgColor}`}
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">{card.label}</p>
              <p className={`mt-1 text-2xl font-bold ${card.color}`}>
                {card.value}
              </p>
            </div>
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white shadow-sm">
              {card.icon}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
