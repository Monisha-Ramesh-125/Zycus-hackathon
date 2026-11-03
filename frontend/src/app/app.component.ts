import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, map, of, switchMap, timer } from 'rxjs';

import { HEALTH_POLL_INTERVAL_MS } from './core/api.config';
import { ApiService } from './core/api.service';
import { NotificationService } from './core/notification.service';
import { QueueService } from './core/queue.service';
import { StrategySwitcherComponent } from './layout/strategy-switcher.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, StrategySwitcherComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  private readonly api = inject(ApiService);
  readonly queue = inject(QueueService);
  readonly notifications = inject(NotificationService);

  readonly online = signal<boolean | null>(null);

  constructor() {
    this.queue.start();
    timer(0, HEALTH_POLL_INTERVAL_MS)
      .pipe(
        switchMap(() =>
          this.api.ping().pipe(
            map(() => true),
            catchError(() => of(false))
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe(online => this.online.set(online));
  }
}
