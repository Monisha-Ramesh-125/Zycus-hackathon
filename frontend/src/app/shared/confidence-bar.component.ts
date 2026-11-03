import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-confidence-bar',
  standalone: true,
  template: `
    <div class="confidence">
      <div class="confidence-head">
        <span>Confidence</span>
        <strong>{{ percent }}% <span class="muted">({{ level }})</span></strong>
      </div>
      <div class="meter" role="meter" aria-label="Confidence" aria-valuemin="0" aria-valuemax="100"
           [attr.aria-valuenow]="percent" [attr.data-level]="level">
        <div class="meter-fill" [style.width.%]="percent"></div>
      </div>
    </div>
  `
})
export class ConfidenceBarComponent {
  @Input({ required: true }) value = 0;

  get percent(): number {
    return Math.round(Math.max(0, Math.min(1, this.value)) * 100);
  }

  get level(): 'high' | 'medium' | 'low' {
    return this.percent >= 80 ? 'high' : this.percent >= 50 ? 'medium' : 'low';
  }
}
