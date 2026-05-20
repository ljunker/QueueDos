import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { PublicUser, TicketComment } from '../../core/api.models';
import { userById } from '../../state/queue.selectors';

@Component({
  selector: 'qd-ticket-comments-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Comments</h3>
      <form class="stack" [formGroup]="commentForm" (ngSubmit)="submitComment()">
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
  `
})
export class TicketCommentsPanelComponent {
  readonly comments = input<TicketComment[]>([]);
  readonly users = input<PublicUser[]>([]);

  readonly commentSubmitted = output<string>();

  protected readonly userById = userById;
  protected readonly commentForm = new FormGroup({
    body: new FormControl('', { nonNullable: true })
  });

  protected submitComment(): void {
    const body = this.commentForm.controls.body.value.trim();
    if (!body) return;
    this.commentSubmitted.emit(body);
    this.commentForm.reset();
  }

  protected formatDateTime(value: string): string {
    return new Date(value).toLocaleString();
  }
}
