import { Component, computed, inject } from '@angular/core';

import { QueueService } from '../../core/queue.service';
import { AppointmentView } from '../../models/views';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { QueueCardComponent } from './queue-card.component';

@Component({
  selector: 'app-queue-page',
  standalone: true,
  imports: [QueueCardComponent, LocalDatePipe],
  templateUrl: './queue-page.component.html'
})
export class QueuePageComponent {
  readonly queue = inject(QueueService);

  /** Urgent visits first, then the earliest original time. */
  readonly items = computed<AppointmentView[]>(() =>
    [...this.queue.appointments()].sort((a, b) => {
      const urgency = Number(b.type === 'URGENT') - Number(a.type === 'URGENT');
      return urgency !== 0 ? urgency : a.slot.startTime.localeCompare(b.slot.startTime);
    })
  );
  readonly withSuggestion = computed(() => this.items().filter(a => a.pendingSuggestion).length);
  readonly withoutSuggestion = computed(() => this.items().length - this.withSuggestion());

  lastUpdatedText(): string {
    const date = this.queue.lastUpdated();
    return date ? date.toLocaleTimeString('en-GB') : '';
  }
}
