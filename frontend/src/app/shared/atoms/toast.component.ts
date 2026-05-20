import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'qd-toast',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (message()) {
      <button type="button" class="toast" (click)="cleared.emit()" aria-label="Dismiss notification">
        {{ message() }}
      </button>
    }
  `
})
export class ToastComponent {
  readonly message = input('');
  readonly cleared = output<void>();
}
