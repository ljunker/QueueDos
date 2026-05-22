import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {PublicUser, Ticket, TicketType, Workflow} from '../../core/api.models';
import {priorityLabel, statusById, typeById, userById} from '../../state/queue.selectors';
import {BadgeComponent} from '../atoms/badge.component';

@Component({
  selector: 'qd-ticket-summary-panel',
  standalone: true,
  imports: [BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Details</h3>
      <p>{{ ticket().description || 'No description' }}</p>
      <div class="badges">
        <qd-badge>{{ statusById(workflow(), ticket().statusId)?.name ?? 'Status' }}</qd-badge>
        <qd-badge [dotColor]="typeById(types(), ticket().typeId)?.color ?? '#667085'">
          {{ typeById(types(), ticket().typeId)?.name ?? 'Type' }}
        </qd-badge>
        <qd-badge [variant]="priorityVariant(ticket().priority)">{{ priorityLabel(ticket().priority) }}</qd-badge>
        @for (label of ticket().labels; track label) {
          <qd-badge>{{ label }}</qd-badge>
        }
      </div>
      <dl class="meta-grid">
        <div>
          <dt>Assignee</dt>
          <dd>{{ userById(users(), ticket().assigneeId)?.displayName ?? 'Unassigned' }}</dd>
        </div>
        <div>
          <dt>Reporter</dt>
          <dd>{{ userById(users(), ticket().reporterId)?.displayName ?? '' }}</dd>
        </div>
        <div>
          <dt>Due</dt>
          <dd>{{ ticket().dueDate || '-' }}</dd>
        </div>
        <div>
          <dt>Estimate</dt>
          <dd>{{ ticket().estimate ?? '-' }}</dd>
        </div>
        <div>
          <dt>Committed</dt>
          <dd>{{ commitmentNames() || '-' }}</dd>
        </div>
      </dl>
      @if (currentUser(); as user) {
        <button type="button" (click)="commitmentChanged.emit(!isCommitted(user.id))">
          {{ isCommitted(user.id) ? 'Leave commitment' : 'Commit to ticket' }}
        </button>
      }
    </section>
  `
})
export class TicketSummaryPanelComponent {
  readonly ticket = input.required<Ticket>();
  readonly workflow = input<Workflow | null>(null);
  readonly types = input<TicketType[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly currentUser = input<PublicUser | null>(null);

  readonly commitmentChanged = output<boolean>();

  protected readonly priorityLabel = priorityLabel;
  protected readonly statusById = statusById;
  protected readonly typeById = typeById;
  protected readonly userById = userById;

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }

  protected isCommitted(userId: string): boolean {
    return this.ticket().committedUserIds.includes(userId);
  }

  protected commitmentNames(): string {
    return this.ticket().committedUserIds
    .map((userId) => userById(this.users(), userId)?.displayName ?? '')
    .filter(Boolean)
    .join(', ');
  }
}
