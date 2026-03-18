export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ErrorResponse {
  error: string;
  code: string;
  details?: Record<string, unknown>;
  fieldErrors?: { field: string; message: string }[];
  timestamp: string;
  path: string;
}
