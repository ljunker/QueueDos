import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'qd-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge" [class.priority-critical]="variant() === 'critical'" [class.priority-high]="variant() === 'high'"
      [class.priority-medium]="variant() === 'medium'" [class.priority-low]="variant() === 'low'">
      @if (dotColor()) {
        <span class="type-dot" [style.background]="dotColor()"></span>
      }
      <ng-content />
    </span>
  `
})
export class BadgeComponent {
  readonly variant = input<'default' | 'critical' | 'high' | 'medium' | 'low'>('default');
  readonly dotColor = input<string | null>(null);
}
