import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { PublicUser, TicketChange, TicketRevisionPage, TicketRevisionSummary } from '../../core/api.models';
import { userById } from '../../state/queue.selectors';

@Component({
  selector: 'qd-ticket-history-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel wide">
      <h3>History</h3>
      <div class="timeline">
        @for (revision of page().revisions; track revision.id) {
          <article class="timeline-item" role="button" tabindex="0"
                   (click)="revisionOpened.emit(revision)"
                   (keydown.enter)="revisionOpened.emit(revision)">
            <strong>v{{ revision.version }} · {{ actionLabel(revision) }}</strong>
            <small>{{ actorName(revision) }} · {{ formatDateTime(revision.createdAt) }}</small>
            @for (change of revision.changes; track change.field) {
              <p>{{ change.field }}: {{ value(change.oldValue) }} → {{ value(change.newValue) }}</p>
            }
          </article>
        } @empty {
          <p class="muted">No revisions</p>
        }
      </div>
      @if (page().nextBeforeVersion; as beforeVersion) {
        <button type="button" (click)="olderRequested.emit(beforeVersion)">Load older</button>
      }
      @if (legacyChanges().length) {
        <details>
          <summary>Legacy history ({{ legacyChanges().length }})</summary>
          <div class="timeline">
            @for (change of legacyChanges(); track change.id) {
              <article class="timeline-item">
                <strong>{{ userById(users(), change.actorId)?.displayName ?? 'User' }}</strong>
                <small>{{ formatDateTime(change.createdAt) }}</small>
                <p>{{ changeText(change) }}</p>
              </article>
            }
          </div>
        </details>
      }
    </section>
  `
})
export class TicketHistoryPanelComponent {
  readonly page = input<TicketRevisionPage>({revisions: [], nextBeforeVersion: null});
  readonly legacyChanges = input<TicketChange[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly revisionOpened = output<TicketRevisionSummary>();
  readonly olderRequested = output<number>();

  protected readonly userById = userById;

  protected formatDateTime(value: string): string {
    return new Date(value).toLocaleString();
  }

  protected changeText(change: TicketChange): string {
    if (change.oldValue === null && change.newValue === 'created') {
      return 'created ticket';
    }
    return `${change.field}: ${change.oldValue ?? '-'} -> ${change.newValue ?? '-'}`;
  }

  protected actionLabel(revision: TicketRevisionSummary): string {
    return revision.action.toLowerCase().replaceAll('_', ' ');
  }

  protected actorName(revision: TicketRevisionSummary): string {
    return revision.actorId ? userById(this.users(), revision.actorId)?.displayName ?? 'User' : 'System';
  }

  protected value(value: unknown): string {
    if (value === null || value === undefined || value === '') return '–';
    if (Array.isArray(value)) return value.join(', ') || '–';
    return String(value);
  }
}
