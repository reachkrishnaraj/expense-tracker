import { useState } from 'react';
import { useCategories } from '../hooks/useUsers';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useToast } from '../components/common/Toast';

export function CategoryManagementPage() {
  const { addToast } = useToast();
  const {
    categories,
    isLoading,
    error,
    refetch,
    addCategory,
    renameCategory,
    deactivateCategory,
    activateCategory,
  } = useCategories();

  const [newCategoryName, setNewCategoryName] = useState('');
  const [isAdding, setIsAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleAdd = async () => {
    const name = newCategoryName.trim();
    if (!name) return;

    setIsSubmitting(true);
    try {
      await addCategory(name);
      addToast('Category added successfully', 'success');
      setNewCategoryName('');
      setIsAdding(false);
    } catch {
      addToast('Failed to add category', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRename = async (id: string) => {
    const name = editingName.trim();
    if (!name) return;

    setIsSubmitting(true);
    try {
      await renameCategory(id, name);
      addToast('Category renamed successfully', 'success');
      setEditingId(null);
      setEditingName('');
    } catch {
      addToast('Failed to rename category', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeactivate = async (id: string) => {
    setIsSubmitting(true);
    try {
      await deactivateCategory(id);
      addToast('Category deactivated', 'success');
    } catch {
      addToast('Failed to deactivate category', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleActivate = async (id: string) => {
    setIsSubmitting(true);
    try {
      await activateCategory(id);
      addToast('Category activated', 'success');
    } catch {
      addToast('Failed to activate category', 'error');
    } finally {
      setIsSubmitting(false);
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
          onClick={refetch}
          className="mt-3 text-sm font-medium text-red-700 underline hover:text-red-800"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            Category Management
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage expense categories for your organization.
          </p>
        </div>
        {!isAdding && (
          <button
            type="button"
            onClick={() => setIsAdding(true)}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Add Category
          </button>
        )}
      </div>

      {/* Inline Add Form */}
      {isAdding && (
        <div className="mb-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4">
          <h3 className="mb-2 text-sm font-semibold text-gray-900">
            New Category
          </h3>
          <div className="flex gap-3">
            <input
              type="text"
              value={newCategoryName}
              onChange={(e) => setNewCategoryName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleAdd();
                if (e.key === 'Escape') {
                  setIsAdding(false);
                  setNewCategoryName('');
                }
              }}
              placeholder="Category name"
              autoFocus
              className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
            <button
              type="button"
              onClick={handleAdd}
              disabled={isSubmitting || !newCategoryName.trim()}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              {isSubmitting ? 'Adding...' : 'Add'}
            </button>
            <button
              type="button"
              onClick={() => {
                setIsAdding(false);
                setNewCategoryName('');
              }}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Category List */}
      {categories.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
          <p className="text-sm text-gray-500">
            No categories yet. Add your first category to get started.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <ul className="divide-y divide-gray-200">
            {categories.map((category) => (
              <li
                key={category.id}
                className="flex items-center justify-between px-6 py-4 hover:bg-gray-50"
              >
                {editingId === category.id ? (
                  <div className="flex flex-1 items-center gap-3">
                    <input
                      type="text"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') handleRename(category.id);
                        if (e.key === 'Escape') {
                          setEditingId(null);
                          setEditingName('');
                        }
                      }}
                      autoFocus
                      className="flex-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                    <button
                      type="button"
                      onClick={() => handleRename(category.id)}
                      disabled={isSubmitting || !editingName.trim()}
                      className="text-xs font-medium text-indigo-600 hover:text-indigo-800 disabled:opacity-50"
                    >
                      Save
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setEditingId(null);
                        setEditingName('');
                      }}
                      className="text-xs font-medium text-gray-500 hover:text-gray-700"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium text-gray-900">
                        {category.name}
                      </span>
                      {!category.active && (
                        <span className="inline-flex rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">
                          Inactive
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(category.id);
                          setEditingName(category.name);
                        }}
                        className="text-xs font-medium text-indigo-600 hover:text-indigo-800"
                      >
                        Rename
                      </button>
                      {category.active ? (
                        <button
                          type="button"
                          onClick={() => handleDeactivate(category.id)}
                          disabled={isSubmitting}
                          className="text-xs font-medium text-red-600 hover:text-red-800 disabled:opacity-50"
                        >
                          Deactivate
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleActivate(category.id)}
                          disabled={isSubmitting}
                          className="text-xs font-medium text-green-600 hover:text-green-800 disabled:opacity-50"
                        >
                          Activate
                        </button>
                      )}
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
