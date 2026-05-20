import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Store } from '@ngrx/store';

import { Priority, Ticket } from '../../core/api.models';
import { AdminViewComponent } from '../../shared/organisms/admin-view.component';
import { BoardViewComponent } from '../../shared/organisms/board-view.component';
import { SidebarComponent } from '../../shared/organisms/sidebar.component';
import { TicketDetailViewComponent } from '../../shared/organisms/ticket-detail-view.component';
import { TicketDialogComponent } from '../../shared/organisms/ticket-dialog.component';
import { TicketListViewComponent } from '../../shared/organisms/ticket-list-view.component';
import { ToastComponent } from '../../shared/atoms/toast.component';
import { QueueActions } from '../../state/queue.actions';
import {
  selectActiveTab,
  selectActiveUsers,
  selectCurrentUser,
  selectData,
  selectDialogTicket,
  selectError,
  selectFilters,
  selectIsAdmin,
  selectLoading,
  selectOrganizations,
  selectPriorities,
  selectProjectTickets,
  selectProjectTypes,
  selectProjects,
  selectSelectedProject,
  selectSelectedProjectId,
  selectSelectedTicket,
  selectSelectedTicketChanges,
  selectSelectedTicketComments,
  selectSelectedWorkflow,
  selectSortedStatuses,
  selectTicketDialog,
  selectToast,
  selectUsers,
  selectVisibleTickets,
  selectWorkflowDraft
} from '../../state/queue.selectors';
import { RouteWorkspaceState, TicketFilters, TicketSort, WorkspaceTab } from '../../state/queue.models';

