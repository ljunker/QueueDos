import {ChangeDetectionStrategy, Component, DestroyRef, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ActivatedRoute, ParamMap} from '@angular/router';
import {Store} from '@ngrx/store';

import {
  CreateSavedTicketFilterRequest,
  Priority,
  SavedTicketFilterCriteria,
  SavedTicketFilterView,
  Ticket
} from '../../core/api.models';
import {SidebarComponent} from '../../shared/organisms/sidebar.component';
import {TicketDialogComponent} from '../../shared/organisms/ticket-dialog.component';
import {ToastComponent} from '../../shared/atoms/toast.component';
import {WorkspaceTabHostComponent} from '../../shared/organisms/workspace-tab-host.component';
import {WorkspaceToolbarComponent} from '../../shared/organisms/workspace-toolbar.component';
import {QueueActions} from '../../state/queue.actions';
import {
  selectActiveTab,
  selectActiveUsers,
  selectCurrentUser,
  selectData,
  selectDialogProject,
  selectDialogTicket,
  selectDialogTypes,
  selectDialogWorkflow,
  selectError,
  selectFilters,
  selectIsAdmin,
  selectLoading,
  selectMyTickets,
  selectMyTicketsFilters,
  selectMyTicketsSavedFilters,
  selectOrganizations,
  selectPriorities,
  selectProjects,
  selectProjectSavedTicketFilters,
  selectProjectTickets,
  selectProjectTypes,
  selectSelectedProject,
  selectSelectedProjectId,
  selectSelectedTicket,
  selectSelectedTicketChanges,
  selectSelectedTicketComments,
  selectSelectedTicketTypes,
  selectSelectedTicketWorkflow,
  selectSelectedWorkflow,
  selectSortedStatuses,
  selectTicketDialog,
  selectToast,
  selectUsers,
  selectVisibleTickets,
  selectWorkflowDraft
} from '../../state/queue.selectors';
import {
  MyTicketsFilters,
  myTicketsSort,
  RouteWorkspaceState,
  TicketFilters,
  ticketSort,
  WorkspaceTab
} from '../../state/queue.models';

