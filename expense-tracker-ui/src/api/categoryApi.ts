import axiosInstance from './axiosInstance';
import type { Category } from '../types/category';

export const categoryApi = {
  getCategories(): Promise<Category[]> {
    return axiosInstance
      .get<Category[]>('/categories')
      .then((res) => res.data);
  },
};
