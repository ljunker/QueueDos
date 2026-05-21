import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Priority, Project } from '../../core/api.models';
import { MyTicketsFilters, MyTicketsSort } from '../../state/queue.models';
import { priorityLabel } from '../../state/queue.selectors';

@Component({
  selector: 'qd-my-ticket-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filters my-ticket-filters">
      <input
        type="search"
        placeholder="Search key, title, description"
        [value]="filters().q"
        (input)="change({ q: valueOf($event) })">
      <select [value]="filters().projectId" (change)="change({ projectId: valueOf($event) })">
        <option value="">All projects</option>
        @for (project of projects(); track project.id) {
          <option [value]="project.id">{{ project.key }} - {{ project.name }}</option>
        }
      </select>
      <select [value]="filters().priority" (change)="change({ priority: priorityValue($event) })">
        <option value="">All priorities</option>
        @for (priority of priorities(); track priority) {
          <option [value]="priority">{{ priorityLabel(priority) }}</option>
        }
      </select>
      <input placeholder="Label" [value]="filters().label" (input)="change({ label: valueOf($event) })">
      <select [value]="filters().sort" (change)="change({ sort: sortValue($event) })">
        <option value="number">Ticket number</option>
        <option value="title">Title</option>
        <option value="priority">Priority</option>
        <option value="updated">Updated</option>
      </select>
    </div>
  `
})
export class MyTicketFiltersComponent {
  readonly filters = input.required<MyTicketsFilters>();
  readonly projects = input<Project[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly filtersChanged = output<Partial<MyTicketsFilters>>();

  protected readonly priorityLabel = priorityLabel;

  protected change(filters: Partial<MyTicketsFilters>): void {
    this.filtersChanged.emit(filters);
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected priorityValue(event: Event): Priority | '' {
    return this.valueOf(event) as Priority | '';
  }

  protected sortValue(event: Event): MyTicketsSort {
    return this.valueOf(event) as MyTicketsSort;
  }
}
