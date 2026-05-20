import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Priority, PublicUser, Ticket, TicketType, Workflow, WorkflowStatus } from '../../core/api.models';
import { priorityLabel, statusById, typeById, userById } from '../../state/queue.selectors';
import { TicketFilters } from '../../state/queue.models';
import { BadgeComponent } from '../atoms/badge.component';
import { TicketFiltersComponent } from '../molecules/ticket-filters.component';

@Component({
  selector: 'qd-ticket-list-view',
  standalone: true,
  imports: [BadgeComponent, TicketFiltersComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <qd-ticket-filters
      [filters]="filters()"
      [statuses]="statuses()"
      [types]="types()"
      [priorities]="priorities()"
      [users]="users()"
      (filtersChanged)="filtersChanged.emit($event)" />

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Key</th>
            <th>Title</th>
            <th>Type</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Assignee</th>
            <th>Due</th>
            <th>Estimate</th>
          </tr>
        </thead>
        <tbody>
          @for (ticket of tickets(); track ticket.id) {
            <tr (click)="ticketOpened.emit(ticket.id)">
              <td><strong>{{ ticket.key }}</strong></td>
              <td>{{ ticket.title }}</td>
              <td>{{ typeById(types(), ticket.typeId)?.name ?? '' }}</td>
              <td><qd-badge [variant]="priorityVariant(ticket.priority)">{{ priorityLabel(ticket.priority) }}</qd-badge></td>
              <td>{{ statusById(workflow(), ticket.statusId)?.name ?? '' }}</td>
              <td>{{ userById(allUsers(), ticket.assigneeId)?.displayName ?? 'Unassigned' }}</td>
              <td>{{ ticket.dueDate ?? '' }}</td>
              <td>{{ ticket.estimate ?? '' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="8" class="muted">No tickets match the current filters.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class TicketListViewComponent {
  readonly tickets = input<Ticket[]>([]);
  readonly filters = input.required<TicketFilters>();
  readonly statuses = input<WorkflowStatus[]>([]);
  readonly workflow = input<Workflow | null>(null);
  readonly types = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly allUsers = input<PublicUser[]>([]);

  readonly filtersChanged = output<Partial<TicketFilters>>();
  readonly ticketOpened = output<string>();

  protected readonly priorityLabel = priorityLabel;
  protected readonly statusById = statusById;
  protected readonly typeById = typeById;
  protected readonly userById = userById;

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }
}
