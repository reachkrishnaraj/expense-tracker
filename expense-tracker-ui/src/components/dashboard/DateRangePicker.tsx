interface DateRangePickerProps {
  startDate: string;
  endDate: string;
  onStartDateChange: (date: string) => void;
  onEndDateChange: (date: string) => void;
}

export function DateRangePicker({
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
}: DateRangePickerProps) {
  const today = new Date();

  const getPresetRange = (preset: string): { start: string; end: string } => {
    const end = today.toISOString().split('T')[0];

    switch (preset) {
      case 'thisMonth': {
        const start = new Date(today.getFullYear(), today.getMonth(), 1)
          .toISOString()
          .split('T')[0];
        return { start, end };
      }
      case 'lastMonth': {
        const start = new Date(today.getFullYear(), today.getMonth() - 1, 1)
          .toISOString()
          .split('T')[0];
        const lastDay = new Date(today.getFullYear(), today.getMonth(), 0)
          .toISOString()
          .split('T')[0];
        return { start, end: lastDay };
      }
      case 'last3Months': {
        const start = new Date(today.getFullYear(), today.getMonth() - 2, 1)
          .toISOString()
          .split('T')[0];
        return { start, end };
      }
      case 'thisYear': {
        const start = new Date(today.getFullYear(), 0, 1)
          .toISOString()
          .split('T')[0];
        return { start, end };
      }
      default:
        return { start: '', end: '' };
    }
  };

  const handlePreset = (preset: string) => {
    const { start, end } = getPresetRange(preset);
    onStartDateChange(start);
    onEndDateChange(end);
  };

  const presets = [
    { key: 'thisMonth', label: 'This Month' },
    { key: 'lastMonth', label: 'Last Month' },
    { key: 'last3Months', label: 'Last 3 Months' },
    { key: 'thisYear', label: 'This Year' },
  ];

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-gray-600">From</label>
        <input
          type="date"
          value={startDate}
          onChange={(e) => onStartDateChange(e.target.value)}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
        />
      </div>
      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-gray-600">To</label>
        <input
          type="date"
          value={endDate}
          onChange={(e) => onEndDateChange(e.target.value)}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
        />
      </div>

      <div className="h-6 w-px bg-gray-300" />

      <div className="flex gap-1.5">
        {presets.map((preset) => (
          <button
            key={preset.key}
            type="button"
            onClick={() => handlePreset(preset.key)}
            className="rounded-md border border-gray-200 bg-white px-3 py-1.5 text-xs font-medium text-gray-600 hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700 transition-colors"
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
}
