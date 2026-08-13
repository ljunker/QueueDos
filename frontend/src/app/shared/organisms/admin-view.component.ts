import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {
  ActivityHook,
  CreateActivityHookRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  Project,
  PublicUser,
  Ticket,
  TicketType,
  UpdateActivityHookRequest,
  UpdateProjectRequest,
  UpdateTicketTypeRequest,
  UpdateUserRequest,
  Workflow
} from '../../core/api.models';
import {AdminPage, WorkflowStatusPatch, WorkflowTransitionPatch} from '../../state/queue.models';
import {AdminActivityHooksPanelComponent} from './admin-activity-hooks-panel.component';
import {AdminDeletedTicketsPanelComponent} from './admin-deleted-tickets-panel.component';
import {AdminProjectsPanelComponent} from './admin-projects-panel.component';
import {AdminTicketTypesPanelComponent} from './admin-ticket-types-panel.component';
import {AdminUsersPanelComponent} from './admin-users-panel.component';
import {AdminWorkflowPanelComponent} from './admin-workflow-panel.component';

@Component({
  selector: 'qd-admin-view',
  standalone: true,
  imports: [
    AdminActivityHooksPanelComponent,
    AdminDeletedTicketsPanelComponent,
    AdminProjectsPanelComponent,
    AdminTicketTypesPanelComponent,
    AdminUsersPanelComponent,
    AdminWorkflowPanelComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (activePage()) {
      @case ('overview') {
        <section class="admin-overview dashboard">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Administration</p>
              <h2>Overview</h2>
              <p class="muted">Organization access, project setup and integrations at a glance.</p>
            </div>
          </div>
          <div class="metric-grid admin-metrics">
            <button type="button" class="metric" (click)="pageSelected.emit('users')"><span>Active users</span><strong>{{ activeUserCount() }}</strong></button>
            <button type="button" class="metric" (click)="pageSelected.emit('users')"><span>Inactive users</span><strong>{{ users().length - activeUserCount() }}</strong></button>
            <button type="button" class="metric" (click)="pageSelected.emit('users')"><span>Admins</span><strong>{{ adminCount() }}</strong></button>
            <button type="button" class="metric" (click)="pageSelected.emit('projects')"><span>Projects</span><strong>{{ projects().length }}</strong></button>
            <button type="button" class="metric" (click)="pageSelected.emit('trash')"><span>Deleted tickets</span><strong>{{ deletedTickets().length }}</strong></button>
            <button type="button" class="metric" (click)="pageSelected.emit('integrations')"><span>Active integrations</span><strong>{{ activeIntegrationCount() }}</strong></button>
          </div>
          <section class="panel admin-shortcuts">
            <h3>Quick access</h3>
            <div class="quick-link-grid">
              <button type="button" (click)="pageSelected.emit('users')">Manage users</button>
              <button type="button" (click)="pageSelected.emit('projects')">Manage projects</button>
              <button type="button" (click)="pageSelected.emit('configuration')">Configure current project</button>
              <button type="button" (click)="pageSelected.emit('integrations')">Manage integrations</button>
              <button type="button" (click)="pageSelected.emit('trash')">Open trash</button>
            </div>
          </section>
        </section>
      }
      @case ('users') {
        <qd-admin-users-panel
          [users]="users()"
          [currentUser]="currentUser()"
          (userCreated)="userCreated.emit($event)"
          (userUpdated)="userUpdated.emit($event)"
          (temporaryPasswordRequested)="temporaryPasswordRequested.emit($event)" />
      }
      @case ('projects') {
        <qd-admin-projects-panel
          [projects]="projects()"
          [selectedProject]="selectedProject()"
          [tickets]="tickets()"
          (projectWizardOpened)="projectWizardOpened.emit()"
          (projectUpdated)="projectUpdated.emit($event)"
          (projectDeleted)="projectDeleted.emit($event)"
          (projectSelected)="projectSelected.emit($event)" />
      }
      @case ('configuration') {
        <section class="admin-configuration">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Project configuration</p>
              <h2>{{ selectedProject()?.name ?? 'No project selected' }}</h2>
              <p class="muted">Ticket types and workflow for the currently selected project.</p>
            </div>
          </div>
          <qd-admin-ticket-types-panel
            [selectedProject]="selectedProject()"
            [projectTypes]="projectTypes()"
            (ticketTypeCreated)="ticketTypeCreated.emit($event)"
            (ticketTypeUpdated)="ticketTypeUpdated.emit($event)"
            (ticketTypeDeleted)="ticketTypeDeleted.emit($event)" />
          <qd-admin-workflow-panel
            [workflowDraft]="workflowDraft()"
            (statusAdded)="statusAdded.emit()"
            (statusPatched)="statusPatched.emit($event)"
            (statusRemoved)="statusRemoved.emit($event)"
            (statusMoved)="statusMoved.emit($event)"
            (transitionAdded)="transitionAdded.emit()"
            (transitionPatched)="transitionPatched.emit($event)"
            (transitionRemoved)="transitionRemoved.emit($event)"
            (workflowSaved)="workflowSaved.emit($event)" />
        </section>
      }
      @case ('integrations') {
        <qd-admin-activity-hooks-panel
          [hooks]="activityHooks()"
          (hookCreated)="activityHookCreated.emit($event)"
          (hookUpdated)="activityHookUpdated.emit($event)"
          (hookDeleted)="activityHookDeleted.emit($event)" />
      }
      @case ('trash') {
        <qd-admin-deleted-tickets-panel
          [tickets]="deletedTickets()"
          (ticketRestored)="ticketRestored.emit($event)" />
      }
    }
  `
})
export class AdminViewComponent {
  readonly activePage = input<AdminPage>('overview');
  readonly projects = input<Project[]>([]);
  readonly selectedProject = input<Project | null>(null);
  readonly tickets = input<Ticket[]>([]);
  readonly deletedTickets = input<Ticket[]>([]);
  readonly activityHooks = input<ActivityHook[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly currentUser = input<PublicUser | null>(null);
  readonly projectTypes = input<TicketType[]>([]);
  readonly workflowDraft = input<Workflow | null>(null);

  readonly pageSelected = output<AdminPage>();
  readonly projectWizardOpened = output<void>();
  readonly projectUpdated = output<{projectId: string; request: UpdateProjectRequest}>();
  readonly projectSelected = output<string>();
  readonly projectDeleted = output<string>();
  readonly userCreated = output<{request: CreateUserRequest; generateTemporaryPassword: boolean}>();
  readonly userUpdated = output<{userId: string; request: UpdateUserRequest}>();
  readonly temporaryPasswordRequested = output<PublicUser>();
  readonly ticketTypeCreated = output<CreateTicketTypeRequest>();
  readonly ticketTypeUpdated = output<{typeId: string; request: UpdateTicketTypeRequest}>();
  readonly ticketTypeDeleted = output<string>();
  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();
  readonly statusMoved = output<{index: number; direction: -1 | 1}>();
  readonly transitionAdded = output<void>();
  readonly transitionPatched = output<WorkflowTransitionPatch>();
  readonly transitionRemoved = output<number>();
  readonly workflowSaved = output<Workflow>();
  readonly ticketRestored = output<string>();
  readonly activityHookCreated = output<CreateActivityHookRequest>();
  readonly activityHookUpdated = output<{hookId: string; request: UpdateActivityHookRequest}>();
  readonly activityHookDeleted = output<string>();

  protected activeUserCount(): number {
    return this.users().filter((user) => user.active).length;
  }

  protected adminCount(): number {
    return this.users().filter((user) => user.role === 'ADMIN').length;
  }

  protected activeIntegrationCount(): number {
    return this.activityHooks().filter((hook) => hook.active).length;
  }
}
