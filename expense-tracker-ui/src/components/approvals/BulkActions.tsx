interface BulkActionsProps {
  selectedCount: number;
  onBulkApprove: () => void;
  onBulkReject: () => void;
  onClearSelection: () => void;
  isSubmitting: boolean;
}

export function BulkActions({
  selectedCount,
  onBulkApprove,
  onBulkReject,
  onClearSelection,
  isSubmitting,
}: BulkActionsProps) {
  if (selectedCount === 0) return null;

  return (
    <div className="fixed bottom-6 left-1/2 z-40 -translate-x-1/2 transform">
      <div className="flex items-center gap-4 rounded-lg bg-gray-900 px-6 py-3 shadow-2xl">
        <span className="text-sm font-medium text-white">
          {selectedCount} item{selectedCount !== 1 ? 's' : ''} selected
        </span>

        <div className="h-5 w-px bg-gray-600" />

        <button
          type="button"
          onClick={onBulkApprove}
          disabled={isSubmitting}
          className="rounded-md bg-green-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
        >
          Approve All
        </button>

        <button
          type="button"
          onClick={onBulkReject}
          disabled={isSubmitting}
          className="rounded-md bg-red-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
        >
          Reject All
        </button>

        <button
          type="button"
          onClick={onClearSelection}
          disabled={isSubmitting}
          className="rounded-md px-3 py-1.5 text-sm font-medium text-gray-300 hover:text-white disabled:opacity-50"
        >
          Clear
        </button>
      </div>
    </div>
  );
}
