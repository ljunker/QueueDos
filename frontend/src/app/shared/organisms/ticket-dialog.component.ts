import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
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
import { TicketVersionConflictState } from '../../state/queue.models';
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
            @if (versionConflict()) {
              <section class="panel">
                <p class="error">This ticket changed while the editor was open. Your draft is still here.</p>
                <p class="muted">Reload the latest version or deliberately apply your draft to version {{ versionConflict()!.currentVersion }}.</p>
                @if (overlappingFields().length) {
                  <p>Changed on both sides: {{ overlappingFields().join(', ') }}</p>
                }
                <button type="button" [disabled]="!hasCurrentConflictTicket()" (click)="reloadConflict()">Reload</button>
                <button type="button" class="primary" [disabled]="!hasCurrentConflictTicket()" (click)="applyConflict()">Apply my changes</button>
              </section>
            }
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
  readonly versionConflict = input<TicketVersionConflictState | null>(null);
  readonly conflictTicket = input<Ticket | null>(null);

  readonly closed = output<void>();
  readonly saved = output<TicketDialogSave>();
  readonly deleteRequested = output<string>();
  readonly conflictReloadRequested = output<string>();
  private readonly editingVersion = signal(0);
  private readonly editingTicket = signal<Ticket | null>(null);

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
      if (this.versionConflict()) return;
      const ticket = this.ticket();
      const statuses = this.statuses();
      this.resetForm(ticket, statuses[0]?.id ?? '');
      this.editingVersion.set(ticket?.version ?? 0);
      this.editingTicket.set(ticket);
    });
  }

  protected statuses() {
    return sortedStatuses(this.workflow());
  }

  protected submit(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const estimate = value.estimate === '' ? null : Number(value.estimate);
    const ticket = this.ticket();
    if (ticket) {
      const request = changedRequest(this.editingTicket() ?? ticket, value, estimate, this.editingVersion());
      this.saved.emit({
        mode: 'edit',
        request: {
          id: ticket.id,
          request
        }
      });
      return;
    }

    const project = this.project();
    if (!project) return;
    this.saved.emit({
      mode: 'create',
      request: {
        title: value.title,
        description: value.description,
        typeId: value.typeId,
        priority: value.priority,
        assigneeId: value.assigneeId || null,
        labels: parseLabels(value.labels),
        dueDate: value.dueDate || null,
        estimate,
        projectId: project.id,
        statusId: value.statusId
      }
    });
  }

  protected overlappingFields(): string[] {
    const conflict = this.versionConflict();
    const original = this.editingTicket();
    const current = this.currentConflictTicket();
    if (!conflict || !original || !current) return [];
    return changedFields(conflict.save.request)
      .filter((field) => ticketField(original, field) !== ticketField(current, field));
  }

  protected reloadConflict(): void {
    const current = this.currentConflictTicket();
    if (!current) return;
    this.resetForm(current, current.statusId);
    this.editingVersion.set(current.version);
    this.editingTicket.set(current);
    this.conflictReloadRequested.emit(current.id);
  }

  protected applyConflict(): void {
    const overlapping = this.overlappingFields();
    if (overlapping.length && !confirm(`Overwrite server changes to: ${overlapping.join(', ')}?`)) return;
    const conflict = this.versionConflict();
    const current = this.currentConflictTicket();
    if (!conflict || !current) return;
    this.editingTicket.set(current);
    this.editingVersion.set(conflict.currentVersion);
    this.submit();
  }

  protected hasCurrentConflictTicket(): boolean {
    return this.currentConflictTicket() !== null;
  }

  private currentConflictTicket(): Ticket | null {
    const conflict = this.versionConflict();
    const current = this.conflictTicket();
    return conflict && current?.version === conflict.currentVersion ? current : null;
  }

  private resetForm(ticket: Ticket | null, defaultStatusId: string): void {
    this.form.reset(
      {
        title: ticket?.title ?? '',
        description: ticket?.description ?? '',
        labels: ticket?.labels.join(', ') ?? '',
        dueDate: ticket?.dueDate ?? '',
        typeId: ticket?.typeId ?? this.types()[0]?.id ?? '',
        priority: ticket?.priority ?? 'MEDIUM',
        assigneeId: ticket?.assigneeId ?? '',
        statusId: ticket?.statusId ?? defaultStatusId,
        estimate: ticket?.estimate?.toString() ?? ''
      },
      { emitEvent: false }
    );
  }
}

function parseLabels(value: string): string[] {
  return value.split(',').map((label) => label.trim()).filter(Boolean);
}

function changedRequest(ticket: Ticket, value: ReturnType<TicketFormGroup['getRawValue']>, estimate: number | null, expectedVersion: number): UpdateTicketRequest {
  const request: UpdateTicketRequest = {expectedVersion};
  const labels = parseLabels(value.labels);
  if (value.title !== ticket.title) request.title = value.title;
  if (value.description !== ticket.description) request.description = value.description;
  if (value.typeId !== ticket.typeId) request.typeId = value.typeId;
  if (value.priority !== ticket.priority) request.priority = value.priority;
  if ((value.assigneeId || null) !== ticket.assigneeId) {
    if (value.assigneeId) request.assigneeId = value.assigneeId; else request.clearAssignee = true;
  }
  if (JSON.stringify(labels) !== JSON.stringify(ticket.labels)) request.labels = labels;
  if ((value.dueDate || null) !== ticket.dueDate) {
    if (value.dueDate) request.dueDate = value.dueDate; else request.clearDueDate = true;
  }
  if (estimate !== ticket.estimate) {
    if (estimate === null) request.clearEstimate = true; else request.estimate = estimate;
  }
  if (value.statusId !== ticket.statusId) request.statusId = value.statusId;
  return request;
}

function ticketField(ticket: Ticket, field: string): string {
  return JSON.stringify(ticket[field as keyof Ticket]);
}

function changedFields(request: UpdateTicketRequest): string[] {
  const fields = Object.keys(request)
    .filter((field) => !['expectedVersion', 'clearAssignee', 'clearDueDate', 'clearEstimate'].includes(field));
  if (request.clearAssignee) fields.push('assigneeId');
  if (request.clearDueDate) fields.push('dueDate');
  if (request.clearEstimate) fields.push('estimate');
  return [...new Set(fields)];
}
