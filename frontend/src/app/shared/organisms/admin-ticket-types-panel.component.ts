import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { CreateTicketTypeRequest, Project, TicketType } from '../../core/api.models';

@Component({
  selector: 'qd-admin-ticket-types-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Ticket types</h3>
      <form class="inline-form" [formGroup]="typeForm" (ngSubmit)="submitType()">
        <input placeholder="Type name" formControlName="name">
        <input type="color" aria-label="Type color" formControlName="color">
        <button type="submit" [disabled]="typeForm.invalid || !selectedProject()">Add</button>
      </form>
      <div class="admin-list">
        @for (type of projectTypes(); track type.id) {
          <div class="admin-item">
            <div>
              <strong><span class="type-dot inline-dot" [style.background]="type.color"></span> {{ type.name }}</strong>
              <small>{{ type.description || 'No description' }}</small>
            </div>
            <button type="button" (click)="ticketTypeDeleted.emit(type.id)">Delete</button>
          </div>
        }
      </div>
    </section>
  `
})
export class AdminTicketTypesPanelComponent {
  readonly selectedProject = input<Project | null>(null);
  readonly projectTypes = input<TicketType[]>([]);

  readonly ticketTypeCreated = output<CreateTicketTypeRequest>();
  readonly ticketTypeDeleted = output<string>();

  protected readonly typeForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    color: new FormControl('#2563eb', { nonNullable: true })
  });

  protected submitType(): void {
    const project = this.selectedProject();
    if (this.typeForm.invalid || !project) return;
    this.ticketTypeCreated.emit({
      projectId: project.id,
      name: this.typeForm.controls.name.value,
      color: this.typeForm.controls.color.value
    });
    this.typeForm.reset({ name: '', color: '#2563eb' });
  }
}
