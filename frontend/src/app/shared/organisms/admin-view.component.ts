import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CreateProjectRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  Project,
  PublicUser,
  Role,
  Ticket,
  TicketType,
  Workflow
} from '../../core/api.models';
import { roleLabel, sortedStatuses } from '../../state/queue.selectors';
import { WorkflowStatusPatch, WorkflowTransitionPatch } from '../../state/queue.models';

@Component({
  selector: 'qd-admin-view',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="admin-grid">
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

      <section class="panel">
        <h3>Users</h3>
        <form class="stack" [formGroup]="userForm" (ngSubmit)="submitUser()">
          <input type="email" placeholder="email@example.com" formControlName="email">
          <input placeholder="Display name" formControlName="displayName">
          <div class="split">
            <select formControlName="role">
              <option value="MEMBER">Member</option>
              <option value="ADMIN">Admin</option>
            </select>
            <input type="password" placeholder="Password" formControlName="password">
          </div>
          <button type="submit" [disabled]="userForm.invalid">Add user</button>
        </form>
        <div class="admin-list">
          @for (user of users(); track user.id) {
            <div class="admin-item">
              <div>
                <strong>{{ user.displayName }}</strong>
                <small>{{ user.email }} · {{ roleLabel(user.role) }} · {{ user.active ? 'active' : 'inactive' }}</small>
              </div>
              <button type="button" (click)="userToggled.emit({ userId: user.id, active: !user.active })">
                {{ user.active ? 'Disable' : 'Enable' }}
              </button>
            </div>
          }
        </div>
      </section>

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

      <section class="panel wide">
        <h3>Workflow</h3>
        @if (workflowDraft(); as workflow) {
          <div class="workflow-editor">
            <div>
              <div class="section-heading">
                <span>Statuses</span>
                <button type="button" (click)="statusAdded.emit()">Add status</button>
              </div>
              <div class="editor-list">
                @for (status of sortedStatuses(workflow); track status.id; let index = $index) {
                  <div class="editor-row status-row">
                    <input [value]="status.name" aria-label="Status name" (input)="patchStatus(index, { name: valueOf($event) })">
                    <select [value]="status.category" aria-label="Status category" (change)="patchStatus(index, { category: valueOf($event) })">
                      <option value="TODO">Todo</option>
                      <option value="IN_PROGRESS">In progress</option>
                      <option value="DONE">Done</option>
                    </select>
                    <button type="button" (click)="statusRemoved.emit(index)">Remove</button>
                  </div>
                }
              </div>
            </div>

            <div>
              <div class="section-heading">
                <span>Transitions</span>
                <button type="button" (click)="transitionAdded.emit()" [disabled]="workflow.statuses.length < 2">Add transition</button>
              </div>
              <div class="editor-list">
                @for (transition of workflow.transitions; track transition.id; let index = $index) {
                  <div class="editor-row transition-row">
                    <select
                      [value]="transition.fromStatusId ?? ''"
                      aria-label="From status"
                      (change)="patchTransition(index, { fromStatusId: valueOf($event) || null, globalTransition: !valueOf($event) })">
                      <option value="">Any status</option>
                      @for (status of sortedStatuses(workflow); track status.id) {
                        <option [value]="status.id">{{ status.name }}</option>
                      }
                    </select>
                    <select
                      [value]="transition.toStatusId"
                      aria-label="To status"
                      (change)="patchTransition(index, { toStatusId: valueOf($event) })">
                      @for (status of sortedStatuses(workflow); track status.id) {
                        <option [value]="status.id">{{ status.name }}</option>
                      }
                    </select>
                    <select
                      [value]="roleSelectValue(transition.allowedRoles)"
                      aria-label="Allowed roles"
                      (change)="patchTransition(index, { allowedRoles: rolesFromSelect($event) })">
                      <option value="BOTH">Admin + Member</option>
                      <option value="ADMIN">Admin</option>
                      <option value="MEMBER">Member</option>
                    </select>
                    <input
                      [value]="transition.requiredFields.join(', ')"
                      placeholder="required fields"
                      (input)="patchTransition(index, { requiredFields: fieldsFromInput($event) })">
                    <label class="inline-check">
                      <input
                        type="checkbox"
                        [checked]="transition.globalTransition"
                        (change)="patchTransition(index, { globalTransition: checked($event), fromStatusId: checked($event) ? null : workflow.statuses[0]?.id ?? null })">
                      Global
                    </label>
                    <label class="inline-check">
                      <input
                        type="checkbox"
                        [checked]="transition.allowBackward !== false"
                        (change)="patchTransition(index, { allowBackward: checked($event) })">
                      Back
                    </label>
                    <button type="button" (click)="transitionRemoved.emit(index)">Remove</button>
                  </div>
                }
              </div>
            </div>
          </div>
          <div class="actions">
            <button type="button" class="primary" (click)="workflowSaved.emit(workflow)">Save workflow</button>
          </div>
        } @else {
          <p class="muted">No workflow configured.</p>
        }
      </section>
    </div>
  `
})
export class AdminViewComponent {
  readonly projects = input<Project[]>([]);
  readonly selectedProject = input<Project | null>(null);
  readonly tickets = input<Ticket[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly projectTypes = input<TicketType[]>([]);
  readonly workflowDraft = input<Workflow | null>(null);

  readonly projectCreated = output<CreateProjectRequest>();
  readonly projectSelected = output<string>();
  readonly userCreated = output<CreateUserRequest>();
  readonly userToggled = output<{ userId: string; active: boolean }>();
  readonly ticketTypeCreated = output<CreateTicketTypeRequest>();
  readonly ticketTypeDeleted = output<string>();
  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();
  readonly transitionAdded = output<void>();
  readonly transitionPatched = output<WorkflowTransitionPatch>();
  readonly transitionRemoved = output<number>();
  readonly workflowSaved = output<Workflow>();

  protected readonly roleLabel = roleLabel;
  protected readonly sortedStatuses = sortedStatuses;

  protected readonly projectForm = new FormGroup({
    key: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  protected readonly userForm = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    role: new FormControl<Role>('MEMBER', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  protected readonly typeForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    color: new FormControl('#2563eb', { nonNullable: true })
  });

  protected submitProject(): void {
    if (this.projectForm.invalid) return;
    this.projectCreated.emit(this.projectForm.getRawValue());
    this.projectForm.reset();
  }

  protected submitUser(): void {
    if (this.userForm.invalid) return;
    this.userCreated.emit(this.userForm.getRawValue());
    this.userForm.reset({ email: '', displayName: '', role: 'MEMBER', password: '' });
  }

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

  protected ticketCount(projectId: string): number {
    return this.tickets().filter((ticket) => ticket.projectId === projectId).length;
  }

  protected patchStatus(index: number, changes: WorkflowStatusPatch['changes']): void {
    this.statusPatched.emit({ index, changes });
  }

  protected patchTransition(index: number, changes: WorkflowTransitionPatch['changes']): void {
    this.transitionPatched.emit({ index, changes });
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected fieldsFromInput(event: Event): string[] {
    return this.valueOf(event).split(',').map((value) => value.trim()).filter(Boolean);
  }

  protected roleSelectValue(roles: Role[]): 'BOTH' | Role {
    return roles.includes('ADMIN') && roles.includes('MEMBER') ? 'BOTH' : roles[0] ?? 'MEMBER';
  }

  protected rolesFromSelect(event: Event): Role[] {
    const value = this.valueOf(event);
    return value === 'BOTH' ? ['ADMIN', 'MEMBER'] : [value as Role];
  }
}
