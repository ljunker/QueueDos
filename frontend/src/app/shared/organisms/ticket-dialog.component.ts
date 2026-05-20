import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CreateTicketRequest,
  Priority,
  Project,
  PublicUser,
  Ticket,
  TicketType,
  UpdateTicketRequest,
  Workflow
} from '../../core/api.models';
import { sortedStatuses } from '../../state/queue.selectors';
import { TicketDialogSave } from '../../state/queue.models';
import { TicketFormFieldsComponent } from '../molecules/ticket-form-fields.component';
import { TicketFormGroup } from '../models/ticket-form.model';

@Component({
  selector: 'qd-ticket-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, TicketFormFieldsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="dialog-backdrop" (click)="closed.emit()">
        <section class="dialog" (click)="$event.stopPropagation()">
          <form class="dialog-body" [formGroup]="form" (ngSubmit)="submit()">
            <header>
              <h3>{{ ticket() ? ticket()?.key : 'New ticket' }}</h3>
              <button type="button" class="icon-button" aria-label="Close" (click)="closed.emit()">x</button>
            </header>

            <qd-ticket-form-fields
              [form]="form"
              [types]="types()"
              [priorities]="priorities()"
              [users]="users()"
              [statuses]="statuses()" />

            <footer>
              @if (ticket() && isAdmin()) {
                <button type="button" class="danger" (click)="deleteRequested.emit(ticket()!.id)">Delete</button>
              } @else {
                <span></span>
              }
              <span></span>
              <button type="button" (click)="closed.emit()">Cancel</button>
              <button type="submit" class="primary" [disabled]="form.invalid">Save</button>
            </footer>
          </form>
        </section>
      </div>
    }
  `
})
export class TicketDialogComponent {
  readonly open = input(false);
  readonly ticket = input<Ticket | null>(null);
  readonly project = input<Project | null>(null);
  readonly workflow = input<Workflow | null>(null);
  readonly types = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly isAdmin = input(false);

  readonly closed = output<void>();
  readonly saved = output<TicketDialogSave>();
  readonly deleteRequested = output<string>();

  protected readonly form: TicketFormGroup = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', { nonNullable: true }),
    labels: new FormControl('', { nonNullable: true }),
    dueDate: new FormControl('', { nonNullable: true }),
    typeId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    priority: new FormControl<Priority>('MEDIUM', { nonNullable: true, validators: [Validators.required] }),
    assigneeId: new FormControl('', { nonNullable: true }),
    statusId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    estimate: new FormControl('', { nonNullable: true })
  });

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const ticket = this.ticket();
      const statuses = this.statuses();
      this.form.reset(
        {
          title: ticket?.title ?? '',
          description: ticket?.description ?? '',
          labels: ticket?.labels.join(', ') ?? '',
          dueDate: ticket?.dueDate ?? '',
          typeId: ticket?.typeId ?? this.types()[0]?.id ?? '',
          priority: ticket?.priority ?? 'MEDIUM',
          assigneeId: ticket?.assigneeId ?? '',
          statusId: ticket?.statusId ?? statuses[0]?.id ?? '',
          estimate: ticket?.estimate?.toString() ?? ''
        },
        { emitEvent: false }
      );
    });
  }

  protected statuses() {
    return sortedStatuses(this.workflow());
  }

  protected submit(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const estimate = value.estimate === '' ? null : Number(value.estimate);
    const baseRequest: UpdateTicketRequest = {
      title: value.title,
      description: value.description,
      typeId: value.typeId,
      priority: value.priority,
      assigneeId: value.assigneeId || null,
      labels: parseLabels(value.labels),
      dueDate: value.dueDate || null,
      estimate,
      clearDueDate: !value.dueDate,
      clearEstimate: value.estimate === ''
    };

    const ticket = this.ticket();
    if (ticket) {
      this.saved.emit({
        mode: 'edit',
        request: {
          id: ticket.id,
          request: baseRequest,
          toStatusId: value.statusId
        }
      });
      return;
    }

    const project = this.project();
    if (!project) return;
    this.saved.emit({
      mode: 'create',
      request: {
        ...(baseRequest as CreateTicketRequest),
        projectId: project.id,
        statusId: value.statusId
      }
    });
  }
}

function parseLabels(value: string): string[] {
  return value.split(',').map((label) => label.trim()).filter(Boolean);
}
