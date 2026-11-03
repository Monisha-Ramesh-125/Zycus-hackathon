import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, ViewChild } from '@angular/core';

let nextDialogId = 0;

/** A modal confirmation: Escape cancels, Tab stays inside, focus returns to the opener on close. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  template: `
    <div class="overlay" (click)="onBackdrop($event)">
      <div class="dialog" role="dialog" aria-modal="true" [attr.aria-labelledby]="titleId" #dialog>
        <h2 [id]="titleId">{{ title }}</h2>
        <div class="dialog-body"><ng-content /></div>
        <div class="dialog-actions">
          <button type="button" class="btn" #cancelButton [disabled]="busy" (click)="cancelled.emit()">Cancel</button>
          <button type="button" class="btn" [class.btn-danger]="tone === 'danger'" [class.btn-primary]="tone !== 'danger'"
                  [disabled]="busy" (click)="confirmed.emit()">
            {{ busy ? 'Working…' : confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) title = '';
  @Input() confirmLabel = 'Confirm';
  @Input() tone: 'primary' | 'danger' = 'primary';
  @Input() busy = false;
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLElement>;
  @ViewChild('cancelButton', { static: true }) private cancelButton!: ElementRef<HTMLButtonElement>;

  readonly titleId = `dialog-title-${nextDialogId++}`;
  private opener: HTMLElement | null = document.activeElement as HTMLElement | null;

  ngAfterViewInit(): void {
    this.cancelButton.nativeElement.focus();
  }

  ngOnDestroy(): void {
    this.opener?.focus?.();
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget && !this.busy) {
      this.cancelled.emit();
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !this.busy) {
      this.cancelled.emit();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const focusable = Array.from(
      this.dialog.nativeElement.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')
    ).filter(element => !element.hasAttribute('disabled'));
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    } else if (!this.dialog.nativeElement.contains(active)) {
      event.preventDefault();
      first.focus();
    }
  }
}
