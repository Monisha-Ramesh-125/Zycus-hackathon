/*
 * The backend sends local date-times without a zone ("2026-11-03T17:26:29.008026"). They are read
 * field by field so the browser never applies a timezone shift.
 */
const LOCAL = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/;

const pad = (n: number): string => String(n).padStart(2, '0');

export function parseLocal(value: string): Date {
  const match = LOCAL.exec(value);
  if (!match) {
    return new Date(NaN);
  }
  const [, year, month, day, hour, minute, second] = match;
  return new Date(+year, +month - 1, +day, +hour, +minute, +(second ?? 0));
}

export function toLocalDateTimeString(date: Date): string {
  return `${toDateKey(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function toDateKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function dateKeyOf(value: string): string {
  return value.slice(0, 10);
}

export function minutesOfDay(value: string): number {
  const date = parseLocal(value);
  return date.getHours() * 60 + date.getMinutes();
}

export function formatTime(value: string): string {
  const date = parseLocal(value);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formatDay(value: string): string {
  return parseLocal(value).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
}

export function formatDateTime(value: string): string {
  return `${formatDay(value)}, ${formatTime(value)}`;
}

export function isFuture(value: string): boolean {
  return parseLocal(value).getTime() > Date.now();
}
