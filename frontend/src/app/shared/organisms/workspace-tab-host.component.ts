import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';

import {
  BootstrapResponse,
  BulkUpdateTicketsRequest,
  CreateActivityHookRequest,
  CreateProjectRequest,
  CreateTicketCommentRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  Priority,
  Project,
  PublicUser,
  SavedTicketFilter,
  SavedTicketFilterView,
  Ticket,
  TicketChange,
  TicketComment,
  TicketType,
  UpdateActivityHookRequest,
  UpdateUserRequest,
  Workflow,
  WorkflowStatus
} from '../../core/api.models';
import {
  MyTicketsFilters,
  TicketFilters,
  WorkflowStatusPatch,
  WorkflowTransitionPatch,
  WorkspaceTab
} from '../../state/queue.models';
import {AdminViewComponent} from './admin-view.component';
import {BoardViewComponent} from './board-view.component';
import {MyTicketsViewComponent} from './my-tickets-view.component';
import {ProjectDashboardViewComponent} from './project-dashboard-view.component';
import {TicketDetailViewComponent} from './ticket-detail-view.component';
import {TicketListViewComponent} from './ticket-list-view.component';

@Component({
  selector: 'qd-workspace-tab-host',
  standalone: true,
  imports: [
    AdminViewComponent,
    BoardViewComponent,
    MyTicketsViewComponent,
    ProjectDashboardViewComponent,
    TicketDetailViewComponent,
    TicketListViewComponent
  ],
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
        @case ('dashboard') {
          <qd-project-dashboard-view
            [tickets]="projectTickets()"
            [statuses]="statuses()"
            [priorities]="priorities()"
            [users]="users()"
            (filtersChanged)="filtersChanged.emit($event)" />
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
            [savedFilters]="projectSavedFilters()"
            (filtersChanged)="filtersChanged.emit($event)"
            (ticketOpened)="ticketOpened.emit($event)"
            (savedFilterCreated)="savedFilterCreated.emit({ view: 'PROJECT_LIST', name: $event })"
            (savedFilterApplied)="savedFilterApplied.emit($event)"
            (savedFilterRenamed)="savedFilterRenamed.emit($event)"
            (savedFilterDeleted)="savedFilterDeleted.emit($event)"
            (bulkUpdateRequested)="bulkUpdateRequested.emit($event)" />
        }
        @case ('my-tickets') {
          <qd-my-tickets-view
            [tickets]="myTickets()"
            [filters]="myTicketsFilters()"
            [projects]="projects()"
            [types]="data()?.ticketTypes ?? []"
            [workflows]="data()?.workflows ?? []"
            [priorities]="priorities()"
            [activeUsers]="activeUsers()"
            [users]="users()"
            [savedFilters]="myTicketsSavedFilters()"
            (filtersChanged)="myTicketsFiltersChanged.emit($event)"
            (ticketOpened)="ticketOpened.emit($event)"
            (savedFilterCreated)="savedFilterCreated.emit({ view: 'MY_TICKETS', name: $event })"
            (savedFilterApplied)="savedFilterApplied.emit($event)"
            (savedFilterRenamed)="savedFilterRenamed.emit($event)"
            (savedFilterDeleted)="savedFilterDeleted.emit($event)"
            (bulkUpdateRequested)="bulkUpdateRequested.emit($event)" />
        }
        @case ('detail') {
          <qd-ticket-detail-view
            [ticket]="selectedTicket()"
            [comments]="selectedTicketComments()"
            [changes]="selectedTicketChanges()"
            [workflow]="selectedTicketWorkflow()"
            [types]="selectedTicketTypes()"
            [users]="users()"
            [currentUser]="currentUser()"
            (closed)="detailClosed.emit()"
            (editRequested)="editRequested.emit($event)"
            (commitmentChanged)="commitmentChanged.emit($event)"
            (commentSubmitted)="commentSubmitted.emit({ ticketId: $event.ticketId, request: { body: $event.body } })" />
        }
        @case ('admin') {
          @if (isAdmin()) {
            <qd-admin-view
              [projects]="projects()"
              [selectedProject]="selectedProject()"
              [tickets]="data()?.tickets ?? []"
              [deletedTickets]="data()?.deletedTickets ?? []"
              [activityHooks]="data()?.activityHooks ?? []"
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
              (workflowSaved)="workflowSaved.emit()"
              (ticketRestored)="ticketRestored.emit($event)"
              (activityHookCreated)="activityHookCreated.emit($event)"
              (activityHookUpdated)="activityHookUpdated.emit($event)"
              (activityHookDeleted)="activityHookDeleted.emit($event)" />
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
  readonly myTickets = input<Ticket[]>([]);
  readonly projectTypes = input<TicketType[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly filters = input.required<TicketFilters>();
  readonly myTicketsFilters = input.required<MyTicketsFilters>();
  readonly selectedTicket = input<Ticket | null>(null);
  readonly selectedTicketWorkflow = input<Workflow | null>(null);
  readonly selectedTicketTypes = input<TicketType[]>([]);
  readonly selectedTicketComments = input<TicketComment[]>([]);
  readonly selectedTicketChanges = input<TicketChange[]>([]);
  readonly workflowDraft = input<Workflow | null>(null);
  readonly projectSavedFilters = input<SavedTicketFilter[]>([]);
  readonly myTicketsSavedFilters = input<SavedTicketFilter[]>([]);

  readonly ticketOpened = output<string>();
  readonly ticketTransitioned = output<{ ticket: Ticket; toStatusId: string }>();
  readonly transitionDenied = output<void>();
  readonly filtersChanged = output<Partial<TicketFilters>>();
  readonly myTicketsFiltersChanged = output<Partial<MyTicketsFilters>>();
  readonly detailClosed = output<void>();
  readonly editRequested = output<string>();
  readonly commentSubmitted = output<{ ticketId: string; request: CreateTicketCommentRequest }>();
  readonly commitmentChanged = output<{ ticketId: string; committed: boolean }>();
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
  readonly ticketRestored = output<string>();
  readonly activityHookCreated = output<CreateActivityHookRequest>();
  readonly activityHookUpdated = output<{ hookId: string; request: UpdateActivityHookRequest }>();
  readonly activityHookDeleted = output<string>();
  readonly savedFilterCreated = output<{ view: SavedTicketFilterView; name: string }>();
  readonly savedFilterApplied = output<SavedTicketFilter>();
  readonly savedFilterRenamed = output<{ filterId: string; name: string }>();
  readonly savedFilterDeleted = output<string>();
  readonly bulkUpdateRequested = output<BulkUpdateTicketsRequest>();
}
