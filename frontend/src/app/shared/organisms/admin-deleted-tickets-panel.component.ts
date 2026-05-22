import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {Ticket} from '../../core/api.models';

@Component({
  selector: 'qd-admin-deleted-tickets-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Deleted tickets</h3>
      <div class="admin-list">
        @for (ticket of tickets(); track ticket.id) {
          <div class="admin-item">
            <div>
              <strong>{{ ticket.key }} · {{ ticket.title }}</strong>
              <small>Deleted {{ ticket.deletedAt }}</small>
            </div>
            <button type="button" (click)="ticketRestored.emit(ticket.id)">Restore</button>
          </div>
        } @empty {
          <p class="muted">No deleted tickets.</p>
        }
      </div>
    </section>
  `
})
export class AdminDeletedTicketsPanelComponent {
  readonly tickets = input<Ticket[]>([]);
  readonly ticketRestored = output<string>();
}
