import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

import {
  BulkUpdateTicketsRequest,
  Priority,
  Project,
  PublicUser,
  Ticket,
  TicketType,
  Workflow
} from '../../core/api.models';
import { priorityLabel, typeById, userById } from '../../state/queue.selectors';
import { BadgeComponent } from '../atoms/badge.component';

@Component({
  selector: 'qd-ticket-table',
  standalone: true,
  imports: [BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="bulk-bar" aria-label="Bulk ticket actions">
      <strong>{{ selectedCount() }} selected</strong>
      <select aria-label="Bulk assignee" [value]="assigneeId()" (change)="assigneeId.set(valueOf($event))">
        <option value="">Choose assignee</option>
        @for (user of activeUsers(); track user.id) {
          <option [value]="user.id">{{ user.displayName }}</option>
        }
      </select>
      <button type="button" [disabled]="!selectedCount() || !assigneeId()" (click)="assign()">Assign</button>
      <button type="button" [disabled]="!selectedCount()" (click)="clearAssignee()">Clear assignee</button>
      <select aria-label="Bulk priority" [value]="priority()" (change)="priority.set(priorityValue($event))">
        <option value="">Choose priority</option>
        @for (item of priorities(); track item) {
          <option [value]="item">{{ priorityLabel(item) }}</option>
        }
      </select>
      <button type="button" [disabled]="!selectedCount() || !priority()" (click)="setPriority()">Set priority</button>
    </section>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th class="select-column">
              <input
                type="checkbox"
                aria-label="Select visible tickets"
                [checked]="allSelected()"
                [disabled]="!tickets().length"
                (change)="toggleAll(checkedOf($event))">
            </th>
            <th>Key</th>
            @if (showProject()) {
              <th>Project</th>
            }
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
              <td class="select-column" (click)="$event.stopPropagation()">
                <input
                  type="checkbox"
                  [attr.aria-label]="'Select ' + ticket.key"
                  [checked]="isSelected(ticket.id)"
                  (change)="toggleTicket(ticket.id, checkedOf($event))">
              </td>
              <td><strong>{{ ticket.key }}</strong></td>
              @if (showProject()) {
                <td>{{ projectById(ticket.projectId)?.key ?? '' }}</td>
              }
              <td>{{ ticket.title }}</td>
              <td>{{ typeById(types(), ticket.typeId)?.name ?? '' }}</td>
              <td><qd-badge [variant]="priorityVariant(ticket.priority)">{{ priorityLabel(ticket.priority) }}</qd-badge></td>
              <td>{{ statusName(ticket) }}</td>
              <td>{{ userById(allUsers(), ticket.assigneeId)?.displayName ?? 'Unassigned' }}</td>
              <td>{{ ticket.dueDate ?? '' }}</td>
              <td>{{ ticket.estimate ?? '' }}</td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="showProject() ? 10 : 9" class="muted">No tickets match the current filters.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class TicketTableComponent {
  readonly tickets = input<Ticket[]>([]);
  readonly projects = input<Project[]>([]);
  readonly types = input<TicketType[]>([]);
  readonly workflows = input<Workflow[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly activeUsers = input<PublicUser[]>([]);
  readonly allUsers = input<PublicUser[]>([]);
  readonly showProject = input(false);

  readonly ticketOpened = output<string>();
  readonly bulkUpdateRequested = output<BulkUpdateTicketsRequest>();

  protected readonly assigneeId = signal('');
  protected readonly priority = signal<Priority | ''>('');
  protected readonly selectedIds = signal<Set<string>>(new Set());
  protected readonly priorityLabel = priorityLabel;
  protected readonly typeById = typeById;
  protected readonly userById = userById;

  protected selectedTicketIds(): string[] {
    return this.tickets().filter((ticket) => this.selectedIds().has(ticket.id)).map((ticket) => ticket.id);
  }

  protected selectedCount(): number {
    return this.selectedTicketIds().length;
  }

  protected allSelected(): boolean {
    return Boolean(this.tickets().length) && this.tickets().every((ticket) => this.selectedIds().has(ticket.id));
  }

  protected isSelected(ticketId: string): boolean {
    return this.selectedIds().has(ticketId);
  }

  protected toggleTicket(ticketId: string, checked: boolean): void {
    const selected = new Set(this.selectedIds());
    if (checked) {
      selected.add(ticketId);
    } else {
      selected.delete(ticketId);
    }
    this.selectedIds.set(selected);
  }

  protected toggleAll(checked: boolean): void {
    const selected = new Set(this.selectedIds());
    this.tickets().forEach((ticket) => {
      if (checked) {
        selected.add(ticket.id);
      } else {
        selected.delete(ticket.id);
      }
    });
    this.selectedIds.set(selected);
  }

  protected assign(): void {
    const assigneeId = this.assigneeId();
    if (!assigneeId) return;
    this.emitBulkUpdate({ ticketIds: this.selectedTicketIds(), assigneeId });
  }

  protected clearAssignee(): void {
    this.emitBulkUpdate({ ticketIds: this.selectedTicketIds(), clearAssignee: true });
  }

  protected setPriority(): void {
    const priority = this.priority();
    if (!priority) return;
    this.emitBulkUpdate({ ticketIds: this.selectedTicketIds(), priority });
  }

  protected projectById(projectId: string): Project | null {
    return this.projects().find((project) => project.id === projectId) ?? null;
  }

  protected statusName(ticket: Ticket): string {
    return this.workflows()
      .find((workflow) => workflow.projectId === ticket.projectId)
      ?.statuses.find((status) => status.id === ticket.statusId)?.name ?? '';
  }

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  protected checkedOf(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected priorityValue(event: Event): Priority | '' {
    return this.valueOf(event) as Priority | '';
  }

  private emitBulkUpdate(request: BulkUpdateTicketsRequest): void {
    if (!request.ticketIds.length) return;
    this.bulkUpdateRequested.emit(request);
    this.selectedIds.set(new Set());
  }
}