@Component({
  selector: 'qd-workspace-page',
  standalone: true,
  imports: [
    AdminViewComponent,
    BoardViewComponent,
    SidebarComponent,
    TicketDetailViewComponent,
    TicketDialogComponent,
    TicketListViewComponent,
    ToastComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !data()) {
      <main class="app-loading">
        <p class="muted">Loading...</p>
      </main>
    } @else if (error()) {
      <main class="app-loading">
        <p class="error">{{ error() }}</p>
      </main>
    } @else {
      <main class="app">
        <qd-sidebar
          [organizations]="organizations()"
          [projects]="projects()"
          [selectedProjectId]="selectedProjectId()"
          [user]="currentUser()"
          [activeTab]="activeTab()"
          [isAdmin]="isAdmin()"
          (projectSelected)="dispatchProjectSelected($event)"
          (tabSelected)="dispatchTabSelected($event)"
          (logoutRequested)="store.dispatch(logoutRequested())" />

        <section class="workspace">
          <header class="toolbar">
            <div>
              <p class="eyebrow">{{ selectedProject()?.key ?? 'Project' }}</p>
              <h2>{{ selectedProject()?.name ?? 'No project' }}</h2>
            </div>
            <button type="button" class="primary" [disabled]="!selectedProject()" (click)="openCreateDialog()">New ticket</button>
          </header>

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
                  (ticketOpened)="openTicket($event)"
                  (ticketTransitioned)="transitionTicket($event)"
                  (transitionDenied)="showToast('This workflow transition is not allowed.')" />
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
                  (filtersChanged)="changeFilters($event)"
                  (ticketOpened)="openTicket($event)" />
              }
              @case ('detail') {
                <qd-ticket-detail-view
                  [ticket]="selectedTicket()"
                  [comments]="selectedTicketComments()"
                  [changes]="selectedTicketChanges()"
                  [workflow]="workflow()"
                  [types]="projectTypes()"
                  [users]="users()"
                  (closed)="store.dispatch(detailClosed())"
                  (editRequested)="openEditDialog($event)"
                  (commentSubmitted)="createComment($event)" />
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
                    (projectCreated)="store.dispatch(projectCreateRequested({ request: $event }))"
                    (projectSelected)="dispatchProjectSelected($event)"
                    (userCreated)="store.dispatch(userCreateRequested({ request: $event }))"
                    (userToggled)="store.dispatch(userUpdateRequested({ userId: $event.userId, request: { active: $event.active } }))"
                    (ticketTypeCreated)="store.dispatch(ticketTypeCreateRequested({ request: $event }))"
                    (ticketTypeDeleted)="store.dispatch(ticketTypeDeleteRequested({ typeId: $event }))"
                    (statusAdded)="store.dispatch(workflowStatusAdded())"
                    (statusPatched)="store.dispatch(workflowStatusPatched($event))"
                    (statusRemoved)="store.dispatch(workflowStatusRemoved({ index: $event }))"
                    (transitionAdded)="store.dispatch(workflowTransitionAdded())"
                    (transitionPatched)="store.dispatch(workflowTransitionPatched($event))"
                    (transitionRemoved)="store.dispatch(workflowTransitionRemoved({ index: $event }))"
                    (workflowSaved)="saveWorkflow()" />
                }
              }
            }
          </section>
        </section>

        <qd-ticket-dialog
          [open]="Boolean(ticketDialog())"
          [ticket]="dialogTicket()"
          [project]="selectedProject()"
          [workflow]="workflow()"
          [types]="projectTypes()"
          [priorities]="priorities()"
          [users]="activeUsers()"
          [isAdmin]="isAdmin()"
          (closed)="store.dispatch(ticketDialogClosed())"
          (saved)="store.dispatch(ticketDialogSaved($event))"
          (deleteRequested)="store.dispatch(ticketDeleteRequested({ ticketId: $event }))" />

        <qd-toast [message]="toast()" (cleared)="store.dispatch(toastCleared())" />
      </main>
    }
  `
})
export class WorkspacePageComponent {
  protected readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly data = this.store.selectSignal(selectData);
  protected readonly loading = this.store.selectSignal(selectLoading);
  protected readonly error = this.store.selectSignal(selectError);
  protected readonly organizations = this.store.selectSignal(selectOrganizations);
  protected readonly projects = this.store.selectSignal(selectProjects);
  protected readonly selectedProjectId = this.store.selectSignal(selectSelectedProjectId);
  protected readonly selectedProject = this.store.selectSignal(selectSelectedProject);
  protected readonly currentUser = this.store.selectSignal(selectCurrentUser);
  protected readonly users = this.store.selectSignal(selectUsers);
  protected readonly activeUsers = this.store.selectSignal(selectActiveUsers);
  protected readonly isAdmin = this.store.selectSignal(selectIsAdmin);
  protected readonly activeTab = this.store.selectSignal(selectActiveTab);
  protected readonly workflow = this.store.selectSignal(selectSelectedWorkflow);
  protected readonly statuses = this.store.selectSignal(selectSortedStatuses);
  protected readonly projectTickets = this.store.selectSignal(selectProjectTickets);
  protected readonly projectTypes = this.store.selectSignal(selectProjectTypes);
  protected readonly priorities = this.store.selectSignal(selectPriorities);
  protected readonly visibleTickets = this.store.selectSignal(selectVisibleTickets);
  protected readonly filters = this.store.selectSignal(selectFilters);
  protected readonly selectedTicket = this.store.selectSignal(selectSelectedTicket);
  protected readonly selectedTicketComments = this.store.selectSignal(selectSelectedTicketComments);
  protected readonly selectedTicketChanges = this.store.selectSignal(selectSelectedTicketChanges);
  protected readonly ticketDialog = this.store.selectSignal(selectTicketDialog);
  protected readonly dialogTicket = this.store.selectSignal(selectDialogTicket);
  protected readonly workflowDraft = this.store.selectSignal(selectWorkflowDraft);
  protected readonly toast = this.store.selectSignal(selectToast);

  protected readonly logoutRequested = QueueActions.logoutRequested;
  protected readonly detailClosed = QueueActions.detailClosed;
  protected readonly projectCreateRequested = QueueActions.projectCreateRequested;
  protected readonly userCreateRequested = QueueActions.userCreateRequested;
  protected readonly userUpdateRequested = QueueActions.userUpdateRequested;
  protected readonly ticketTypeCreateRequested = QueueActions.ticketTypeCreateRequested;
  protected readonly ticketTypeDeleteRequested = QueueActions.ticketTypeDeleteRequested;
  protected readonly workflowStatusAdded = QueueActions.workflowStatusAdded;
  protected readonly workflowStatusPatched = QueueActions.workflowStatusPatched;
  protected readonly workflowStatusRemoved = QueueActions.workflowStatusRemoved;
  protected readonly workflowTransitionAdded = QueueActions.workflowTransitionAdded;
  protected readonly workflowTransitionPatched = QueueActions.workflowTransitionPatched;
  protected readonly workflowTransitionRemoved = QueueActions.workflowTransitionRemoved;
  protected readonly ticketDialogClosed = QueueActions.ticketDialogClosed;
  protected readonly ticketDialogSaved = QueueActions.ticketDialogSaved;
  protected readonly ticketDeleteRequested = QueueActions.ticketDeleteRequested;
  protected readonly toastCleared = QueueActions.toastCleared;
  protected readonly Boolean = Boolean;

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.store.dispatch(QueueActions.routeStateChanged({ state: routeStateFromParams(params) }));
    });
  }

  protected dispatchProjectSelected(projectId: string): void {
    this.store.dispatch(QueueActions.projectSelected({ projectId }));
  }

  protected dispatchTabSelected(tab: WorkspaceTab): void {
    this.store.dispatch(QueueActions.tabSelected({ tab }));
  }

  protected openCreateDialog(): void {
    this.store.dispatch(QueueActions.ticketDialogOpened({ mode: 'create', ticketId: null }));
  }

  protected openEditDialog(ticketId: string): void {
    this.store.dispatch(QueueActions.ticketDialogOpened({ mode: 'edit', ticketId }));
  }

  protected openTicket(ticketId: string): void {
    this.store.dispatch(QueueActions.ticketDetailOpened({ ticketId }));
  }

  protected transitionTicket(event: { ticket: Ticket; toStatusId: string }): void {
    this.store.dispatch(QueueActions.ticketTransitionRequested(event));
  }

  protected changeFilters(filters: Partial<TicketFilters>): void {
    this.store.dispatch(QueueActions.filtersChanged({ filters }));
  }

  protected createComment(event: { ticketId: string; body: string }): void {
    this.store.dispatch(QueueActions.commentCreateRequested({ ticketId: event.ticketId, request: { body: event.body } }));
  }

  protected showToast(message: string): void {
    this.store.dispatch(QueueActions.toastShown({ message }));
  }

  protected saveWorkflow(): void {
    const project = this.selectedProject();
    const workflow = this.workflowDraft();
    if (!project || !workflow) return;
    this.store.dispatch(
      QueueActions.workflowSaveRequested({
        projectId: project.id,
        request: {
          statuses: workflow.statuses,
          transitions: workflow.transitions
        }
      })
    );
  }
}

function routeStateFromParams(params: ParamMap): RouteWorkspaceState {
  const tab = params.get('tab');
  const activeTab = isWorkspaceTab(tab) ? tab : 'board';
  return {
    activeTab,
    selectedProjectId: params.get('projectId') ?? undefined,
    detailTicketId: params.get('ticketId'),
    filters: {
      q: params.get('q') ?? '',
      statusId: params.get('statusId') ?? '',
      typeId: params.get('typeId') ?? '',
      priority: priorityFromParam(params.get('priority')),
      assigneeId: params.get('assigneeId') ?? '',
      label: params.get('label') ?? '',
      sort: sortFromParam(params.get('sort'))
    }
  };
}

function isWorkspaceTab(value: string | null): value is WorkspaceTab {
  return value === 'board' || value === 'list' || value === 'detail' || value === 'admin';
}

function priorityFromParam(value: string | null): Priority | '' {
  return value === 'LOW' || value === 'MEDIUM' || value === 'HIGH' || value === 'CRITICAL' ? value : '';
}

function sortFromParam(value: string | null): TicketSort {
  return value === 'title' || value === 'priority' || value === 'status' || value === 'updated' ? value : 'number';
}
