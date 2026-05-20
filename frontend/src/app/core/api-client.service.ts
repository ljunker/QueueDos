import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  BootstrapResponse,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  LoginRequest,
  LoginResponse,
  Ticket,
  TicketComment,
  TicketDetailResponse,
  TransitionTicketRequest,
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

  addComment(id: string, request: CreateTicketCommentRequest) {
    return this.http.post<TicketComment>(`/api/tickets/${id}/comments`, request);
  }
}
