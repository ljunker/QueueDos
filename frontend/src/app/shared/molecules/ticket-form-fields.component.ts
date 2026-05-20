import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { Priority, PublicUser, TicketType, WorkflowStatus } from '../../core/api.models';
import { TicketFormGroup } from '../models/ticket-form.model';

@Component({
  selector: 'qd-ticket-form-fields',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ticket-form-fields" [formGroup]="form()">
      <label>
        Title
        <input formControlName="title" required>
      </label>
      <label>
        Description
        <textarea rows="5" formControlName="description"></textarea>
      </label>

      <div class="form-grid">
        <label>
          Labels
          <input formControlName="labels" placeholder="bug, customer, blocked">
        </label>
        <label>
          Due date
          <input type="date" formControlName="dueDate">
        </label>
      </div>

      <div class="form-grid">
        <label>
          Type
          <select formControlName="typeId">
            @for (type of types(); track type.id) {
              <option [value]="type.id">{{ type.name }}</option>
            }
          </select>
        </label>
        <label>
          Priority
          <select formControlName="priority">
            @for (priority of priorities(); track priority) {
              <option [value]="priority">{{ priority }}</option>
            }
          </select>
        </label>
        <label>
          Assignee
          <select formControlName="assigneeId">
            <option value="">Unassigned</option>
            @for (user of users(); track user.id) {
              <option [value]="user.id">{{ user.displayName }}</option>
            }
          </select>
        </label>
        <label>
          Status
          <select formControlName="statusId">
            @for (status of statuses(); track status.id) {
              <option [value]="status.id">{{ status.name }}</option>
            }
          </select>
        </label>
        <label>
          Estimate
          <input type="number" min="0" max="999" step="1" formControlName="estimate">
        </label>
      </div>
    </div>
  `
})
export class TicketFormFieldsComponent {
  readonly form = input.required<TicketFormGroup>();
  readonly types = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly statuses = input<WorkflowStatus[]>([]);
}
