import { createFeatureSelector, createSelector } from '@ngrx/store';

import {
  BootstrapResponse,
  Priority,
  Project,
  PublicUser,
  Ticket,
  TicketChange,
  TicketComment,
  TicketType,
  Workflow,
  WorkflowStatus
} from '../core/api.models';
import { QueueState } from './queue.reducer';
import { MyTicketsFilters, TicketFilters } from './queue.models';

export const selectQueueState = createFeatureSelector<QueueState>('queue');

export const selectToken = createSelector(selectQueueState, (state) => state.token);
export const selectData = createSelector(selectQueueState, (state) => state.data);
export const selectLoading = createSelector(selectQueueState, (state) => state.loading);
export const selectAuthLoading = createSelector(selectQueueState, (state) => state.authLoading);
export const selectError = createSelector(selectQueueState, (state) => state.error);
export const selectLoginError = createSelector(selectQueueState, (state) => state.loginError);
export const selectActiveTab = createSelector(selectQueueState, (state) => state.activeTab);
export const selectFilters = createSelector(selectQueueState, (state) => state.filters);
export const selectMyTicketsFilters = createSelector(selectQueueState, (state) => state.myTicketsFilters);
export const selectSelectedProjectId = createSelector(selectQueueState, (state) => state.selectedProjectId);
export const selectDetailTicketId = createSelector(selectQueueState, (state) => state.detailTicketId);
export const selectTicketDialog = createSelector(selectQueueState, (state) => state.ticketDialog);
export const selectWorkflowDraft = createSelector(selectQueueState, (state) => state.workflowDraft);
export const selectToast = createSelector(selectQueueState, (state) => state.toast);

export const selectCurrentUser = createSelector(selectData, (data) => data?.currentUser ?? null);
export const selectIsAdmin = createSelector(selectCurrentUser, (user) => user?.role === 'ADMIN');
export const selectOrganizations = createSelector(selectData, (data) => data?.organizations ?? []);
export const selectProjects = createSelector(selectData, (data) => data?.projects ?? []);
export const selectUsers = createSelector(selectData, (data) => data?.users ?? []);
export const selectActiveUsers = createSelector(selectUsers, (users) => users.filter((user) => user.active));
export const selectTicketTypes = createSelector(selectData, (data) => data?.ticketTypes ?? []);
export const selectPriorities = createSelector(selectData, (data) => data?.priorities ?? []);
export const selectSavedTicketFilters = createSelector(selectData, (data) => data?.savedTicketFilters ?? []);

export const selectSelectedProject = createSelector(
  selectProjects,
  selectSelectedProjectId,
  (projects, projectId) => projects.find((project) => project.id === projectId) ?? null
);

export const selectSelectedWorkflow = createSelector(
  selectData,
  selectSelectedProjectId,
  (data, projectId) => data?.workflows.find((workflow) => workflow.projectId === projectId) ?? null
);

export const selectSortedStatuses = createSelector(selectSelectedWorkflow, (workflow) => sortedStatuses(workflow));

export const selectProjectTickets = createSelector(
  selectData,
  selectSelectedProjectId,
  (data, projectId) => data?.tickets.filter((ticket) => ticket.projectId === projectId) ?? []
);

export const selectProjectTypes = createSelector(
  selectTicketTypes,
  selectSelectedProjectId,
  (types, projectId) => types.filter((type) => type.projectId === projectId)
);

export const selectDialogTicket = createSelector(
  selectData,
  selectTicketDialog,
  (data, dialog) => (dialog?.ticketId ? data?.tickets.find((ticket) => ticket.id === dialog.ticketId) ?? null : null)
);

export const selectVisibleTickets = createSelector(
  selectProjectTickets,
  selectFilters,
  selectSelectedWorkflow,
  (tickets, filters, workflow) => sortTickets(filterTickets(tickets, filters), filters.sort, workflow)
);

export const selectMyTickets = createSelector(
  selectData,
  selectCurrentUser,
  selectMyTicketsFilters,
  selectProjects,
  (data, user, filters, projects) =>
    sortMyTickets(
      filterMyTickets(
        data?.tickets.filter((ticket) => ticket.assigneeId === user?.id) ?? [],
        filters
      ),
      filters.sort,
      projects
    )
);

