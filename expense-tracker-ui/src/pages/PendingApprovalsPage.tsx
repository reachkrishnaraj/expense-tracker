import { useState, useEffect, useCallback } from 'react';
import { approvalApi } from '../api/approvalApi';
import type { PendingExpense } from '../types/approval';
import type { PaginatedResponse } from '../types/common';
import { ApprovalTable } from '../components/approvals/ApprovalTable';
import { BulkActions } from '../components/approvals/BulkActions';
import { RejectModal } from '../components/approvals/RejectModal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useToast } from '../components/common/Toast';

export function PendingApprovalsPage() {
  const { addToast } = useToast();
  const [data, setData] = useState<PaginatedResponse<PendingExpense> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [page, setPage] = useState(0);

  // Reject modal state
  const [rejectTarget, setRejectTarget] = useState<{
    type: 'single' | 'bulk';
    id?: string;
  } | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await approvalApi.getPendingApprovals({
        page,
        size: 20,
      });
      setData(result);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to load pending approvals';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleApprove = async (id: string) => {
    setIsSubmitting(true);
    try {
      await approvalApi.approveExpense(id);
      addToast('Expense approved successfully', 'success');
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
      await fetchData();
    } catch {
      addToast('Failed to approve expense', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = (id: string) => {
    setRejectTarget({ type: 'single', id });
  };

  const handleRejectConfirm = async (reason: string) => {
    if (!rejectTarget) return;

    setIsSubmitting(true);
    try {
      if (rejectTarget.type === 'single' && rejectTarget.id) {
        await approvalApi.rejectExpense(rejectTarget.id, reason);
        addToast('Expense rejected', 'success');
        setSelectedIds((prev) => {
          const next = new Set(prev);
          next.delete(rejectTarget.id!);
          return next;
        });
      } else if (rejectTarget.type === 'bulk') {
        const result = await approvalApi.bulkAction({
          expenseIds: Array.from(selectedIds),
          action: 'REJECT',
          comment: reason,
        });
        const successCount = result.successful.length;
        const failCount = result.failed.length;
        if (failCount > 0) {
          addToast(
            `${successCount} rejected, ${failCount} failed`,
            'info'
          );
        } else {
          addToast(`${successCount} expenses rejected`, 'success');
        }
        setSelectedIds(new Set());
      }
      setRejectTarget(null);
      await fetchData();
    } catch {
      addToast('Failed to reject expense(s)', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBulkApprove = async () => {
    setIsSubmitting(true);
    try {
      const result = await approvalApi.bulkAction({
        expenseIds: Array.from(selectedIds),
        action: 'APPROVE',
      });
      const successCount = result.successful.length;
      const failCount = result.failed.length;
      if (failCount > 0) {
        addToast(
          `${successCount} approved, ${failCount} failed`,
          'info'
        );
      } else {
        addToast(`${successCount} expenses approved`, 'success');
      }
      setSelectedIds(new Set());
      await fetchData();
    } catch {
      addToast('Failed to bulk approve', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBulkReject = () => {
    setRejectTarget({ type: 'bulk' });
  };

  const handleToggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleToggleAll = () => {
    if (!data) return;
    if (selectedIds.size === data.content.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(data.content.map((e) => e.id)));
    }
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
          onClick={fetchData}
          className="mt-3 text-sm font-medium text-red-700 underline hover:text-red-800"
        >
          Try again
        </button>
      </div>
    );
  }

  const expenses = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Pending Approvals</h1>
        <p className="mt-1 text-sm text-gray-500">
          Review and approve or reject expense submissions from your team.
        </p>
      </div>

      <ApprovalTable
        expenses={expenses}
        selectedIds={selectedIds}
        onToggleSelect={handleToggleSelect}
        onToggleAll={handleToggleAll}
        onApprove={handleApprove}
        onReject={handleReject}
        isSubmitting={isSubmitting}
      />

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <p className="text-sm text-gray-600">
            Page {page + 1} of {totalPages} ({data?.totalElements} total)
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      )}

      <BulkActions
        selectedCount={selectedIds.size}
        onBulkApprove={handleBulkApprove}
        onBulkReject={handleBulkReject}
        onClearSelection={() => setSelectedIds(new Set())}
        isSubmitting={isSubmitting}
      />

      <RejectModal
        isOpen={rejectTarget !== null}
        onClose={() => setRejectTarget(null)}
        onConfirm={handleRejectConfirm}
        title={
          rejectTarget?.type === 'bulk'
            ? `Reject ${selectedIds.size} Expenses`
            : 'Reject Expense'
        }
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
