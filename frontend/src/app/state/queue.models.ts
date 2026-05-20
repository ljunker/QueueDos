import {
  CreateTicketRequest,
  Priority,
  UpdateTicketRequest,
  Workflow,
  WorkflowStatus,
  WorkflowTransition
} from '../core/api.models';

export type WorkspaceTab = 'board' | 'list' | 'detail' | 'admin';
export type TicketSort = 'number' | 'title' | 'priority' | 'status' | 'updated';

export interface TicketFilters {
  q: string;
  statusId: string;
  typeId: string;
  priority: Priority | '';
  assigneeId: string;
  label: string;
  sort: TicketSort;
}

export interface RouteWorkspaceState {
  activeTab?: WorkspaceTab;
  selectedProjectId?: string;
  detailTicketId?: string | null;
  filters?: Partial<TicketFilters>;
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

export function cloneWorkflow(workflow: Workflow | null | undefined): Workflow | null {
  if (!workflow) return null;
  return structuredClone(workflow);
}
