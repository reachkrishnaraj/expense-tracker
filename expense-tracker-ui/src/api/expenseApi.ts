import axiosInstance from './axiosInstance';
import type { PaginatedResponse } from '../types/common';
import type {
  Expense,
  ExpenseDetail,
  CreateExpenseRequest,
  UpdateExpenseRequest,
  ExpenseFilterParams,
} from '../types/expense';

export const expenseApi = {
  getExpenses(params: ExpenseFilterParams): Promise<PaginatedResponse<Expense>> {
    return axiosInstance
      .get<PaginatedResponse<Expense>>('/expenses', { params })
      .then((res) => res.data);
  },

  getExpense(id: string): Promise<ExpenseDetail> {
    return axiosInstance
      .get<ExpenseDetail>(`/expenses/${id}`)
      .then((res) => res.data);
  },

  createExpense(data: CreateExpenseRequest): Promise<Expense> {
    return axiosInstance
      .post<Expense>('/expenses', data)
      .then((res) => res.data);
  },

  updateExpense(id: string, data: UpdateExpenseRequest): Promise<Expense> {
    return axiosInstance
      .put<Expense>(`/expenses/${id}`, data)
      .then((res) => res.data);
  },

  submitExpense(id: string): Promise<Expense> {
    return axiosInstance
      .post<Expense>(`/expenses/${id}/submit`)
      .then((res) => res.data);
  },

  deleteExpense(id: string): Promise<void> {
    return axiosInstance
      .delete(`/expenses/${id}`)
      .then(() => undefined);
  },
};
