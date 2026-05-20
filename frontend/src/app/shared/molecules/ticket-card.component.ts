import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { PublicUser, Ticket, TicketType } from '../../core/api.models';
import { priorityLabel } from '../../state/queue.selectors';
import { BadgeComponent } from '../atoms/badge.component';

@Component({
  selector: 'qd-ticket-card',
  standalone: true,
  imports: [BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article
      class="ticket-card"
      draggable="true"
      (click)="opened.emit(ticket().id)"
      (dragstart)="dragStarted.emit(ticket().id)">
      <div class="ticket-meta">
        <qd-badge>{{ ticket().key }}</qd-badge>
        <qd-badge [variant]="priorityVariant(ticket().priority)">{{ priorityLabel(ticket().priority) }}</qd-badge>
      </div>

      <strong>{{ ticket().title }}</strong>

      <div class="badges">
        <qd-badge [dotColor]="type()?.color ?? '#667085'">{{ type()?.name ?? 'Type' }}</qd-badge>
        @for (label of ticket().labels; track label) {
          <qd-badge>{{ label }}</qd-badge>
        }
      </div>

      <div class="card-footer">
        <span class="muted">{{ assignee()?.displayName ?? 'Unassigned' }}</span>
        @if (ticket().dueDate) {
          <span class="muted">Due {{ ticket().dueDate }}</span>
        }
        @if (ticket().estimate !== null && ticket().estimate !== undefined) {
          <span class="muted">{{ ticket().estimate }} pts</span>
        }
      </div>
    </article>
  `
})
export class TicketCardComponent {
  readonly ticket = input.required<Ticket>();
  readonly type = input<TicketType | null>(null);
  readonly assignee = input<PublicUser | null>(null);
  readonly opened = output<string>();
  readonly dragStarted = output<string>();

  protected readonly priorityLabel = priorityLabel;

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }
}
