import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { PublicUser, Ticket, TicketChange, TicketComment, TicketType, Workflow } from '../../core/api.models';
import { TicketCommentsPanelComponent } from './ticket-comments-panel.component';
import { TicketHistoryPanelComponent } from './ticket-history-panel.component';
import { TicketSummaryPanelComponent } from './ticket-summary-panel.component';

@Component({
  selector: 'qd-ticket-detail-view',
  standalone: true,
  imports: [TicketCommentsPanelComponent, TicketHistoryPanelComponent, TicketSummaryPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ticket(); as selectedTicket) {
      <div class="ticket-detail">
        <div class="detail-header">
          <button type="button" (click)="closed.emit()">Back</button>
          <div>
            <p class="eyebrow">{{ selectedTicket.key }}</p>
            <h2>{{ selectedTicket.title }}</h2>
          </div>
          <button type="button" class="primary" (click)="editRequested.emit(selectedTicket.id)">Edit</button>
        </div>

        <div class="detail-grid">
          <qd-ticket-summary-panel [ticket]="selectedTicket" [workflow]="workflow()" [types]="types()" [users]="users()" />
          <qd-ticket-comments-panel
            [comments]="comments()"
            [users]="users()"
            (commentSubmitted)="commentSubmitted.emit({ ticketId: selectedTicket.id, body: $event })" />
          <qd-ticket-history-panel [changes]="changes()" [users]="users()" />
        </div>
      </div>
    } @else {
      <p class="muted">No ticket selected.</p>
    }
  `
})
export class TicketDetailViewComponent {
  readonly ticket = input<Ticket | null>(null);
  readonly comments = input<TicketComment[]>([]);
  readonly changes = input<TicketChange[]>([]);
  readonly workflow = input<Workflow | null>(null);
  readonly types = input<TicketType[]>([]);
  readonly users = input<PublicUser[]>([]);

  readonly closed = output<void>();
  readonly editRequested = output<string>();
  readonly commentSubmitted = output<{ ticketId: string; body: string }>();
}
