import type { AuditEntry } from '../../types/expense';
import { formatDateTime } from '../../utils/formatDate';

interface AuditTimelineProps {
  entries: AuditEntry[];
}

function getActionColor(toStatus: string): string {
  switch (toStatus) {
    case 'SUBMITTED':
      return 'bg-blue-500';
    case 'APPROVED':
      return 'bg-green-500';
    case 'REJECTED':
      return 'bg-red-500';
    case 'DRAFT':
    default:
      return 'bg-gray-400';
  }
}

function getActionLabel(action: string): string {
  switch (action) {
    case 'CREATED':
      return 'Created';
    case 'UPDATED':
      return 'Updated';
    case 'SUBMITTED':
      return 'Submitted for approval';
    case 'APPROVED':
      return 'Approved';
    case 'REJECTED':
      return 'Rejected';
    default:
      return action;
  }
}

export function AuditTimeline({ entries }: AuditTimelineProps) {
  if (entries.length === 0) {
    return (
      <p className="text-sm text-gray-500 italic">No activity recorded.</p>
    );
  }

  const sortedEntries = [...entries].sort(
    (a, b) =>
      new Date(b.performedAt).getTime() - new Date(a.performedAt).getTime()
  );

  return (
    <div className="flow-root">
      <ul className="-mb-8">
        {sortedEntries.map((entry, idx) => (
          <li key={entry.id}>
            <div className="relative pb-8">
              {idx !== sortedEntries.length - 1 && (
                <span
                  className="absolute left-4 top-4 -ml-px h-full w-0.5 bg-gray-200"
                  aria-hidden="true"
                />
              )}
              <div className="relative flex space-x-3">
                <div>
                  <span
                    className={`flex h-8 w-8 items-center justify-center rounded-full ring-8 ring-white ${getActionColor(entry.toStatus)}`}
                  >
                    {entry.toStatus === 'APPROVED' ? (
                      <svg
                        className="h-4 w-4 text-white"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth={2}
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M4.5 12.75l6 6 9-13.5"
                        />
                      </svg>
                    ) : entry.toStatus === 'REJECTED' ? (
                      <svg
                        className="h-4 w-4 text-white"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth={2}
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M6 18L18 6M6 6l12 12"
                        />
                      </svg>
                    ) : (
                      <svg
                        className="h-4 w-4 text-white"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth={2}
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"
                        />
                      </svg>
                    )}
                  </span>
                </div>
                <div className="flex min-w-0 flex-1 justify-between space-x-4 pt-1.5">
                  <div>
                    <p className="text-sm text-gray-900">
                      {getActionLabel(entry.action)}{' '}
                      <span className="font-medium text-gray-600">
                        by {entry.performedByName}
                      </span>
                    </p>
                    {entry.comment && (
                      <p className="mt-1 text-sm text-gray-500 italic">
                        &ldquo;{entry.comment}&rdquo;
                      </p>
                    )}
                  </div>
                  <div className="whitespace-nowrap text-right text-xs text-gray-500">
                    {formatDateTime(entry.performedAt)}
                  </div>
                </div>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