export const selectSelectedTicketComments = createSelector(
  selectData,
  selectDetailTicketId,
  (data, ticketId) => commentsForTicket(data, ticketId)
);

export const selectSelectedTicketChanges = createSelector(
  selectData,
  selectDetailTicketId,
  (data, ticketId) => changesForTicket(data, ticketId)
);

export const selectUrlQueryParams = createSelector(
  selectActiveTab,
  selectSelectedProjectId,
  selectDetailTicketId,
  selectFilters,
  selectMyTicketsFilters,
  (activeTab, projectId, ticketId, filters, myTicketsFilters) => {
    const queryParams: Record<string, string> = {};
    if (activeTab !== 'board') queryParams['tab'] = activeTab;
    if (projectId) queryParams['projectId'] = projectId;
    if (ticketId) queryParams['ticketId'] = ticketId;
    if (filters.q) queryParams['q'] = filters.q;
    if (filters.statusId) queryParams['statusId'] = filters.statusId;
    if (filters.typeId) queryParams['typeId'] = filters.typeId;
    if (filters.priority) queryParams['priority'] = filters.priority;
    if (filters.assigneeId) queryParams['assigneeId'] = filters.assigneeId;
    if (filters.label) queryParams['label'] = filters.label;
    if (filters.sort !== 'number') queryParams['sort'] = filters.sort;
    if (myTicketsFilters.projectId) queryParams['myProjectId'] = myTicketsFilters.projectId;
    if (myTicketsFilters.q) queryParams['myQ'] = myTicketsFilters.q;
    if (myTicketsFilters.priority) queryParams['myPriority'] = myTicketsFilters.priority;
    if (myTicketsFilters.label) queryParams['myLabel'] = myTicketsFilters.label;
    if (myTicketsFilters.sort !== 'number') queryParams['mySort'] = myTicketsFilters.sort;
    return queryParams;
  }
);

export const selectProjectSavedTicketFilters = createSelector(
  selectSavedTicketFilters,
  selectSelectedProjectId,
  (filters, projectId) => filters.filter((filter) => filter.view === 'PROJECT_LIST' && filter.projectId === projectId)
);

export const selectMyTicketsSavedFilters = createSelector(
  selectSavedTicketFilters,
  (filters) => filters.filter((filter) => filter.view === 'MY_TICKETS')
);

export const selectSelectedTicket = createSelector(
  selectData,
  selectDetailTicketId,
  (data, ticketId) => data?.tickets.find((ticket) => ticket.id === ticketId) ?? null
);

export const selectSelectedTicketWorkflow = createSelector(
  selectData,
  selectSelectedTicket,
  (data, ticket) => data?.workflows.find((workflow) => workflow.projectId === ticket?.projectId) ?? null
);

export const selectSelectedTicketTypes = createSelector(
  selectTicketTypes,
  selectSelectedTicket,
  (types, ticket) => types.filter((type) => type.projectId === ticket?.projectId)
);

export const selectDialogProject = createSelector(
  selectProjects,
  selectDialogTicket,
  selectSelectedProject,
  (projects, ticket, selectedProject) =>
    ticket ? projects.find((project) => project.id === ticket.projectId) ?? null : selectedProject
);

export const selectDialogWorkflow = createSelector(
  selectData,
  selectDialogProject,
  (data, project) => data?.workflows.find((workflow) => workflow.projectId === project?.id) ?? null
);

export const selectDialogTypes = createSelector(
  selectTicketTypes,
  selectDialogProject,
  (types, project) => types.filter((type) => type.projectId === project?.id)
);

export function typeById(types: TicketType[], typeId: string): TicketType | null {
  return types.find((type) => type.id === typeId) ?? null;
}

export function userById(users: PublicUser[], userId: string | null): PublicUser | null {
  return users.find((user) => user.id === userId) ?? null;
}

export function statusById(workflow: Workflow | null, statusId: string): WorkflowStatus | null {
  return workflow?.statuses.find((status) => status.id === statusId) ?? null;
}

