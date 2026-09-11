import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import {ProjectRole, PublicUser, Ticket, TicketType, Workflow, WorkflowStatus} from '../../core/api.models';
import {priorityRank, statusRank, typeById, userById} from '../../state/queue.selectors';
import { TicketCardComponent } from '../molecules/ticket-card.component';

type BoardTicketSort = 'number' | 'priority' | 'label';

@Component({
  selector: 'qd-board-view',
  standalone: true,
  imports: [TicketCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    .board-controls {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 12px;
    }

    .board-sort {
      width: min(100%, 220px);
    }
  `],
  template: `
    @if (!workflow()) {
      <p class="muted">No workflow configured.</p>
    } @else {
      <div class="board-controls">
        <label class="board-sort">
          <span>Sort tickets by</span>
          <select [value]="sortMode" (change)="changeSort($event)">
            <option value="number">Ticket number</option>
            <option value="priority">Priority</option>
            <option value="label">Label</option>
          </select>
        </label>
      </div>
      <div class="board" aria-live="polite">
        @for (status of statuses(); track status.id) {
          <section
            class="column"
            [class.drag-over]="dragOverStatusId === status.id"
            (dragover)="allowDrop($event, status.id)"
            (dragleave)="dragOverStatusId = null"
            (drop)="dropOnStatus($event, status.id)">
            <header class="column-header">
              <span>{{ status.name }}</span>
              <span class="badge">{{ ticketsForStatus(status.id).length }}</span>
            </header>
            <div class="column-body">
              @for (ticket of ticketsForStatus(status.id); track ticket.id) {
                <qd-ticket-card
                  [ticket]="ticket"
                  [type]="typeById(types(), ticket.typeId)"
                  [assignee]="userById(users(), ticket.assigneeId)"
                  (opened)="ticketOpened.emit($event)"
                  (dragStarted)="draggedTicketId = $event" />
              } @empty {
                <p class="muted">No tickets</p>
              }
            </div>
          </section>
        }
      </div>
    }
  `
})
export class BoardViewComponent {
  readonly workflow = input<Workflow | null>(null);
  readonly statuses = input<WorkflowStatus[]>([]);
  readonly tickets = input<Ticket[]>([]);
  readonly types = input<TicketType[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly currentRole = input<ProjectRole>('MEMBER');

  readonly ticketOpened = output<string>();
  readonly ticketTransitioned = output<{ ticket: Ticket; toStatusId: string }>();
  readonly transitionDenied = output<void>();

  protected draggedTicketId: string | null = null;
  protected dragOverStatusId: string | null = null;
  protected sortMode: BoardTicketSort = 'number';
  protected readonly typeById = typeById;
  protected readonly userById = userById;

  protected ticketsForStatus(statusId: string): Ticket[] {
    return this.tickets()
      .filter((ticket) => ticket.statusId === statusId)
      .sort((left, right) => this.compareTickets(left, right));
  }

  protected changeSort(event: Event): void {
    this.sortMode = (event.target as HTMLSelectElement).value as BoardTicketSort;
  }

  protected allowDrop(event: DragEvent, statusId: string): void {
    event.preventDefault();
    this.dragOverStatusId = statusId;
  }

  protected dropOnStatus(event: DragEvent, statusId: string): void {
    event.preventDefault();
    this.dragOverStatusId = null;
    const ticket = this.tickets().find((item) => item.id === this.draggedTicketId);
    this.draggedTicketId = null;
    if (!ticket || ticket.statusId === statusId) return;
    if (!this.canTransition(ticket, statusId)) {
      this.transitionDenied.emit();
      return;
    }
    this.ticketTransitioned.emit({ ticket, toStatusId: statusId });
  }

  private canTransition(ticket: Ticket, toStatusId: string): boolean {
    const workflow = this.workflow();
    if (!workflow) return false;
    return workflow.transitions.some((transition) => {
      const backward = statusRank(workflow, toStatusId) < statusRank(workflow, ticket.statusId);
      return (
        (transition.globalTransition || transition.fromStatusId === ticket.statusId) &&
        transition.toStatusId === toStatusId &&
        (transition.allowedRoles.includes(this.currentRole()) ||
          (this.currentRole() === 'ADMIN' && transition.allowedRoles.includes('MEMBER'))) &&
        (!backward || transition.allowBackward !== false)
      );
    });
  }

  private compareTickets(left: Ticket, right: Ticket): number {
    if (this.sortMode === 'priority') {
      return priorityRank(right.priority) - priorityRank(left.priority) || left.number - right.number;
    }
    if (this.sortMode === 'label') {
      const leftLabel = labelSortKey(left.labels);
      const rightLabel = labelSortKey(right.labels);
      if (!leftLabel && rightLabel) return 1;
      if (leftLabel && !rightLabel) return -1;
      return leftLabel.localeCompare(rightLabel) || left.number - right.number;
    }
    return left.number - right.number;
  }
}

function labelSortKey(labels: string[]): string {
  return labels
    .map((label) => label.trim().toLocaleLowerCase())
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right))
    .join('\u0000');
}
