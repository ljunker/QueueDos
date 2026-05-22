import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';

import {
  AuthConfigResponse,
  BootstrapResponse,
  BulkUpdateTicketsRequest,
  CreateActivityHookRequest,
  CreateProjectRequest,
  CreateSavedTicketFilterRequest,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  LoginRequest,
  LoginResponse,
  Project,
  SaveWorkflowRequest,
  Ticket,
  TicketComment,
  TicketDetailResponse,
  TicketType,
  TransitionTicketRequest,
  UpdateActivityHookRequest,
  UpdateSavedTicketFilterRequest,
  UpdateTicketRequest,
  UpdateUserRequest
} from './api.models';

@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>('/api/auth/login', request);
  }

  authConfig() {
    return this.http.get<AuthConfigResponse>('/api/auth/config');
  }

  bootstrap() {
    return this.http.get<BootstrapResponse>('/api/bootstrap');
  }

  createTicket(request: CreateTicketRequest) {
    return this.http.post<Ticket>('/api/tickets', request);
  }

  ticketDetail(id: string) {
    return this.http.get<TicketDetailResponse>(`/api/tickets/${id}`);
  }

  updateTicket(id: string, request: UpdateTicketRequest) {
    return this.http.put<Ticket>(`/api/tickets/${id}`, request);
  }

  transitionTicket(id: string, request: TransitionTicketRequest) {
    return this.http.post<Ticket>(`/api/tickets/${id}/transition`, request);
  }

  bulkUpdateTickets(request: BulkUpdateTicketsRequest) {
    return this.http.post<Ticket[]>('/api/tickets/bulk-update', request);
  }

  addComment(id: string, request: CreateTicketCommentRequest) {
    return this.http.post<TicketComment>(`/api/tickets/${id}/comments`, request);
  }

  saveCommitment(id: string, committed: boolean) {
    return this.http.post<Ticket>(`/api/tickets/${id}/commitment`, {committed});
  }

  deleteTicket(id: string) {
    return this.http.delete<void>(`/api/tickets/${id}`);
  }

  restoreTicket(id: string) {
    return this.http.post<Ticket>(`/api/tickets/${id}/restore`, {});
  }

  createProject(request: CreateProjectRequest) {
    return this.http.post<Project>('/api/projects', request);
  }

  createUser(request: CreateUserRequest) {
    return this.http.post('/api/users', request);
  }

  updateUser(id: string, request: UpdateUserRequest) {
    return this.http.put(`/api/users/${id}`, request);
  }

  createTicketType(request: CreateTicketTypeRequest) {
    return this.http.post<TicketType>('/api/ticket-types', request);
  }

  deleteTicketType(id: string) {
    return this.http.delete<void>(`/api/ticket-types/${id}`);
  }

  saveWorkflow(projectId: string, request: SaveWorkflowRequest) {
    return this.http.put(`/api/projects/${projectId}/workflow`, request);
  }

  createSavedTicketFilter(request: CreateSavedTicketFilterRequest) {
    return this.http.post('/api/saved-ticket-filters', request);
  }

  updateSavedTicketFilter(id: string, request: UpdateSavedTicketFilterRequest) {
    return this.http.put(`/api/saved-ticket-filters/${id}`, request);
  }

  deleteSavedTicketFilter(id: string) {
    return this.http.delete<void>(`/api/saved-ticket-filters/${id}`);
  }

  createActivityHook(request: CreateActivityHookRequest) {
    return this.http.post('/api/activity-hooks', request);
  }

  updateActivityHook(id: string, request: UpdateActivityHookRequest) {
    return this.http.put(`/api/activity-hooks/${id}`, request);
  }

  deleteActivityHook(id: string) {
    return this.http.delete<void>(`/api/activity-hooks/${id}`);
  }
}
