import { Injectable, signal } from '@angular/core';

import { friendlyMessage, toApiError } from './api-error';

export interface NoticeModel {
  id: number;
  kind: 'success' | 'info' | 'error';
  text: string;
  link?: { label: string; route: string };
}

const AUTO_DISMISS_MS = 6000;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly notices = signal<NoticeModel[]>([]);
  private nextId = 1;

  success(text: string, link?: NoticeModel['link']): void {
    this.push('success', text, link);
  }

  info(text: string, link?: NoticeModel['link']): void {
    this.push('info', text, link);
  }

  /** Accepts a message or any caught error; backend errors are translated to plain language. */
  error(error: unknown): void {
    this.push('error', typeof error === 'string' ? error : friendlyMessage(toApiError(error)));
  }

  dismiss(id: number): void {
    this.notices.update(list => list.filter(notice => notice.id !== id));
  }

  private push(kind: NoticeModel['kind'], text: string, link?: NoticeModel['link']): void {
    if (this.notices().some(notice => notice.kind === kind && notice.text === text)) {
      return;
    }
    const id = this.nextId++;
    this.notices.update(list => [...list, { id, kind, text, link }]);
    if (kind !== 'error') {
      setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
    }
  }
}
