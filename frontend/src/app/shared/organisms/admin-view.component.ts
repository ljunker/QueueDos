import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import {
  CreateProjectRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  Project,
  PublicUser,
  Ticket,
  TicketType,
  Workflow
} from '../../core/api.models';
import { WorkflowStatusPatch, WorkflowTransitionPatch } from '../../state/queue.models';
import { AdminProjectsPanelComponent } from './admin-projects-panel.component';
import { AdminTicketTypesPanelComponent } from './admin-ticket-types-panel.component';
import { AdminUsersPanelComponent } from './admin-users-panel.component';
import { AdminWorkflowPanelComponent } from './admin-workflow-panel.component';

@Component({
  selector: 'qd-admin-view',
  standalone: true,
  imports: [AdminProjectsPanelComponent, AdminTicketTypesPanelComponent, AdminUsersPanelComponent, AdminWorkflowPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="admin-grid">
      <qd-admin-projects-panel
        [projects]="projects()"
        [tickets]="tickets()"
        (projectCreated)="projectCreated.emit($event)"
        (projectSelected)="projectSelected.emit($event)" />
      <qd-admin-users-panel
        [users]="users()"
        (userCreated)="userCreated.emit($event)"
        (userToggled)="userToggled.emit($event)" />
      <qd-admin-ticket-types-panel
        [selectedProject]="selectedProject()"
        [projectTypes]="projectTypes()"
        (ticketTypeCreated)="ticketTypeCreated.emit($event)"
        (ticketTypeDeleted)="ticketTypeDeleted.emit($event)" />
      <qd-admin-workflow-panel
        [workflowDraft]="workflowDraft()"
        (statusAdded)="statusAdded.emit()"
        (statusPatched)="statusPatched.emit($event)"
        (statusRemoved)="statusRemoved.emit($event)"
        (transitionAdded)="transitionAdded.emit()"
        (transitionPatched)="transitionPatched.emit($event)"
        (transitionRemoved)="transitionRemoved.emit($event)"
        (workflowSaved)="workflowSaved.emit($event)" />
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
}