export function priorityLabel(priority: Priority): string {
  return {
    LOW: 'Low',
    MEDIUM: 'Medium',
    HIGH: 'High',
    CRITICAL: 'Critical'
  }[priority];
}

export function roleLabel(role: PublicUser['role']): string {
  return role === 'ADMIN' ? 'Admin' : 'Member';
}

export function sortedStatuses(workflow: Workflow | null): WorkflowStatus[] {
  return [...(workflow?.statuses ?? [])].sort((left, right) => left.sortOrder - right.sortOrder);
}

export function statusRank(workflow: Workflow | null, statusId: string): number {
  return workflow?.statuses.find((status) => status.id === statusId)?.sortOrder ?? 999;
}

function filterTickets(tickets: Ticket[], filters: TicketFilters): Ticket[] {
  const query = filters.q.trim().toLowerCase();
  const label = filters.label.trim().toLowerCase();

  return tickets.filter((ticket) => {
    const searchable = `${ticket.key} ${ticket.title} ${ticket.description} ${ticket.labels.join(' ')}`.toLowerCase();
    return (
      (!query || searchable.includes(query)) &&
      (!filters.statusId || ticket.statusId === filters.statusId) &&
      (!filters.typeId || ticket.typeId === filters.typeId) &&
      (!filters.priority || ticket.priority === filters.priority) &&
      (!filters.assigneeId ||
        (filters.assigneeId === 'unassigned' ? !ticket.assigneeId : ticket.assigneeId === filters.assigneeId)) &&
      (!label || ticket.labels.some((ticketLabel) => ticketLabel.toLowerCase() === label))
    );
  });
}

function sortTickets(tickets: Ticket[], sort: TicketFilters['sort'], workflow: Workflow | null): Ticket[] {
  const copy = [...tickets];
  if (sort === 'title') return copy.sort((left, right) => left.title.localeCompare(right.title));
  if (sort === 'priority') return copy.sort((left, right) => priorityRank(right.priority) - priorityRank(left.priority));
  if (sort === 'status') return copy.sort((left, right) => statusRank(workflow, left.statusId) - statusRank(workflow, right.statusId));
  if (sort === 'updated') return copy.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  return copy.sort((left, right) => left.number - right.number);
}

function filterMyTickets(tickets: Ticket[], filters: MyTicketsFilters): Ticket[] {
  const query = filters.q.trim().toLowerCase();
  const label = filters.label.trim().toLowerCase();
  return tickets.filter((ticket) => {
    const searchable = `${ticket.key} ${ticket.title} ${ticket.description} ${ticket.labels.join(' ')}`.toLowerCase();
    return (
      (!filters.projectId || ticket.projectId === filters.projectId) &&
      (!query || searchable.includes(query)) &&
      (!filters.priority || ticket.priority === filters.priority) &&
      (!label || ticket.labels.some((ticketLabel) => ticketLabel.toLowerCase() === label))
    );
  });
}

function sortMyTickets(tickets: Ticket[], sort: MyTicketsFilters['sort'], projects: Project[]): Ticket[] {
  const copy = [...tickets];
  if (sort === 'title') return copy.sort((left, right) => left.title.localeCompare(right.title));
  if (sort === 'priority') return copy.sort((left, right) => priorityRank(right.priority) - priorityRank(left.priority));
  if (sort === 'updated') return copy.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  return copy.sort((left, right) => {
    const projectCompare = projectKey(projects, left.projectId).localeCompare(projectKey(projects, right.projectId));
    return projectCompare || left.number - right.number;
  });
}

function projectKey(projects: Project[], projectId: string): string {
  return projects.find((project) => project.id === projectId)?.key ?? '';
}

function priorityRank(priority: Priority): number {
  return ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].indexOf(priority);
}

function commentsForTicket(data: BootstrapResponse | null, ticketId: string | null): TicketComment[] {
  return [...(data?.comments ?? [])]
    .filter((comment) => comment.ticketId === ticketId)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

function changesForTicket(data: BootstrapResponse | null, ticketId: string | null): TicketChange[] {
  return [...(data?.ticketChanges ?? [])]
    .filter((change) => change.ticketId === ticketId)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}
