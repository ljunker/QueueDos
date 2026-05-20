import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {PublicUser, Ticket, TicketType} from '../../core/api.models';
import {priorityLabel} from '../../state/queue.selectors';
import {BadgeComponent} from '../atoms/badge.component';

@Component({
  selector: 'qd-ticket-card',
  standalone: true,
  imports: [BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article
      class="ticket-card"
      draggable="true"
      (click)="handleClick($event)"
      (dragstart)="handleDragStart()"
      (dragend)="handleDragEnd()">
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
  private dragging = false;
  private lastDragEndedAt = 0;

  protected handleClick(event: MouseEvent): void {
    if (this.dragging || Date.now() - this.lastDragEndedAt < 250) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }

    this.opened.emit(this.ticket().id);
  }

  protected handleDragStart(): void {
    this.dragging = true;
    this.dragStarted.emit(this.ticket().id);
  }

  protected handleDragEnd(): void {
    this.dragging = false;
    this.lastDragEndedAt = Date.now();
  }

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }
}
