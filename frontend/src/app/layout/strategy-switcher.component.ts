import { Component, OnInit, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { NotificationService } from '../core/notification.service';
import { StrategyView } from '../models/views';

const LABELS: Record<string, string> = { rule: 'Rules', ai: 'AI' };

/** Shows which scheduling strategy produces suggestions and lets staff switch it. */
@Component({
  selector: 'app-strategy-switcher',
  standalone: true,
  template: `
    <div class="strategy" role="group" aria-label="Scheduling strategy">
      <span class="strategy-label" id="strategy-label">Suggestions by</span>
      @if (view(); as v) {
        @for (name of v.available; track name) {
          <button type="button" class="seg" [class.seg-active]="name === v.active" [attr.aria-pressed]="name === v.active"
                  [disabled]="busy()" (click)="select(name)"
                  [title]="name === 'ai' ? 'AI model; falls back to rules if it is unavailable' : 'Built-in scheduling rules'">
            {{ label(name) }}
          </button>
        }
      } @else {
        <span class="muted">{{ loadFailed() ? 'unavailable' : '…' }}</span>
      }
    </div>
  `
})
export class StrategySwitcherComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);

  readonly view = signal<StrategyView | null>(null);
  readonly busy = signal(false);
  readonly loadFailed = signal(false);

  ngOnInit(): void {
    this.api.strategy().subscribe({
      next: view => this.view.set(view),
      error: () => this.loadFailed.set(true)
    });
  }

  label(name: string): string {
    return LABELS[name] ?? name;
  }

  select(name: string): void {
    if (this.view()?.active === name) {
      return;
    }
    this.busy.set(true);
    this.api.switchStrategy(name).subscribe({
      next: view => {
        this.view.set(view);
        this.busy.set(false);
        this.notifications.info(`New suggestions will come from: ${this.label(view.active)}.`);
      },
      error: error => {
        this.busy.set(false);
        this.notifications.error(error);
      }
    });
  }
}
