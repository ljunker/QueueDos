import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Priority, PublicUser, TicketType, WorkflowStatus } from '../../core/api.models';
import { priorityLabel } from '../../state/queue.selectors';
import { TicketFilters, TicketSort } from '../../state/queue.models';

@Component({
  selector: 'qd-ticket-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filters">
      <input
        type="search"
        placeholder="Search key, title, description"
        [value]="filters().q"
        (input)="change({ q: valueOf($event) })">
      <select [value]="filters().statusId" (change)="change({ statusId: valueOf($event) })">
        <option value="">All statuses</option>
        @for (status of statuses(); track status.id) {
          <option [value]="status.id">{{ status.name }}</option>
        }
      </select>
      <select [value]="filters().typeId" (change)="change({ typeId: valueOf($event) })">
        <option value="">All types</option>
        @for (type of types(); track type.id) {
          <option [value]="type.id">{{ type.name }}</option>
        }
      </select>
      <select [value]="filters().priority" (change)="change({ priority: priorityValue($event) })">
        <option value="">All priorities</option>
        @for (priority of priorities(); track priority) {
          <option [value]="priority">{{ priorityLabel(priority) }}</option>
        }
      </select>
      <select [value]="filters().assigneeId" (change)="change({ assigneeId: valueOf($event) })">
        <option value="">All assignees</option>
        <option value="unassigned">Unassigned</option>
        @for (user of users(); track user.id) {
          <option [value]="user.id">{{ user.displayName }}</option>
        }
      </select>
      <input placeholder="Label" [value]="filters().label" (input)="change({ label: valueOf($event) })">
      <select [value]="filters().sort" (change)="change({ sort: sortValue($event) })">
        <option value="number">Ticket number</option>
        <option value="title">Title</option>
        <option value="priority">Priority</option>
        <option value="status">Status</option>
        <option value="updated">Updated</option>
      </select>
    </div>
  `
})
export class TicketFiltersComponent {
  readonly filters = input.required<TicketFilters>();
  readonly statuses = input<WorkflowStatus[]>([]);
  readonly types = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly filtersChanged = output<Partial<TicketFilters>>();

  protected readonly priorityLabel = priorityLabel;

  protected change(filters: Partial<TicketFilters>): void {
    this.filtersChanged.emit(filters);
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected priorityValue(event: Event): Priority | '' {
    return this.valueOf(event) as Priority | '';
  }

  protected sortValue(event: Event): TicketSort {
    return this.valueOf(event) as TicketSort;
  }
}
