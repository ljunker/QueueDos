import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import {
  BootstrapResponse,
  CreateProjectRequest,
  CreateTicketCommentRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  Priority,
  Project,
  PublicUser,
  Ticket,
  TicketChange,
  TicketComment,
  TicketType,
  UpdateUserRequest,
  Workflow,
  WorkflowStatus
} from '../../core/api.models';
import { TicketFilters, WorkflowStatusPatch, WorkflowTransitionPatch, WorkspaceTab } from '../../state/queue.models';
import { AdminViewComponent } from './admin-view.component';
import { BoardViewComponent } from './board-view.component';
import { TicketDetailViewComponent } from './ticket-detail-view.component';
import { TicketListViewComponent } from './ticket-list-view.component';

@Component({
  selector: 'qd-workspace-tab-host',
  standalone: true,
  imports: [AdminViewComponent, BoardViewComponent, TicketDetailViewComponent, TicketListViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="tab-panel">
      @switch (activeTab()) {
        @case ('board') {
          <qd-board-view
            [workflow]="workflow()"
            [statuses]="statuses()"
            [tickets]="projectTickets()"
            [types]="projectTypes()"
            [users]="users()"
            [currentRole]="currentUser()?.role ?? 'MEMBER'"
            (ticketOpened)="ticketOpened.emit($event)"
            (ticketTransitioned)="ticketTransitioned.emit($event)"
            (transitionDenied)="transitionDenied.emit()" />
        }
        @case ('list') {
          <qd-ticket-list-view
            [tickets]="visibleTickets()"
            [filters]="filters()"
            [statuses]="statuses()"
            [workflow]="workflow()"
            [types]="projectTypes()"
            [priorities]="priorities()"
            [users]="activeUsers()"
            [allUsers]="users()"
            (filtersChanged)="filtersChanged.emit($event)"
            (ticketOpened)="ticketOpened.emit($event)" />
        }
        @case ('detail') {
          <qd-ticket-detail-view
            [ticket]="selectedTicket()"
            [comments]="selectedTicketComments()"
            [changes]="selectedTicketChanges()"
            [workflow]="workflow()"
            [types]="projectTypes()"
            [users]="users()"
            (closed)="detailClosed.emit()"
            (editRequested)="editRequested.emit($event)"
            (commentSubmitted)="commentSubmitted.emit({ ticketId: $event.ticketId, request: { body: $event.body } })" />
        }
        @case ('admin') {
          @if (isAdmin()) {
            <qd-admin-view
              [projects]="projects()"
              [selectedProject]="selectedProject()"
              [tickets]="data()?.tickets ?? []"
              [users]="users()"
              [projectTypes]="projectTypes()"
              [workflowDraft]="workflowDraft()"
              (projectCreated)="projectCreated.emit($event)"
              (projectSelected)="projectSelected.emit($event)"
              (userCreated)="userCreated.emit($event)"
              (userToggled)="userUpdated.emit({ userId: $event.userId, request: { active: $event.active } })"
              (ticketTypeCreated)="ticketTypeCreated.emit($event)"
              (ticketTypeDeleted)="ticketTypeDeleted.emit($event)"
              (statusAdded)="statusAdded.emit()"
              (statusPatched)="statusPatched.emit($event)"
              (statusRemoved)="statusRemoved.emit($event)"
              (transitionAdded)="transitionAdded.emit()"
              (transitionPatched)="transitionPatched.emit($event)"
              (transitionRemoved)="transitionRemoved.emit($event)"
              (workflowSaved)="workflowSaved.emit()" />
          }
        }
      }
    </section>
  `
})
export class WorkspaceTabHostComponent {
  readonly data = input<BootstrapResponse | null>(null);
  readonly activeTab = input<WorkspaceTab>('board');
  readonly isAdmin = input(false);
  readonly projects = input<Project[]>([]);
  readonly selectedProject = input<Project | null>(null);
  readonly currentUser = input<PublicUser | null>(null);
  readonly users = input<PublicUser[]>([]);
  readonly activeUsers = input<PublicUser[]>([]);
  readonly workflow = input<Workflow | null>(null);
  readonly statuses = input<WorkflowStatus[]>([]);
  readonly projectTickets = input<Ticket[]>([]);
  readonly visibleTickets = input<Ticket[]>([]);
  readonly projectTypes = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly filters = input.required<TicketFilters>();
  readonly selectedTicket = input<Ticket | null>(null);
  readonly selectedTicketComments = input<TicketComment[]>([]);
  readonly selectedTicketChanges = input<TicketChange[]>([]);
  readonly workflowDraft = input<Workflow | null>(null);

  readonly ticketOpened = output<string>();
  readonly ticketTransitioned = output<{ ticket: Ticket; toStatusId: string }>();
  readonly transitionDenied = output<void>();
  readonly filtersChanged = output<Partial<TicketFilters>>();
  readonly detailClosed = output<void>();
  readonly editRequested = output<string>();
  readonly commentSubmitted = output<{ ticketId: string; request: CreateTicketCommentRequest }>();
  readonly projectCreated = output<CreateProjectRequest>();
  readonly projectSelected = output<string>();
  readonly userCreated = output<CreateUserRequest>();
  readonly userUpdated = output<{ userId: string; request: UpdateUserRequest }>();
  readonly ticketTypeCreated = output<CreateTicketTypeRequest>();
  readonly ticketTypeDeleted = output<string>();
  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();
  readonly transitionAdded = output<void>();
  readonly transitionPatched = output<WorkflowTransitionPatch>();
  readonly transitionRemoved = output<number>();
  readonly workflowSaved = output<void>();
}
