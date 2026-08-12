import {ChangeDetectionStrategy, Component, effect, input, output} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

import {Project, Ticket, UpdateProjectRequest} from '../../core/api.models';

@Component({
  selector: 'qd-admin-projects-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <div class="section-heading">
        <h3>Projects</h3>
        <button type="button" class="primary" (click)="projectWizardOpened.emit()">New project</button>
      </div>
      <div class="admin-list">
        @for (project of projects(); track project.id) {
          <div class="admin-item" [class.selected-admin-item]="project.id === selectedProject()?.id">
            <div>
              <strong><span class="project-color-dot" [style.background]="project.color"></span>{{ project.key }} - {{ project.name }}</strong>
              <small>{{ ticketCount(project.id) }} tickets{{ project.archived ? ' · archived' : '' }}</small>
            </div>
            <button type="button" (click)="projectSelected.emit(project.id)">Open</button>
          </div>
        }
      </div>

      @if (selectedProject(); as project) {
        <form class="project-settings-form" [formGroup]="projectForm" (ngSubmit)="submitProject(project)">
          <div class="section-heading">
            <span>Edit project</span>
          </div>
          <div class="form-grid">
            <label>
              Project name
              <input formControlName="name" maxlength="160" required>
            </label>
            <label>
              Key
              <input formControlName="key" maxlength="10" required [readonly]="ticketCount(project.id) > 0" (input)="uppercaseKey()">
            </label>
          </div>
          @if (ticketCount(project.id) > 0) {
            <small class="muted">The key is locked after the first ticket is created.</small>
          }
          <label>
            Description
            <textarea rows="3" formControlName="description"></textarea>
          </label>
          <label class="color-field">
            Project color
            <span class="color-control">
              <input type="color" aria-label="Project color" formControlName="color">
              <code>{{ projectForm.controls.color.value }}</code>
            </span>
          </label>
          <div class="actions">
            <span></span>
            <button type="submit" class="primary" [disabled]="projectForm.invalid || projectForm.pristine">Save project</button>
          </div>
        </form>
      }
    </section>
  `
})
export class AdminProjectsPanelComponent {
  readonly projects = input<Project[]>([]);
  readonly selectedProject = input<Project | null>(null);
  readonly tickets = input<Ticket[]>([]);

  readonly projectWizardOpened = output<void>();
  readonly projectUpdated = output<{projectId: string; request: UpdateProjectRequest}>();
  readonly projectSelected = output<string>();

  protected readonly projectForm = new FormGroup({
    key: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9]{1,9}$/)]
    }),
    name: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    description: new FormControl('', {nonNullable: true}),
    color: new FormControl('#2563eb', {nonNullable: true, validators: [Validators.required]})
  });

  constructor() {
    effect(() => {
      const project = this.selectedProject();
      if (!project) return;
      this.projectForm.reset({
        key: project.key,
        name: project.name,
        description: project.description,
        color: project.color
      });
    });
  }

  protected uppercaseKey(): void {
    const control = this.projectForm.controls.key;
    const upper = control.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (upper !== control.value) control.setValue(upper);
  }

  protected submitProject(project: Project): void {
    if (this.projectForm.invalid) return;
    const value = this.projectForm.getRawValue();
    this.projectUpdated.emit({
      projectId: project.id,
      request: {
        key: value.key.trim().toUpperCase(),
        name: value.name.trim(),
        description: value.description.trim(),
        color: value.color
      }
    });
  }

  protected ticketCount(projectId: string): number {
    return this.tickets().filter((ticket) => ticket.projectId === projectId).length;
  }
}
