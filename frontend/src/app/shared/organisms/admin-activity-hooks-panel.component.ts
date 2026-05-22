import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

import {
  ActivityEventType,
  ActivityHook,
  CreateActivityHookRequest,
  UpdateActivityHookRequest
} from '../../core/api.models';

@Component({
  selector: 'qd-admin-activity-hooks-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel activity-hooks">
      <h3>Slack activity hooks</h3>
      <form class="stack" [formGroup]="hookForm" (ngSubmit)="createHook()">
        <select formControlName="eventType" aria-label="Slack hook event">
          @for (event of eventTypes; track event) {
            <option [value]="event">{{ eventLabel(event) }}</option>
          }
        </select>
        <input type="url" placeholder="https://hooks.slack.com/services/..." formControlName="webhookUrl">
        <textarea rows="2" placeholder="{{ templateHint }}" formControlName="messageTemplate"></textarea>
        <label class="inline-check">
          <input type="checkbox" formControlName="active">
          Active
        </label>
        <button type="submit" [disabled]="hookForm.invalid">Add hook</button>
      </form>

      <div class="admin-list">
        @for (hook of hooks(); track hook.id) {
          <div class="admin-item hook-item">
            <div class="hook-fields">
              <select
                aria-label="Configured Slack hook event"
                [value]="hook.eventType"
                (change)="hookUpdated.emit({ hookId: hook.id, request: { eventType: eventValue($event) } })">
                @for (event of eventTypes; track event) {
                  <option [value]="event">{{ eventLabel(event) }}</option>
                }
              </select>
              <input
                aria-label="Slack webhook URL"
                [value]="hook.webhookUrl"
                (change)="hookUpdated.emit({ hookId: hook.id, request: { webhookUrl: valueOf($event) } })">
              <textarea
                aria-label="Slack message template"
                rows="2"
                [value]="hook.messageTemplate"
                (change)="hookUpdated.emit({ hookId: hook.id, request: { messageTemplate: valueOf($event) } })"></textarea>
              <label class="inline-check">
                <input
                  type="checkbox"
                  [checked]="hook.active"
                  (change)="hookUpdated.emit({ hookId: hook.id, request: { active: checkedOf($event) } })">
                Active
              </label>
            </div>
            <button type="button" class="danger" (click)="hookDeleted.emit(hook.id)">Delete</button>
          </div>
        } @empty {
          <p class="muted">No Slack hooks configured.</p>
        }
      </div>
    </section>
  `
})
export class AdminActivityHooksPanelComponent {
  readonly hooks = input<ActivityHook[]>([]);
  readonly hookCreated = output<CreateActivityHookRequest>();
  readonly hookUpdated = output<{ hookId: string; request: UpdateActivityHookRequest }>();
  readonly hookDeleted = output<string>();

  protected readonly eventTypes: ActivityEventType[] = [
    'TICKET_CREATED',
    'TICKET_UPDATED',
    'TICKET_MOVED',
    'COMMENT_ADDED',
    'COMMITMENT_CHANGED',
    'TICKET_DELETED',
    'TICKET_RESTORED'
  ];
  protected readonly templateHint = '{{actorName}} moved {{ticketKey}}';
  protected readonly hookForm = new FormGroup({
    eventType: new FormControl<ActivityEventType>('TICKET_MOVED', {nonNullable: true}),
    webhookUrl: new FormControl('', {nonNullable: true, validators: [Validators.required]}),
    messageTemplate: new FormControl(this.templateHint, {nonNullable: true, validators: [Validators.required]}),
    active: new FormControl(true, {nonNullable: true})
  });

  protected createHook(): void {
    if (this.hookForm.invalid) return;
    this.hookCreated.emit(this.hookForm.getRawValue());
    this.hookForm.reset({
      eventType: 'TICKET_MOVED',
      webhookUrl: '',
      messageTemplate: this.templateHint,
      active: true
    });
  }

  protected eventLabel(event: ActivityEventType): string {
    return event.split('_').map((word) => `${word[0]}${word.slice(1).toLowerCase()}`).join(' ');
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  protected checkedOf(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected eventValue(event: Event): ActivityEventType {
    return (event.target as HTMLSelectElement).value as ActivityEventType;
  }
}
