import { createActionGroup, emptyProps, props } from '@ngrx/store';

import {
  BootstrapResponse,
  BulkUpdateTicketsRequest,
  CreateSavedTicketFilterRequest,
  CreateProjectRequest,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  LoginRequest,
  LoginResponse,
  Project,
  PublicUser,
  SavedTicketFilter,
  SaveWorkflowRequest,
  Ticket,
  UpdateSavedTicketFilterRequest,
  UpdateUserRequest
} from '../core/api.models';
import {
  RouteWorkspaceState,
  TicketDialogSave,
  TicketDialogState,
  TicketFilters,
  MyTicketsFilters,
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
    'My Tickets Filters Changed': props<{ filters: Partial<MyTicketsFilters> }>(),
    'Saved Ticket Filter Applied': props<{ filter: SavedTicketFilter }>(),

    'Ticket Dialog Opened': props<TicketDialogState>(),
    'Ticket Dialog Closed': emptyProps(),
    'Ticket Dialog Saved': props<TicketDialogSave>(),
    'Ticket Create Requested': props<{ request: CreateTicketRequest }>(),
    'Ticket Update Requested': props<UpdateTicketWithTransition>(),
    'Ticket Transition Requested': props<{ ticket: Ticket; toStatusId: string }>(),
    'Ticket Delete Requested': props<{ ticketId: string }>(),
    'Tickets Bulk Update Requested': props<{ request: BulkUpdateTicketsRequest }>(),
    'Comment Create Requested': props<{ ticketId: string; request: CreateTicketCommentRequest }>(),

    'Project Create Requested': props<{ request: CreateProjectRequest }>(),
    'Project Created': props<{ project: Project }>(),
    'User Create Requested': props<{ request: CreateUserRequest }>(),
    'User Update Requested': props<{ userId: string; request: UpdateUserRequest }>(),
    'Ticket Type Create Requested': props<{ request: CreateTicketTypeRequest }>(),
    'Ticket Type Delete Requested': props<{ typeId: string }>(),
    'Workflow Save Requested': props<{ projectId: string; request: SaveWorkflowRequest }>(),
    'Saved Ticket Filter Create Requested': props<{ request: CreateSavedTicketFilterRequest }>(),
    'Saved Ticket Filter Update Requested': props<{ filterId: string; request: UpdateSavedTicketFilterRequest }>(),
    'Saved Ticket Filter Delete Requested': props<{ filterId: string }>(),

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
