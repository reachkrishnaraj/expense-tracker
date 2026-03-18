import type { ExpenseStatus } from '../../types/expense';
import type { Category } from '../../types/category';

interface FilterValues {
  status: ExpenseStatus | '';
  categoryId: string;
  startDate: string;
  endDate: string;
}

interface ExpenseFilterBarProps {
  filters: FilterValues;
  categories: Category[];
  onFilterChange: (filters: FilterValues) => void;
}

const statuses: { value: ExpenseStatus | ''; label: string }[] = [
  { value: '', label: 'All Statuses' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SUBMITTED', label: 'Submitted' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

export type { FilterValues };

export function ExpenseFilterBar({
  filters,
  categories,
  onFilterChange,
}: ExpenseFilterBarProps) {
  const handleChange = (field: keyof FilterValues, value: string) => {
    onFilterChange({ ...filters, [field]: value });
  };

  const handleClear = () => {
    onFilterChange({ status: '', categoryId: '', startDate: '', endDate: '' });
  };

  const hasActiveFilters =
    filters.status !== '' ||
    filters.categoryId !== '' ||
    filters.startDate !== '' ||
    filters.endDate !== '';

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <div>
          <label
            htmlFor="status-filter"
            className="block text-sm font-medium text-gray-700"
          >
            Status
          </label>
          <select
            id="status-filter"
            value={filters.status}
            onChange={(e) => handleChange('status', e.target.value)}
            className="mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-sm focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 border shadow-sm"
          >
            {statuses.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label
            htmlFor="category-filter"
            className="block text-sm font-medium text-gray-700"
          >
            Category
          </label>
          <select
            id="category-filter"
            value={filters.categoryId}
            onChange={(e) => handleChange('categoryId', e.target.value)}
            className="mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-sm focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 border shadow-sm"
          >
            <option value="">All Categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label
            htmlFor="start-date-filter"
            className="block text-sm font-medium text-gray-700"
          >
            From Date
          </label>
          <input
            id="start-date-filter"
            type="date"
            value={filters.startDate}
            onChange={(e) => handleChange('startDate', e.target.value)}
            className="mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 border shadow-sm"
          />
        </div>

        <div>
          <label
            htmlFor="end-date-filter"
            className="block text-sm font-medium text-gray-700"
          >
            To Date
          </label>
          <input
            id="end-date-filter"
            type="date"
            value={filters.endDate}
            onChange={(e) => handleChange('endDate', e.target.value)}
            className="mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 border shadow-sm"
          />
        </div>

        <div className="flex items-end">
          {hasActiveFilters && (
            <button
              onClick={handleClear}
              className="inline-flex items-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
            >
              Clear Filters
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
