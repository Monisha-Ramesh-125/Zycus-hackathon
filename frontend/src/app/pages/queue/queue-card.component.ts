import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';

import { ApiService } from '../../core/api.service';
import { ApiError, friendlyMessage, suggestsRefresh, toApiError } from '../../core/api-error';
import { strategyDisplay } from '../../core/display';
import { formatDateTime } from '../../core/local-datetime';
import { NotificationService } from '../../core/notification.service';
import { AppointmentView } from '../../models/views';
import { ConfidenceBarComponent } from '../../shared/confidence-bar.component';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

type Panel = 'ACCEPT' | 'REJECT' | null;

/** One waiting appointment with its proposed slot and the accept / reject controls. */
@Component({
  selector: 'app-queue-card',
  standalone: true,
  imports: [StatusBadgeComponent, ConfidenceBarComponent, LocalDatePipe],
  templateUrl: './queue-card.component.html'
})
export class QueueCardComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);

  @Input({ required: true }) appointment!: AppointmentView;
  @Output() resolved = new EventEmitter<void>();

  readonly panel = signal<Panel>(null);
  readonly note = signal('');
  readonly busy = signal(false);
  readonly finding = signal(false);
  readonly problem = signal<ApiError | null>(null);
  readonly findMessage = signal<string | null>(null);

  /** Messages belong to one proposal; a new, dismissed or replaced proposal starts clean. */
  ngOnChanges(changes: SimpleChanges): void {
    const change = changes['appointment'];
    if (change && !change.firstChange) {
      const before = (change.previousValue as AppointmentView).pendingSuggestion?.id;
      const after = (change.currentValue as AppointmentView).pendingSuggestion?.id;
      if (before !== after) {
        this.problem.set(null);
        this.findMessage.set(null);
      }
    }
  }

  get strategy() {
    return strategyDisplay(this.appointment.pendingSuggestion?.strategy ?? '');
  }

  get problemText(): string {
    const problem = this.problem();
    return problem ? friendlyMessage(problem) : '';
  }

  open(panel: Exclude<Panel, null>): void {
    this.panel.set(panel);
    this.note.set('');
    this.problem.set(null);
  }

  close(): void {
    this.panel.set(null);
  }

  submit(): void {
    const suggestion = this.appointment.pendingSuggestion;
    const decision = this.panel();
    if (!suggestion || !decision || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.problem.set(null);
    this.api.decide(suggestion.id, { decision, note: this.note().trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        const when = suggestion.recommendedSlot.startTime;
        this.notifications.success(
          decision === 'ACCEPT'
            ? `${suggestion.patientName} is booked with ${suggestion.recommendedSlot.providerName} (${formatDateTime(when)}).`
            : `Suggestion for ${suggestion.patientName} rejected. You can ask for another slot.`
        );
        this.panel.set(null);
        this.resolved.emit();
      },
      error: (raw: unknown) => {
        this.busy.set(false);
        const error = toApiError(raw);
        if (error.errorCode === 'SLOT_UNAVAILABLE') {
          this.problem.set(error);
          this.panel.set(null);
        } else {
          this.notifications.error(error);
        }
        if (suggestsRefresh(error)) {
          this.resolved.emit();
        }
      }
    });
  }

  /** A proposal whose slot is gone cannot be accepted; rejecting it clears it from the card. */
  dismiss(): void {
    const suggestion = this.appointment.pendingSuggestion;
    if (!suggestion || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.decide(suggestion.id, { decision: 'REJECT', note: 'Slot no longer available' }).subscribe({
      next: () => {
        this.busy.set(false);
        this.problem.set(null);
        this.findMessage.set(null);
        this.notifications.info(`Proposal for ${suggestion.patientName} dismissed. Find a new slot when one opens.`);
        this.resolved.emit();
      },
      error: (raw: unknown) => {
        this.busy.set(false);
        this.notifications.error(raw);
        this.resolved.emit();
      }
    });
  }

  findSlot(): void {
    if (this.finding()) {
      return;
    }
    this.finding.set(true);
    this.findMessage.set(null);
    this.api.suggest(this.appointment.id).subscribe({
      next: suggestion => {
        this.finding.set(false);
        this.problem.set(null);
        this.notifications.success(
          `New suggestion for ${suggestion.patientName}: ${suggestion.recommendedSlot.providerName}.`
        );
        this.resolved.emit();
      },
      error: (raw: unknown) => {
        this.finding.set(false);
        const error = toApiError(raw);
        if (error.errorCode === 'NO_SLOT_AVAILABLE') {
          this.findMessage.set(
            this.appointment.pendingSuggestion
              ? 'No other open slot fits right now. You can dismiss this proposal and try again when a slot opens.'
              : 'Still no suitable slot. Try again when a new slot opens.'
          );
          this.resolved.emit();
        } else {
          this.notifications.error(error);
        }
      }
    });
  }
}
