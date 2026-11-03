import { TitleCasePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';

import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/api-error';
import { PROVIDER_STATUS_DISPLAY } from '../../core/display';
import { NotificationService } from '../../core/notification.service';
import { PROVIDER_STATUSES, ProviderStatus } from '../../models/enums';
import { ProviderView } from '../../models/views';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

export interface ProviderStats {
  upcoming: number;
  waiting: number;
  openSlots: number;
}

@Component({
  selector: 'app-provider-card',
  standalone: true,
  imports: [StatusBadgeComponent, ConfirmDialogComponent, LocalDatePipe, TitleCasePipe],
  templateUrl: './provider-card.component.html'
})
export class ProviderCardComponent {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);

  @Input({ required: true }) provider!: ProviderView;
  @Input({ required: true }) stats!: ProviderStats;
  @Output() changed = new EventEmitter<ProviderStatus>();

  readonly statuses = PROVIDER_STATUSES;
  readonly statusLabel = (status: ProviderStatus): string => PROVIDER_STATUS_DISPLAY[status].label;

  readonly chosen = signal<ProviderStatus | null>(null);
  readonly reason = signal('');
  readonly confirming = signal(false);
  readonly busy = signal(false);

  readonly selected = computed(() => this.chosen() ?? this.provider.status);

  choose(status: ProviderStatus): void {
    this.chosen.set(status);
  }

  get dirty(): boolean {
    return this.selected() !== this.provider.status;
  }

  apply(): void {
    if (!this.dirty || this.busy()) {
      return;
    }
    if (this.selected() === 'OFFLINE') {
      this.confirming.set(true);
      return;
    }
    this.save();
  }

  save(): void {
    const status = this.selected();
    this.busy.set(true);
    this.api.updateProviderStatus(this.provider.id, { status, reason: this.reason().trim() }).subscribe({
      next: updated => {
        this.busy.set(false);
        this.confirming.set(false);
        this.chosen.set(null);
        this.reason.set('');
        if (status === 'OFFLINE') {
          this.notifications.success(
            `${updated.name} is offline. Affected appointments are being rescheduled in the background; proposals will appear in the queue shortly.`,
            { label: 'Open queue', route: '/queue' }
          );
        } else {
          this.notifications.success(`${updated.name} is now ${this.statusLabel(status).toLowerCase()}.`);
        }
        this.changed.emit(status);
      },
      error: (raw: unknown) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.notifications.error(toApiError(raw));
      }
    });
  }
}
