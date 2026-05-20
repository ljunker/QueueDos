import { createReducer, on } from '@ngrx/store';

import { BootstrapResponse, Workflow } from '../core/api.models';
import { QueueActions } from './queue.actions';
import { cloneWorkflow, defaultFilters, TicketDialogState, TicketFilters, WorkspaceTab } from './queue.models';

export interface QueueState {
  token: string | null;
  data: BootstrapResponse | null;
  loading: boolean;
  authLoading: boolean;
  error: string;
  loginError: string;
  selectedProjectId: string | null;
  activeTab: WorkspaceTab;
  detailTicketId: string | null;
  filters: TicketFilters;
  ticketDialog: TicketDialogState | null;
  workflowDraft: Workflow | null;
  toast: string;
}

export const initialState: QueueState = {
  token: null,
  data: null,
  loading: false,
  authLoading: false,
  error: '',
  loginError: '',
  selectedProjectId: null,
  activeTab: 'board',
  detailTicketId: null,
  filters: defaultFilters(),
  ticketDialog: null,
  workflowDraft: null,
  toast: ''
};

export const queueReducer = createReducer(
  initialState,
  on(QueueActions.appStarted, (state, { token }) => ({
    ...state,
    token
  })),
  on(QueueActions.loginRequested, (state) => ({
    ...state,
    authLoading: true,
    loginError: ''
  })),
  on(QueueActions.loginSucceeded, (state, { token }) => ({
    ...state,
    token,
    authLoading: false,
    loginError: ''
  })),
  on(QueueActions.loginFailed, (state, { error }) => ({
    ...state,
    authLoading: false,
    loginError: error
  })),
  on(QueueActions.logoutCompleted, () => ({
    ...initialState
  })),
  on(QueueActions.bootstrapRequested, (state) => ({
    ...state,
    loading: true,
    error: ''
  })),
  on(QueueActions.bootstrapSucceeded, (state, { data }) => {
    const selectedProjectId = resolveProjectId(data, state.selectedProjectId);
    return {
      ...state,
      data,
      loading: false,
      error: '',
      selectedProjectId,
      activeTab: state.activeTab === 'admin' && data.currentUser.role !== 'ADMIN' ? 'board' : state.activeTab,
      workflowDraft: cloneWorkflow(workflowForProject(data, selectedProjectId))
    };
  }),
  on(QueueActions.bootstrapFailed, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(QueueActions.routeStateChanged, (state, { state: routeState }) => {
    const selectedProjectId = routeState.selectedProjectId ?? state.selectedProjectId;
    const activeTab = routeState.activeTab ?? state.activeTab;
    return {
      ...state,
      activeTab: activeTab === 'admin' && state.data?.currentUser.role !== 'ADMIN' ? 'board' : activeTab,
      selectedProjectId,
      detailTicketId: routeState.detailTicketId !== undefined ? routeState.detailTicketId : state.detailTicketId,
      filters: {
        ...state.filters,
        ...routeState.filters
      },
      workflowDraft:
        selectedProjectId !== state.selectedProjectId ? cloneWorkflow(workflowForProject(state.data, selectedProjectId)) : state.workflowDraft
    };
  }),
  on(QueueActions.projectSelected, (state, { projectId }) => ({
    ...state,
    selectedProjectId: projectId,
    detailTicketId: null,
    activeTab: state.activeTab === 'detail' ? 'board' : state.activeTab,
    workflowDraft: cloneWorkflow(workflowForProject(state.data, projectId))
  })),
  on(QueueActions.tabSelected, (state, { tab }) => ({
    ...state,
    activeTab: tab,
    detailTicketId: tab === 'detail' ? state.detailTicketId : null
  })),
  on(QueueActions.ticketDetailOpened, (state, { ticketId }) => ({
    ...state,
    activeTab: 'detail',
    detailTicketId: ticketId
  })),
  on(QueueActions.detailClosed, (state) => ({
    ...state,
    activeTab: 'list',
    detailTicketId: null
  })),
  on(QueueActions.filtersChanged, (state, { filters }) => ({
    ...state,
    activeTab: 'list',
    filters: {
      ...state.filters,
      ...filters
    }
  })),
  on(QueueActions.ticketDialogOpened, (state, dialog) => ({
    ...state,
    ticketDialog: dialog
  })),
  on(QueueActions.ticketDialogClosed, (state) => ({
    ...state,
    ticketDialog: null
  })),
  on(QueueActions.mutationSucceeded, (state, { focusTicketId }) => ({
    ...state,
    ticketDialog: null,
    detailTicketId: focusTicketId ?? state.detailTicketId,
    activeTab: focusTicketId ? 'detail' : state.activeTab
  })),
  on(QueueActions.mutationFailed, (state, { error }) => ({
    ...state,
    toast: error
  })),
  on(QueueActions.toastShown, (state, { message }) => ({
    ...state,
    toast: message
  })),
  on(QueueActions.toastCleared, (state) => ({
    ...state,
    toast: ''
  })),
  on(QueueActions.workflowDraftReset, (state) => ({
    ...state,
    workflowDraft: cloneWorkflow(workflowForProject(state.data, state.selectedProjectId))
  })),
  on(QueueActions.workflowStatusAdded, (state) => {
    if (!state.workflowDraft) return state;
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        statuses: [
          ...state.workflowDraft.statuses,
          {
            id: newId('status'),
            name: 'New status',
            category: 'TODO',
            sortOrder: state.workflowDraft.statuses.length
          }
        ]
      }
    };
  }),
  on(QueueActions.workflowStatusPatched, (state, { index, changes }) => {
    if (!state.workflowDraft) return state;
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        statuses: state.workflowDraft.statuses.map((status, statusIndex) =>
          statusIndex === index ? { ...status, ...changes } : status
        )
      }
    };
  }),
  on(QueueActions.workflowStatusRemoved, (state, { index }) => {
    if (!state.workflowDraft) return state;
    const removed = state.workflowDraft.statuses[index];
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        statuses: state.workflowDraft.statuses.filter((_, statusIndex) => statusIndex !== index),
        transitions: state.workflowDraft.transitions.filter((transition) => {
          return transition.fromStatusId !== removed?.id && transition.toStatusId !== removed?.id;
        })
      }
    };
  }),
  on(QueueActions.workflowTransitionAdded, (state) => {
    if (!state.workflowDraft || state.workflowDraft.statuses.length < 2) return state;
    const [fromStatus, toStatus] = state.workflowDraft.statuses;
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        transitions: [
          ...state.workflowDraft.transitions,
          {
            id: newId('transition'),
            fromStatusId: fromStatus.id,
            toStatusId: toStatus.id,
            allowedRoles: ['ADMIN', 'MEMBER'],
            requiredFields: [],
            globalTransition: false,
            allowBackward: true
          }
        ]
      }
    };
  }),
  on(QueueActions.workflowTransitionPatched, (state, { index, changes }) => {
    if (!state.workflowDraft) return state;
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        transitions: state.workflowDraft.transitions.map((transition, transitionIndex) =>
          transitionIndex === index ? { ...transition, ...changes } : transition
        )
      }
    };
  }),
  on(QueueActions.workflowTransitionRemoved, (state, { index }) => {
    if (!state.workflowDraft) return state;
    return {
      ...state,
      workflowDraft: {
        ...state.workflowDraft,
        transitions: state.workflowDraft.transitions.filter((_, transitionIndex) => transitionIndex !== index)
      }
    };
  })
);

function resolveProjectId(data: BootstrapResponse, preferredProjectId: string | null): string | null {
  const activeProjects = data.projects.filter((project) => !project.archived);
  if (preferredProjectId && data.projects.some((project) => project.id === preferredProjectId)) {
    return preferredProjectId;
  }
  return activeProjects[0]?.id ?? data.projects[0]?.id ?? null;
}

function workflowForProject(data: BootstrapResponse | null, projectId: string | null): Workflow | null {
  return data?.workflows.find((workflow) => workflow.projectId === projectId) ?? null;
}

function newId(prefix: string): string {
  return `${prefix}-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
}