@Component({
  selector: 'qd-workspace-page',
  standalone: true,
  imports: [SidebarComponent, TicketDialogComponent, ToastComponent, WorkspaceTabHostComponent, WorkspaceToolbarComponent],
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
          <qd-workspace-toolbar [project]="selectedProject()" (newTicketRequested)="openCreateDialog()" />
          <qd-workspace-tab-host
            [data]="data()"
            [activeTab]="activeTab()"
            [isAdmin]="isAdmin()"
            [projects]="projects()"
            [selectedProject]="selectedProject()"
            [currentUser]="currentUser()"
            [users]="users()"
            [activeUsers]="activeUsers()"
            [workflow]="workflow()"
            [statuses]="statuses()"
            [projectTickets]="projectTickets()"
            [visibleTickets]="visibleTickets()"
            [myTickets]="myTickets()"
            [projectTypes]="projectTypes()"
            [priorities]="priorities()"
            [filters]="filters()"
            [myTicketsFilters]="myTicketsFilters()"
            [selectedTicket]="selectedTicket()"
            [selectedTicketWorkflow]="selectedTicketWorkflow()"
            [selectedTicketTypes]="selectedTicketTypes()"
            [selectedTicketComments]="selectedTicketComments()"
            [selectedTicketChanges]="selectedTicketChanges()"
            [workflowDraft]="workflowDraft()"
            [projectSavedFilters]="projectSavedFilters()"
            [myTicketsSavedFilters]="myTicketsSavedFilters()"
            (ticketOpened)="openTicket($event)"
            (ticketTransitioned)="transitionTicket($event)"
            (transitionDenied)="showToast('This workflow transition is not allowed.')"
            (filtersChanged)="changeFilters($event)"
            (myTicketsFiltersChanged)="changeMyTicketsFilters($event)"
            (detailClosed)="store.dispatch(detailClosed())"
            (editRequested)="openEditDialog($event)"
            (commitmentChanged)="store.dispatch(ticketCommitmentRequested($event))"
            (commentSubmitted)="store.dispatch(commentCreateRequested($event))"
            (projectCreated)="store.dispatch(projectCreateRequested({ request: $event }))"
            (projectSelected)="dispatchProjectSelected($event)"
            (userCreated)="store.dispatch(userCreateRequested({ request: $event }))"
            (userUpdated)="store.dispatch(userUpdateRequested($event))"
            (ticketTypeCreated)="store.dispatch(ticketTypeCreateRequested({ request: $event }))"
            (ticketTypeDeleted)="store.dispatch(ticketTypeDeleteRequested({ typeId: $event }))"
            (statusAdded)="store.dispatch(workflowStatusAdded())"
            (statusPatched)="store.dispatch(workflowStatusPatched($event))"
            (statusRemoved)="store.dispatch(workflowStatusRemoved({ index: $event }))"
            (transitionAdded)="store.dispatch(workflowTransitionAdded())"
            (transitionPatched)="store.dispatch(workflowTransitionPatched($event))"
            (transitionRemoved)="store.dispatch(workflowTransitionRemoved({ index: $event }))"
            (workflowSaved)="saveWorkflow()"
            (ticketRestored)="store.dispatch(ticketRestoreRequested({ ticketId: $event }))"
            (activityHookCreated)="store.dispatch(activityHookCreateRequested({ request: $event }))"
            (activityHookUpdated)="store.dispatch(activityHookUpdateRequested($event))"
            (activityHookDeleted)="store.dispatch(activityHookDeleteRequested({ hookId: $event }))"
            (savedFilterCreated)="createSavedFilter($event)"
            (savedFilterApplied)="store.dispatch(savedTicketFilterApplied({ filter: $event }))"
            (savedFilterRenamed)="renameSavedFilter($event)"
            (savedFilterDeleted)="store.dispatch(savedTicketFilterDeleteRequested({ filterId: $event }))"
            (bulkUpdateRequested)="store.dispatch(ticketsBulkUpdateRequested({ request: $event }))" />
        </section>

        <qd-ticket-dialog
          [open]="Boolean(ticketDialog())"
          [ticket]="dialogTicket()"
          [project]="dialogProject()"
          [workflow]="dialogWorkflow()"
          [types]="dialogTypes()"
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
  protected readonly myTickets = this.store.selectSignal(selectMyTickets);
  protected readonly filters = this.store.selectSignal(selectFilters);
  protected readonly myTicketsFilters = this.store.selectSignal(selectMyTicketsFilters);
  protected readonly projectSavedFilters = this.store.selectSignal(selectProjectSavedTicketFilters);
  protected readonly myTicketsSavedFilters = this.store.selectSignal(selectMyTicketsSavedFilters);
  protected readonly selectedTicket = this.store.selectSignal(selectSelectedTicket);
  protected readonly selectedTicketWorkflow = this.store.selectSignal(selectSelectedTicketWorkflow);
  protected readonly selectedTicketTypes = this.store.selectSignal(selectSelectedTicketTypes);
  protected readonly selectedTicketComments = this.store.selectSignal(selectSelectedTicketComments);
  protected readonly selectedTicketChanges = this.store.selectSignal(selectSelectedTicketChanges);
  protected readonly ticketDialog = this.store.selectSignal(selectTicketDialog);
  protected readonly dialogTicket = this.store.selectSignal(selectDialogTicket);
  protected readonly dialogProject = this.store.selectSignal(selectDialogProject);
  protected readonly dialogWorkflow = this.store.selectSignal(selectDialogWorkflow);
  protected readonly dialogTypes = this.store.selectSignal(selectDialogTypes);
  protected readonly workflowDraft = this.store.selectSignal(selectWorkflowDraft);
  protected readonly toast = this.store.selectSignal(selectToast);

  protected readonly logoutRequested = QueueActions.logoutRequested;
  protected readonly detailClosed = QueueActions.detailClosed;
  protected readonly commentCreateRequested = QueueActions.commentCreateRequested;
  protected readonly ticketCommitmentRequested = QueueActions.ticketCommitmentRequested;
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
  protected readonly ticketRestoreRequested = QueueActions.ticketRestoreRequested;
  protected readonly ticketsBulkUpdateRequested = QueueActions.ticketsBulkUpdateRequested;
  protected readonly savedTicketFilterApplied = QueueActions.savedTicketFilterApplied;
  protected readonly savedTicketFilterDeleteRequested = QueueActions.savedTicketFilterDeleteRequested;
  protected readonly activityHookCreateRequested = QueueActions.activityHookCreateRequested;
  protected readonly activityHookUpdateRequested = QueueActions.activityHookUpdateRequested;
  protected readonly activityHookDeleteRequested = QueueActions.activityHookDeleteRequested;
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

  protected changeMyTicketsFilters(filters: Partial<MyTicketsFilters>): void {
    this.store.dispatch(QueueActions.myTicketsFiltersChanged({ filters }));
  }

  protected createSavedFilter(event: { view: SavedTicketFilterView; name: string }): void {
    const request = this.savedFilterRequest(event.view, event.name);
    if (request) this.store.dispatch(QueueActions.savedTicketFilterCreateRequested({ request }));
  }

  protected renameSavedFilter(event: { filterId: string; name: string }): void {
    this.store.dispatch(
      QueueActions.savedTicketFilterUpdateRequested({
        filterId: event.filterId,
        request: { name: event.name }
      })
    );
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

  private savedFilterRequest(view: SavedTicketFilterView, name: string): CreateSavedTicketFilterRequest | null {
    if (view === 'PROJECT_LIST') {
      const project = this.selectedProject();
      if (!project) return null;
      return {
        name,
        view,
        projectId: project.id,
        filters: projectFilterCriteria(this.filters())
      };
    }
    return {
      name,
      view,
      projectId: null,
      filters: myTicketsFilterCriteria(this.myTicketsFilters())
    };
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
      sort: ticketSort(params.get('sort'))
    },
    myTicketsFilters: {
      projectId: params.get('myProjectId') ?? '',
      q: params.get('myQ') ?? '',
      priority: priorityFromParam(params.get('myPriority')),
      label: params.get('myLabel') ?? '',
      sort: myTicketsSort(params.get('mySort'))
    }
  };
}

function isWorkspaceTab(value: string | null): value is WorkspaceTab {
  return value === 'board' ||
    value === 'dashboard' ||
    value === 'list' ||
    value === 'my-tickets' ||
    value === 'detail' ||
    value === 'admin';
}

function priorityFromParam(value: string | null): Priority | '' {
  return value === 'LOW' || value === 'MEDIUM' || value === 'HIGH' || value === 'CRITICAL' ? value : '';
}

function projectFilterCriteria(filters: TicketFilters): SavedTicketFilterCriteria {
  return {
    projectId: null,
    q: filters.q,
    statusId: filters.statusId,
    typeId: filters.typeId,
    priority: filters.priority || null,
    assigneeId: filters.assigneeId,
    label: filters.label,
    sort: filters.sort
  };
}

function myTicketsFilterCriteria(filters: MyTicketsFilters): SavedTicketFilterCriteria {
  return {
    projectId: filters.projectId || null,
    q: filters.q,
    statusId: '',
    typeId: '',
    priority: filters.priority || null,
    assigneeId: '',
    label: filters.label,
    sort: filters.sort
  };
}
