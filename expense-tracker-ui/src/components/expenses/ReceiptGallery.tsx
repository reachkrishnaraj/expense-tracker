import { useState } from 'react';
import type { Receipt } from '../../types/expense';
import { receiptApi } from '../../api/receiptApi';

interface ReceiptGalleryProps {
  expenseId: string;
  receipts: Receipt[];
  canDelete?: boolean;
  onDelete?: (receiptId: string) => void;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isImageType(fileType: string): boolean {
  return fileType.startsWith('image/');
}

export function ReceiptGallery({
  expenseId,
  receipts,
  canDelete = false,
  onDelete,
}: ReceiptGalleryProps) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  if (receipts.length === 0) {
    return (
      <p className="text-sm text-gray-500 italic">No receipts attached.</p>
    );
  }

  const handleDownload = async (receipt: Receipt) => {
    setDownloadingId(receipt.id);
    try {
      const blob = await receiptApi.downloadReceipt(expenseId, receipt.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = receipt.fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch {
      // Error handled by caller
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {receipts.map((receipt) => (
        <div
          key={receipt.id}
          className="group relative rounded-lg border border-gray-200 bg-white p-3 shadow-sm"
        >
          <div className="flex items-center space-x-3">
            {isImageType(receipt.fileType) ? (
              <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-indigo-50">
                <svg
                  className="h-6 w-6 text-indigo-600"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth={1.5}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z"
                  />
                </svg>
              </div>
            ) : (
              <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-red-50">
                <svg
                  className="h-6 w-6 text-red-600"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth={1.5}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z"
                  />
                </svg>
              </div>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-gray-900">
                {receipt.fileName}
              </p>
              <p className="text-xs text-gray-500">
                {formatFileSize(receipt.fileSize)}
              </p>
            </div>
          </div>

          <div className="mt-3 flex items-center gap-2">
            <button
              onClick={() => handleDownload(receipt)}
              disabled={downloadingId === receipt.id}
              className="inline-flex items-center rounded px-2.5 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 hover:bg-indigo-100 disabled:opacity-50"
            >
              {downloadingId === receipt.id ? 'Downloading...' : 'Download'}
            </button>
            {canDelete && onDelete && (
              <button
                onClick={() => onDelete(receipt.id)}
                className="inline-flex items-center rounded px-2.5 py-1.5 text-xs font-medium text-red-700 bg-red-50 hover:bg-red-100"
              >
                Remove
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
