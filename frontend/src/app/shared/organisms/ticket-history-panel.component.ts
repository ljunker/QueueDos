import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PublicUser, TicketChange } from '../../core/api.models';
import { userById } from '../../state/queue.selectors';

@Component({
  selector: 'qd-ticket-history-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel wide">
      <h3>History</h3>
      <div class="timeline">
        @for (change of changes(); track change.id) {
          <article class="timeline-item">
            <strong>{{ userById(users(), change.actorId)?.displayName ?? 'User' }}</strong>
            <small>{{ formatDateTime(change.createdAt) }}</small>
            <p>{{ changeText(change) }}</p>
          </article>
        } @empty {
          <p class="muted">No changes</p>
        }
      </div>
    </section>
  `
})
export class TicketHistoryPanelComponent {
  readonly changes = input<TicketChange[]>([]);
  readonly users = input<PublicUser[]>([]);

  protected readonly userById = userById;

  protected formatDateTime(value: string): string {
    return new Date(value).toLocaleString();
  }

  protected changeText(change: TicketChange): string {
    if (change.oldValue === null && change.newValue === 'created') {
      return 'created ticket';
    }
    return `${change.field}: ${change.oldValue ?? '-'} -> ${change.newValue ?? '-'}`;
  }
}
