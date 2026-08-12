import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {PublicUser, Ticket, TicketChange, TicketComment, TicketRevisionDetail, TicketRevisionPage, TicketRevisionSummary, TicketType, Workflow} from '../../core/api.models';
import {TicketCommentsPanelComponent} from './ticket-comments-panel.component';
import {TicketHistoryPanelComponent} from './ticket-history-panel.component';
import {TicketSummaryPanelComponent} from './ticket-summary-panel.component';

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
          <qd-ticket-summary-panel
            [ticket]="selectedTicket"
            [workflow]="workflow()"
            [types]="types()"
            [users]="users()"
            [currentUser]="currentUser()"
            (commitmentChanged)="commitmentChanged.emit({ ticketId: selectedTicket.id, committed: $event, expectedVersion: selectedTicket.version })" />
          <qd-ticket-comments-panel
            [comments]="comments()"
            [users]="users()"
            (commentSubmitted)="commentSubmitted.emit({ ticketId: selectedTicket.id, body: $event })" />
          <qd-ticket-history-panel
            [page]="revisions()"
            [legacyChanges]="legacyChanges()"
            [users]="users()"
            (revisionOpened)="revisionOpened.emit($event)"
            (olderRequested)="olderRevisionsRequested.emit($event)" />
        </div>
        @if (openedRevision(); as detail) {
          <div class="dialog-backdrop" (click)="revisionClosed.emit()">
            <section class="dialog" (click)="$event.stopPropagation()">
              <header>
                <h3>{{ selectedTicket.key }} · Revision {{ detail.revision.version }}</h3>
                <button type="button" class="icon-button" (click)="revisionClosed.emit()">x</button>
              </header>
              <div class="dialog-body">
                <p><strong>{{ detail.snapshot.title }}</strong></p>
                <p>{{ detail.snapshot.description || 'No description' }}</p>
                <dl>
                  <dt>Status</dt><dd>{{ detail.snapshot.statusId }}</dd>
                  <dt>Type</dt><dd>{{ detail.snapshot.typeId }}</dd>
                  <dt>Priority</dt><dd>{{ detail.snapshot.priority }}</dd>
                  <dt>Assignee</dt><dd>{{ detail.snapshot.assigneeId ?? '–' }}</dd>
                  <dt>Labels</dt><dd>{{ detail.snapshot.labels.join(', ') || '–' }}</dd>
                  <dt>Committed users</dt><dd>{{ detail.snapshot.committedUserIds.join(', ') || '–' }}</dd>
                  <dt>Due date</dt><dd>{{ detail.snapshot.dueDate ?? '–' }}</dd>
                  <dt>Estimate</dt><dd>{{ detail.snapshot.estimate ?? '–' }}</dd>
                </dl>
              </div>
              <footer>
                <button type="button" (click)="revisionClosed.emit()">Close</button>
                @if (isAdmin() && detail.revision.restorable && detail.revision.version !== selectedTicket.version) {
                  <button type="button" class="primary" (click)="restore(detail)">Restore this revision</button>
                }
              </footer>
            </section>
          </div>
        }
      </div>
    } @else {
      <p class="muted">No ticket selected.</p>
    }
  `
})
export class TicketDetailViewComponent {
  readonly ticket = input<Ticket | null>(null);
  readonly comments = input<TicketComment[]>([]);
  readonly revisions = input<TicketRevisionPage>({revisions: [], nextBeforeVersion: null});
  readonly legacyChanges = input<TicketChange[]>([]);
  readonly openedRevision = input<TicketRevisionDetail | null>(null);
  readonly workflow = input<Workflow | null>(null);
  readonly types = input<TicketType[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly currentUser = input<PublicUser | null>(null);
  readonly isAdmin = input(false);

  readonly closed = output<void>();
  readonly editRequested = output<string>();
  readonly commentSubmitted = output<{ ticketId: string; body: string }>();
  readonly commitmentChanged = output<{ ticketId: string; committed: boolean; expectedVersion: number }>();
  readonly revisionOpened = output<TicketRevisionSummary>();
  readonly revisionClosed = output<void>();
  readonly olderRevisionsRequested = output<number>();
  readonly revisionRestoreRequested = output<{ticketId: string; version: number; expectedVersion: number}>();

  protected restore(detail: TicketRevisionDetail): void {
    const ticket = this.ticket();
    if (!ticket || !confirm(`Restore revision ${detail.revision.version} as a new ticket version?`)) return;
    this.revisionRestoreRequested.emit({ticketId: ticket.id, version: detail.revision.version, expectedVersion: ticket.version});
  }
}
