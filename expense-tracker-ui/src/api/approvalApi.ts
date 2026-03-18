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
      .get<PaginatedResponse<PendingExpense>>('/expenses/pending', { params })
      .then((res) => res.data);
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
      .post<BulkApprovalResult>('/expenses/bulk-action', data)
      .then((res) => res.data);
  },
};
