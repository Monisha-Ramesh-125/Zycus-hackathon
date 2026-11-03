import { Pipe, PipeTransform } from '@angular/core';

import { formatDateTime, formatDay, formatTime } from '../core/local-datetime';

@Pipe({ name: 'localDate', standalone: true })
export class LocalDatePipe implements PipeTransform {
  transform(value: string | null | undefined, format: 'time' | 'day' | 'dateTime' = 'dateTime'): string {
    if (!value) {
      return '';
    }
    switch (format) {
      case 'time':
        return formatTime(value);
      case 'day':
        return formatDay(value);
      default:
        return formatDateTime(value);
    }
  }
}
