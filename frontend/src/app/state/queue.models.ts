import {
  CreateTicketRequest,
  Priority,
  SavedTicketFilterCriteria,
  UpdateTicketRequest,
  Workflow,
  WorkflowStatus,
  WorkflowTransition
} from '../core/api.models';

export type WorkspaceTab = 'board' | 'dashboard' | 'list' | 'my-tickets' | 'detail' | 'admin';
export type DetailReturnTab = Exclude<WorkspaceTab, 'detail'>;
export type TicketSort = 'number' | 'title' | 'priority' | 'status' | 'updated';
export type MyTicketsSort = Exclude<TicketSort, 'status'>;

export interface TicketFilters {
  q: string;
  statusId: string;
  typeId: string;
  priority: Priority | '';
  assigneeId: string;
  label: string;
  sort: TicketSort;
}

export interface MyTicketsFilters {
  projectId: string;
  q: string;
  priority: Priority | '';
  label: string;
  sort: MyTicketsSort;
}

export interface RouteWorkspaceState {
  activeTab?: WorkspaceTab;
  selectedProjectId?: string;
  detailTicketId?: string | null;
  filters?: Partial<TicketFilters>;
  myTicketsFilters?: Partial<MyTicketsFilters>;
}

export interface TicketDialogState {
  mode: 'create' | 'edit';
  ticketId: string | null;
}

export interface UpdateTicketWithTransition {
  id: string;
  request: UpdateTicketRequest;
  toStatusId: string;
}

export type TicketDialogSave =
  | { mode: 'create'; request: CreateTicketRequest }
  | { mode: 'edit'; request: UpdateTicketWithTransition };

export interface WorkflowStatusPatch {
  index: number;
  changes: Partial<Pick<WorkflowStatus, 'name' | 'category'>>;
}

export interface WorkflowTransitionPatch {
  index: number;
  changes: Partial<WorkflowTransition>;
}

export function defaultFilters(): TicketFilters {
  return {
    q: '',
    statusId: '',
    typeId: '',
    priority: '',
    assigneeId: '',
    label: '',
    sort: 'number'
  };
}

export function defaultMyTicketsFilters(): MyTicketsFilters {
  return {
    projectId: '',
    q: '',
    priority: '',
    label: '',
    sort: 'number'
  };
}

export function savedProjectFilters(criteria: SavedTicketFilterCriteria): TicketFilters {
  return {
    q: criteria.q,
    statusId: criteria.statusId,
    typeId: criteria.typeId,
    priority: criteria.priority ?? '',
    assigneeId: criteria.assigneeId,
    label: criteria.label,
    sort: ticketSort(criteria.sort)
  };
}

export function savedMyTicketsFilters(criteria: SavedTicketFilterCriteria): MyTicketsFilters {
  return {
    projectId: criteria.projectId ?? '',
    q: criteria.q,
    priority: criteria.priority ?? '',
    label: criteria.label,
    sort: myTicketsSort(criteria.sort)
  };
}

export function ticketSort(value: string | null | undefined): TicketSort {
  return value === 'title' || value === 'priority' || value === 'status' || value === 'updated' ? value : 'number';
}

export function myTicketsSort(value: string | null | undefined): MyTicketsSort {
  return value === 'title' || value === 'priority' || value === 'updated' ? value : 'number';
}

export function cloneWorkflow(workflow: Workflow | null | undefined): Workflow | null {
  if (!workflow) return null;
  return structuredClone(workflow);
}
