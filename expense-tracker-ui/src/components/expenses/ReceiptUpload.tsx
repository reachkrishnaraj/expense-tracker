import { useState, useRef, useCallback, type DragEvent } from 'react';

const MAX_FILES = 3;
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'application/pdf'];

interface ReceiptUploadProps {
  existingCount: number;
  onUpload: (files: File[]) => Promise<void>;
  disabled?: boolean;
}

export function ReceiptUpload({
  existingCount,
  onUpload,
  disabled = false,
}: ReceiptUploadProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const remainingSlots = MAX_FILES - existingCount;

  const validateFiles = useCallback(
    (files: FileList | File[]): { valid: File[]; errors: string[] } => {
      const validFiles: File[] = [];
      const fileErrors: string[] = [];
      const fileArray = Array.from(files);

      if (fileArray.length > remainingSlots) {
        fileErrors.push(
          `You can only upload ${remainingSlots} more file${remainingSlots !== 1 ? 's' : ''} (max ${MAX_FILES} total).`
        );
        return { valid: [], errors: fileErrors };
      }

      for (const file of fileArray) {
        if (!ACCEPTED_TYPES.includes(file.type)) {
          fileErrors.push(
            `"${file.name}" is not a supported format. Use JPEG, PNG, GIF, or PDF.`
          );
          continue;
        }
        if (file.size > MAX_FILE_SIZE) {
          fileErrors.push(
            `"${file.name}" exceeds the 5MB size limit.`
          );
          continue;
        }
        validFiles.push(file);
      }

      return { valid: validFiles, errors: fileErrors };
    },
    [remainingSlots]
  );

  const handleFiles = useCallback(
    async (files: FileList | File[]) => {
      setErrors([]);
      const { valid, errors: validationErrors } = validateFiles(files);

      if (validationErrors.length > 0) {
        setErrors(validationErrors);
      }

      if (valid.length > 0) {
        setIsUploading(true);
        try {
          await onUpload(valid);
        } catch {
          setErrors((prev) => [...prev, 'Upload failed. Please try again.']);
        } finally {
          setIsUploading(false);
        }
      }
    },
    [validateFiles, onUpload]
  );

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (!disabled && remainingSlots > 0) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (!disabled && remainingSlots > 0 && e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  };

  const handleClick = () => {
    if (!disabled && remainingSlots > 0) {
      fileInputRef.current?.click();
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFiles(e.target.files);
      e.target.value = '';
    }
  };

  if (remainingSlots <= 0 && !disabled) {
    return (
      <p className="text-sm text-gray-500">
        Maximum number of receipts ({MAX_FILES}) reached.
      </p>
    );
  }

  return (
    <div>
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={handleClick}
        className={`relative cursor-pointer rounded-lg border-2 border-dashed p-6 text-center transition-colors ${
          disabled
            ? 'cursor-not-allowed border-gray-200 bg-gray-50'
            : isDragging
              ? 'border-indigo-500 bg-indigo-50'
              : 'border-gray-300 hover:border-indigo-400 hover:bg-gray-50'
        }`}
      >
        {isUploading ? (
          <div className="flex flex-col items-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-indigo-600" />
            <p className="mt-2 text-sm text-gray-600">Uploading...</p>
          </div>
        ) : (
          <>
            <svg
              className="mx-auto h-10 w-10 text-gray-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5"
              />
            </svg>
            <p className="mt-2 text-sm text-gray-600">
              <span className="font-semibold text-indigo-600">
                Click to upload
              </span>{' '}
              or drag and drop
            </p>
            <p className="mt-1 text-xs text-gray-500">
              JPEG, PNG, GIF, or PDF up to 5MB ({remainingSlots} file
              {remainingSlots !== 1 ? 's' : ''} remaining)
            </p>
          </>
        )}
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept={ACCEPTED_TYPES.join(',')}
          onChange={handleInputChange}
          className="hidden"
          disabled={disabled || isUploading}
        />
      </div>

      {errors.length > 0 && (
        <div className="mt-2 space-y-1">
          {errors.map((error, i) => (
            <p key={i} className="text-sm text-red-600">
              {error}
            </p>
          ))}
        </div>
      )}
    </div>
  );
}
