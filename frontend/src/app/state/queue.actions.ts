import { createActionGroup, emptyProps, props } from '@ngrx/store';

import {
  BootstrapResponse,
  CreateProjectRequest,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  LoginRequest,
  LoginResponse,
  Project,
  PublicUser,
  SaveWorkflowRequest,
  Ticket,
  UpdateUserRequest
} from '../core/api.models';
import {
  RouteWorkspaceState,
  TicketDialogSave,
  TicketDialogState,
  TicketFilters,
  UpdateTicketWithTransition,
  WorkflowStatusPatch,
  WorkflowTransitionPatch,
  WorkspaceTab
} from './queue.models';

export const QueueActions = createActionGroup({
  source: 'Queue',
  events: {
    'App Started': props<{ token: string | null }>(),
    'Login Requested': props<{ request: LoginRequest }>(),
    'Login Succeeded': props<LoginResponse>(),
    'Login Failed': props<{ error: string }>(),
    'Logout Requested': emptyProps(),
    'Logout Completed': emptyProps(),

    'Bootstrap Requested': emptyProps(),
    'Bootstrap Succeeded': props<{ data: BootstrapResponse }>(),
    'Bootstrap Failed': props<{ error: string }>(),

    'Route State Changed': props<{ state: RouteWorkspaceState }>(),
    'Project Selected': props<{ projectId: string }>(),
    'Tab Selected': props<{ tab: WorkspaceTab }>(),
    'Ticket Detail Opened': props<{ ticketId: string }>(),
    'Detail Closed': emptyProps(),
    'Filters Changed': props<{ filters: Partial<TicketFilters> }>(),

    'Ticket Dialog Opened': props<TicketDialogState>(),
    'Ticket Dialog Closed': emptyProps(),
    'Ticket Dialog Saved': props<TicketDialogSave>(),
    'Ticket Create Requested': props<{ request: CreateTicketRequest }>(),
    'Ticket Update Requested': props<UpdateTicketWithTransition>(),
    'Ticket Transition Requested': props<{ ticket: Ticket; toStatusId: string }>(),
    'Ticket Delete Requested': props<{ ticketId: string }>(),
    'Comment Create Requested': props<{ ticketId: string; request: CreateTicketCommentRequest }>(),

    'Project Create Requested': props<{ request: CreateProjectRequest }>(),
    'Project Created': props<{ project: Project }>(),
    'User Create Requested': props<{ request: CreateUserRequest }>(),
    'User Update Requested': props<{ userId: string; request: UpdateUserRequest }>(),
    'Ticket Type Create Requested': props<{ request: CreateTicketTypeRequest }>(),
    'Ticket Type Delete Requested': props<{ typeId: string }>(),
    'Workflow Save Requested': props<{ projectId: string; request: SaveWorkflowRequest }>(),

    'Mutation Succeeded': props<{ message?: string; focusTicketId?: string }>(),
    'Mutation Failed': props<{ error: string }>(),

    'Workflow Draft Reset': emptyProps(),
    'Workflow Status Added': emptyProps(),
    'Workflow Status Patched': props<WorkflowStatusPatch>(),
    'Workflow Status Removed': props<{ index: number }>(),
    'Workflow Transition Added': emptyProps(),
    'Workflow Transition Patched': props<WorkflowTransitionPatch>(),
    'Workflow Transition Removed': props<{ index: number }>(),

    'Toast Shown': props<{ message: string }>(),
    'Toast Cleared': emptyProps()
  }
});
