export function formatDateWithOffset(date: Date): string {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    throw new Error('Invalid date provided to formatDateWithOffset');
  }

  const pad = (value: number, length = 2) => String(Math.abs(Math.trunc(value))).padStart(length, '0');
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');

  const offsetTotalMinutes = -date.getTimezoneOffset();
  const sign = offsetTotalMinutes >= 0 ? '+' : '-';
  const absoluteMinutes = Math.abs(offsetTotalMinutes);
  const offsetHours = pad(Math.floor(absoluteMinutes / 60));
  const offsetMinutes = pad(absoluteMinutes % 60);

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${sign}${offsetHours}:${offsetMinutes}`;
}
