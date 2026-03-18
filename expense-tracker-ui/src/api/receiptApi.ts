import axiosInstance from './axiosInstance';
import type { Receipt } from '../types/expense';

export const receiptApi = {
  uploadReceipt(expenseId: string, file: File): Promise<Receipt> {
    const formData = new FormData();
    formData.append('file', file);
    return axiosInstance
      .post<Receipt>(`/expenses/${expenseId}/receipts`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((res) => res.data);
  },

  downloadReceipt(expenseId: string, receiptId: string): Promise<Blob> {
    return axiosInstance
      .get(`/expenses/${expenseId}/receipts/${receiptId}/download`, {
        responseType: 'blob',
      })
      .then((res) => res.data);
  },

  deleteReceipt(expenseId: string, receiptId: string): Promise<void> {
    return axiosInstance
      .delete(`/expenses/${expenseId}/receipts/${receiptId}`)
      .then(() => undefined);
  },
};
