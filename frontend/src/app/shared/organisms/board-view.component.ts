import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { PublicUser, Role, Ticket, TicketType, Workflow, WorkflowStatus } from '../../core/api.models';
import { statusRank, typeById, userById } from '../../state/queue.selectors';
import { TicketCardComponent } from '../molecules/ticket-card.component';

@Component({
  selector: 'qd-board-view',
  standalone: true,
  imports: [TicketCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!workflow()) {
      <p class="muted">No workflow configured.</p>
    } @else {
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
  readonly currentRole = input<Role>('MEMBER');

  readonly ticketOpened = output<string>();
  readonly ticketTransitioned = output<{ ticket: Ticket; toStatusId: string }>();
  readonly transitionDenied = output<void>();

  protected draggedTicketId: string | null = null;
  protected dragOverStatusId: string | null = null;
  protected readonly typeById = typeById;
  protected readonly userById = userById;

  protected ticketsForStatus(statusId: string): Ticket[] {
    return this.tickets()
      .filter((ticket) => ticket.statusId === statusId)
      .sort((left, right) => left.number - right.number);
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
        transition.allowedRoles.includes(this.currentRole()) &&
        (!backward || transition.allowBackward !== false)
      );
    });
  }
}
