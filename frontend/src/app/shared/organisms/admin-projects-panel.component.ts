import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { CreateProjectRequest, Project, Ticket } from '../../core/api.models';

@Component({
  selector: 'qd-admin-projects-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Projects</h3>
      <form class="inline-form" [formGroup]="projectForm" (ngSubmit)="submitProject()">
        <input placeholder="KEY" maxlength="10" formControlName="key">
        <input placeholder="Project name" formControlName="name">
        <button type="submit" [disabled]="projectForm.invalid">Add</button>
      </form>
      <div class="admin-list">
        @for (project of projects(); track project.id) {
          <div class="admin-item">
            <div>
              <strong>{{ project.key }} - {{ project.name }}</strong>
              <small>{{ ticketCount(project.id) }} tickets{{ project.archived ? ' · archived' : '' }}</small>
            </div>
            <button type="button" (click)="projectSelected.emit(project.id)">Open</button>
          </div>
        }
      </div>
    </section>
  `
})
export class AdminProjectsPanelComponent {
  readonly projects = input<Project[]>([]);
  readonly tickets = input<Ticket[]>([]);

  readonly projectCreated = output<CreateProjectRequest>();
  readonly projectSelected = output<string>();

  protected readonly projectForm = new FormGroup({
    key: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected submitProject(): void {
    if (this.projectForm.invalid) return;
    this.projectCreated.emit(this.projectForm.getRawValue());
    this.projectForm.reset();
  }

  protected ticketCount(projectId: string): number {
    return this.tickets().filter((ticket) => ticket.projectId === projectId).length;
  }
}
