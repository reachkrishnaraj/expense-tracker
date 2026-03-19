import axiosInstance from './axiosInstance';
import type { PaginatedResponse } from '../types/common';
import type {
  PendingExpense,
  BulkApprovalRequest,
  BulkApprovalResult,
} from '../types/approval';

export interface ApprovalParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export const approvalApi = {
  getPendingApprovals(
    params?: ApprovalParams
  ): Promise<PaginatedResponse<PendingExpense>> {
    return axiosInstance
      .get('/approvals/pending', { params })
      .then((res) => {
        const data = res.data as any;
        return {
          ...data,
          content: (data.content || []).map((e: any) => ({
            id: e.id,
            submitterName: e.submitter?.name || e.submitterName || 'Unknown',
            submitterEmail: e.submitter?.email || e.submitterEmail || '',
            date: e.expenseDate || e.date || e.createdAt,
            category: typeof e.category === 'object' ? e.category?.name : (e.categoryName || e.category || 'N/A'),
            description: e.notes || e.merchantName || e.description || '',
            amount: e.amount || 0,
            currency: e.currency || 'USD',
            submittedAt: e.updatedAt || e.submittedAt || e.createdAt,
          })),
        };
      });
  },

  approveExpense(id: string, comment?: string): Promise<void> {
    return axiosInstance
      .post(`/expenses/${id}/approve`, { comment })
      .then(() => undefined);
  },

  rejectExpense(id: string, comment: string): Promise<void> {
    return axiosInstance
      .post(`/expenses/${id}/reject`, { comment })
      .then(() => undefined);
  },

  bulkAction(data: BulkApprovalRequest): Promise<BulkApprovalResult> {
    return axiosInstance
      .post('/approvals/bulk', data)
      .then((res) => {
        const d = res.data as any;
        // Map backend response (processed/skipped/results) to frontend type (successful/failed)
        const successful = (d.results || [])
          .filter((r: any) => r.status === 'SUCCESS')
          .map((r: any) => r.expenseId);
        const failed = (d.results || [])
          .filter((r: any) => r.status !== 'SUCCESS')
          .map((r: any) => ({ id: r.expenseId, reason: r.reason || 'Unknown' }));
        return { successful, failed };
      });
  },
};
