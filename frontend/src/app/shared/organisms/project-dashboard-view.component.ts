import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Priority, PublicUser, Ticket, WorkflowStatus } from '../../core/api.models';
import { TicketFilters } from '../../state/queue.models';
import { priorityLabel } from '../../state/queue.selectors';

@Component({
  selector: 'qd-project-dashboard-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dashboard">
      <section class="dashboard-band">
        <h3>Status</h3>
        <div class="metric-grid">
          @for (status of statuses(); track status.id) {
            <button type="button" class="metric" (click)="drilldown({ statusId: status.id })">
              <span>{{ status.name }}</span>
              <strong>{{ statusCount(status.id) }}</strong>
            </button>
          }
        </div>
      </section>

      <section class="dashboard-band">
        <h3>Priority</h3>
        <div class="metric-grid">
          @for (priority of priorities(); track priority) {
            <button type="button" class="metric" (click)="drilldown({ priority })">
              <span>{{ priorityLabel(priority) }}</span>
              <strong>{{ priorityCount(priority) }}</strong>
            </button>
          }
        </div>
      </section>

      <section class="dashboard-band">
        <h3>Assignee</h3>
        <div class="metric-grid assignees">
          <button type="button" class="metric" (click)="drilldown({ assigneeId: 'unassigned' })">
            <span>Unassigned</span>
            <strong>{{ assigneeCount(null) }}</strong>
          </button>
          @for (user of users(); track user.id) {
            <button type="button" class="metric" (click)="drilldown({ assigneeId: user.id })">
              <span>{{ user.displayName }}</span>
              <strong>{{ assigneeCount(user.id) }}</strong>
            </button>
          }
        </div>
      </section>
    </section>
  `
})
export class ProjectDashboardViewComponent {
  readonly tickets = input<Ticket[]>([]);
  readonly statuses = input<WorkflowStatus[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly users = input<PublicUser[]>([]);

  readonly filtersChanged = output<Partial<TicketFilters>>();
  protected readonly priorityLabel = priorityLabel;

  protected statusCount(statusId: string): number {
    return this.tickets().filter((ticket) => ticket.statusId === statusId).length;
  }

  protected priorityCount(priority: Priority): number {
    return this.tickets().filter((ticket) => ticket.priority === priority).length;
  }

  protected assigneeCount(assigneeId: string | null): number {
    return this.tickets().filter((ticket) => ticket.assigneeId === assigneeId).length;
  }

  protected drilldown(filters: Partial<TicketFilters>): void {
    this.filtersChanged.emit({
      q: '',
      statusId: '',
      typeId: '',
      priority: '',
      assigneeId: '',
      label: '',
      sort: 'number',
      ...filters
    });
  }
}
