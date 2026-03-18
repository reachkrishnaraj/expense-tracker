export interface PendingExpense {
  id: string;
  submitterName: string;
  submitterEmail: string;
  date: string;
  category: string;
  description: string;
  amount: number;
  currency: string;
  submittedAt: string;
}

export interface BulkApprovalRequest {
  expenseIds: string[];
  action: 'APPROVE' | 'REJECT';
  comment?: string;
}

export interface BulkApprovalResult {
  successful: string[];
  failed: { id: string; reason: string }[];
}
