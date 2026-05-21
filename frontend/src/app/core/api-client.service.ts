import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  BootstrapResponse,
  BulkUpdateTicketsRequest,
  CreateSavedTicketFilterRequest,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  CreateProjectRequest,
  CreateTicketTypeRequest,
  CreateUserRequest,
  LoginRequest,
  LoginResponse,
  Project,
  SaveWorkflowRequest,
  Ticket,
  TicketComment,
  TicketType,
  TicketDetailResponse,
  TransitionTicketRequest,
  UpdateSavedTicketFilterRequest,
  UpdateUserRequest,
  UpdateTicketRequest
} from './api.models';

@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>('/api/auth/login', request);
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

  deleteTicket(id: string) {
    return this.http.delete<void>(`/api/tickets/${id}`);
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
}
