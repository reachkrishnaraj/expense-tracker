export type ExpenseStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';

export interface Receipt {
  id: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  uploadedAt: string;
}

export interface AuditEntry {
  id: string;
  action: string;
  fromStatus: ExpenseStatus | null;
  toStatus: ExpenseStatus;
  comment: string | null;
  performedBy: string;
  performedByName: string;
  performedAt: string;
}

export interface Expense {
  id: string;
  amount: number;
  description: string;
  merchant: string;
  expenseDate: string;
  categoryId: string;
  categoryName: string;
  status: ExpenseStatus;
  notes: string | null;
  rejectionComment: string | null;
  receiptCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseDetail extends Expense {
  receipts: Receipt[];
  auditTrail: AuditEntry[];
}

export interface CreateExpenseRequest {
  amount: number;
  description: string;
  merchant: string;
  expenseDate: string;
  categoryId: string;
  notes?: string;
}

export interface UpdateExpenseRequest {
  amount?: number;
  description?: string;
  merchant?: string;
  expenseDate?: string;
  categoryId?: string;
  notes?: string;
}

export interface ExpenseFilterParams {
  status?: ExpenseStatus;
  categoryId?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
