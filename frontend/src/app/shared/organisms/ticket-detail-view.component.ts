import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { PublicUser, Ticket, TicketChange, TicketComment, TicketType, Workflow } from '../../core/api.models';
import { priorityLabel, statusById, typeById, userById } from '../../state/queue.selectors';
import { BadgeComponent } from '../atoms/badge.component';

@Component({
  selector: 'qd-ticket-detail-view',
  standalone: true,
  imports: [BadgeComponent, ReactiveFormsModule],
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
          <section class="panel">
            <h3>Details</h3>
            <p>{{ selectedTicket.description || 'No description' }}</p>
            <div class="badges">
              <qd-badge>{{ statusById(workflow(), selectedTicket.statusId)?.name ?? 'Status' }}</qd-badge>
              <qd-badge [dotColor]="typeById(types(), selectedTicket.typeId)?.color ?? '#667085'">
                {{ typeById(types(), selectedTicket.typeId)?.name ?? 'Type' }}
              </qd-badge>
              <qd-badge [variant]="priorityVariant(selectedTicket.priority)">{{ priorityLabel(selectedTicket.priority) }}</qd-badge>
              @for (label of selectedTicket.labels; track label) {
                <qd-badge>{{ label }}</qd-badge>
              }
            </div>
            <dl class="meta-grid">
              <div>
                <dt>Assignee</dt>
                <dd>{{ userById(users(), selectedTicket.assigneeId)?.displayName ?? 'Unassigned' }}</dd>
              </div>
              <div>
                <dt>Reporter</dt>
                <dd>{{ userById(users(), selectedTicket.reporterId)?.displayName ?? '' }}</dd>
              </div>
              <div>
                <dt>Due</dt>
                <dd>{{ selectedTicket.dueDate || '-' }}</dd>
              </div>
              <div>
                <dt>Estimate</dt>
                <dd>{{ selectedTicket.estimate ?? '-' }}</dd>
              </div>
            </dl>
          </section>

          <section class="panel">
            <h3>Comments</h3>
            <form class="stack" [formGroup]="commentForm" (ngSubmit)="submitComment(selectedTicket.id)">
              <textarea rows="3" placeholder="Add a comment" formControlName="body"></textarea>
              <button type="submit" [disabled]="!commentForm.controls.body.value.trim()">Add comment</button>
            </form>
            <div class="timeline">
              @for (comment of comments(); track comment.id) {
                <article class="timeline-item">
                  <strong>{{ userById(users(), comment.authorId)?.displayName ?? 'User' }}</strong>
                  <small>{{ formatDateTime(comment.createdAt) }}</small>
                  <p>{{ comment.body }}</p>
                </article>
              } @empty {
                <p class="muted">No comments</p>
              }
            </div>
          </section>

          <section class="panel wide">
            <h3>History</h3>
            <div class="timeline">
              @for (change of changes(); track change.id) {
                <article class="timeline-item">
                  <strong>{{ userById(users(), change.actorId)?.displayName ?? 'User' }}</strong>
                  <small>{{ formatDateTime(change.createdAt) }}</small>
                  <p>{{ changeText(change) }}</p>
                </article>
              } @empty {
                <p class="muted">No changes</p>
              }
            </div>
          </section>
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

  protected readonly priorityLabel = priorityLabel;
  protected readonly statusById = statusById;
  protected readonly typeById = typeById;
  protected readonly userById = userById;
  protected readonly commentForm = new FormGroup({
    body: new FormControl('', { nonNullable: true })
  });

  protected submitComment(ticketId: string): void {
    const body = this.commentForm.controls.body.value.trim();
    if (!body) return;
    this.commentSubmitted.emit({ ticketId, body });
    this.commentForm.reset();
  }

  protected priorityVariant(priority: Ticket['priority']): 'critical' | 'high' | 'medium' | 'low' {
    return priority.toLowerCase() as 'critical' | 'high' | 'medium' | 'low';
  }

  protected formatDateTime(value: string): string {
    return new Date(value).toLocaleString();
  }

  protected changeText(change: TicketChange): string {
    if (change.oldValue === null && change.newValue === 'created') {
      return 'created ticket';
    }
    return `${change.field}: ${change.oldValue ?? '-'} -> ${change.newValue ?? '-'}`;
  }
}
