import {ChangeDetectionStrategy, Component, effect, input, output, signal} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

import {
  CreateTicketTypeRequest,
  Project,
  TicketType,
  UpdateTicketTypeRequest
} from '../../core/api.models';

@Component({
  selector: 'qd-admin-ticket-types-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Ticket types</h3>
      <form class="ticket-type-create-form" [formGroup]="typeForm" (ngSubmit)="submitType()">
        <input placeholder="Type name" maxlength="160" formControlName="name">
        <input placeholder="Description (optional)" formControlName="description">
        <input type="color" aria-label="Type color" formControlName="color">
        <button type="submit" [disabled]="typeForm.invalid || !selectedProject()">Add</button>
      </form>
      <div class="admin-list">
        @for (type of projectTypes(); track type.id) {
          <div class="admin-item ticket-type-item">
            <div>
              <strong><span class="type-dot inline-dot" [style.background]="type.color"></span> {{ type.name }}</strong>
              <small>{{ type.description || 'No description' }}</small>
            </div>
            <div class="row-actions">
              <button type="button" (click)="editType(type)">Edit</button>
              <button type="button" (click)="ticketTypeDeleted.emit(type.id)">Delete</button>
            </div>
          </div>
          @if (editingTypeId() === type.id) {
            <form class="ticket-type-edit-form" [formGroup]="editForm" (ngSubmit)="submitEdit(type.id)">
              <label>
                Name
                <input formControlName="name" maxlength="160" required>
              </label>
              <label>
                Description
                <input formControlName="description">
              </label>
              <label class="color-field">
                Color
                <span class="color-control">
                  <input type="color" aria-label="Ticket type color" formControlName="color">
                  <code>{{ editForm.controls.color.value }}</code>
                </span>
              </label>
              <div class="actions">
                <button type="button" (click)="editingTypeId.set(null)">Cancel</button>
                <button type="submit" class="primary" [disabled]="editForm.invalid">Save type</button>
              </div>
            </form>
          }
        }
      </div>
    </section>
  `
})
export class AdminTicketTypesPanelComponent {
  readonly selectedProject = input<Project | null>(null);
  readonly projectTypes = input<TicketType[]>([]);

  readonly ticketTypeCreated = output<CreateTicketTypeRequest>();
  readonly ticketTypeUpdated = output<{typeId: string; request: UpdateTicketTypeRequest}>();
  readonly ticketTypeDeleted = output<string>();

  protected readonly editingTypeId = signal<string | null>(null);
  protected readonly typeForm = new FormGroup({
    name: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    description: new FormControl('', {nonNullable: true}),
    color: new FormControl('#2563eb', {nonNullable: true})
  });
  protected readonly editForm = new FormGroup({
    name: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    description: new FormControl('', {nonNullable: true}),
    color: new FormControl('#2563eb', {nonNullable: true})
  });

  private previousProjectId: string | null = null;

  constructor() {
    effect(() => {
      const projectId = this.selectedProject()?.id ?? null;
      if (projectId !== this.previousProjectId) this.editingTypeId.set(null);
      this.previousProjectId = projectId;
    });
  }

  protected submitType(): void {
    const project = this.selectedProject();
    if (this.typeForm.invalid || !project) return;
    const value = this.typeForm.getRawValue();
    this.ticketTypeCreated.emit({
      projectId: project.id,
      name: value.name.trim(),
      description: value.description.trim(),
      color: value.color
    });
    this.typeForm.reset({name: '', description: '', color: '#2563eb'});
  }

  protected editType(type: TicketType): void {
    this.editingTypeId.set(type.id);
    this.editForm.reset({name: type.name, description: type.description, color: type.color});
  }

  protected submitEdit(typeId: string): void {
    if (this.editForm.invalid) return;
    const value = this.editForm.getRawValue();
    this.ticketTypeUpdated.emit({
      typeId,
      request: {name: value.name.trim(), description: value.description.trim(), color: value.color}
    });
    this.editingTypeId.set(null);
  }
}
