import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ApiClientService } from '../../core/api-client.service';
import { AuthTokenService } from '../../core/auth-token.service';
import { BootstrapResponse, Project, Ticket, TicketType, Workflow, WorkflowStatus } from '../../core/api.models';

@Component({
  selector: 'qd-board-page',
  standalone: true,
  template: `
    @if (loading()) {
      <p class="muted">Loading...</p>
    } @else if (error()) {
      <p class="error">{{ error() }}</p>
    } @else if (data(); as viewModel) {
      <section class="workspace-head">
        <div>
          <p class="eyebrow">{{ selectedProject()?.key || 'Project' }}</p>
          <h1>{{ selectedProject()?.name || 'QueueDos' }}</h1>
        </div>

        <label>
          Project
          <select [value]="selectedProjectId() || ''" (change)="selectProject($any($event.target).value)">
            @for (project of viewModel.projects; track project.id) {
              <option [value]="project.id">{{ project.key }} - {{ project.name }}</option>
            }
          </select>
        </label>
      </section>

      @if (workflow(); as projectWorkflow) {
        <section class="board">
          @for (status of sortedStatuses(projectWorkflow); track status.id) {
            <article class="column">
              <header>
                <span>{{ status.name }}</span>
                <span class="badge">{{ ticketsForStatus(status.id).length }}</span>
              </header>
              <div class="column-body">
                @for (ticket of ticketsForStatus(status.id); track ticket.id) {
                  <section class="ticket-card">
                    <div class="ticket-meta">
                      <span class="badge">{{ ticket.key }}</span>
                      <span class="badge">{{ ticket.priority }}</span>
                    </div>
                    <strong>{{ ticket.title }}</strong>
                    <div class="badges">
                      @if (typeById(ticket.typeId); as type) {
                        <span class="badge">
                          <span class="type-dot" [style.background]="type.color"></span>
                          {{ type.name }}
                        </span>
                      }
                    </div>
                    <small>{{ assigneeName(ticket.assigneeId) }}</small>
                  </section>
                } @empty {
                  <p class="muted">No tickets</p>
                }
              </div>
            </article>
          }
        </section>
      }
    }
  `
})
export class BoardPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly data = signal<BootstrapResponse | null>(null);
  protected readonly selectedProjectId = signal<string | null>(null);
  protected readonly selectedProject = computed(() => {
    const data = this.data();
    return data?.projects.find((project) => project.id === this.selectedProjectId()) ?? null;
  });
  protected readonly workflow = computed(() => {
    const project = this.selectedProject();
    const data = this.data();
    return project && data ? data.workflows.find((workflow) => workflow.projectId === project.id) ?? null : null;
  });

  constructor() {
    this.load();
  }

  protected selectProject(projectId: string): void {
    this.selectedProjectId.set(projectId || null);
  }

  protected sortedStatuses(workflow: Workflow): WorkflowStatus[] {
    return [...workflow.statuses].sort((left, right) => left.sortOrder - right.sortOrder);
  }

  protected ticketsForStatus(statusId: string): Ticket[] {
    return this.projectTickets()
      .filter((ticket) => ticket.statusId === statusId)
      .sort((left, right) => left.number - right.number);
  }

  protected typeById(typeId: string): TicketType | undefined {
    return this.data()?.ticketTypes.find((type) => type.id === typeId);
  }

  protected assigneeName(assigneeId: string | null): string {
    return this.data()?.users.find((user) => user.id === assigneeId)?.displayName ?? 'Unassigned';
  }

  private load(): void {
    if (!this.auth.hasToken()) {
      void this.router.navigateByUrl('/login');
      return;
    }

    this.api.bootstrap().subscribe({
      next: (data) => {
        this.data.set(data);
        this.selectedProjectId.set(data.projects.find((project) => !project.archived)?.id ?? data.projects[0]?.id ?? null);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.clear();
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error.set(error instanceof HttpErrorResponse ? error.error?.message ?? error.message : 'Could not load data.');
        this.loading.set(false);
      }
    });
  }

  private projectTickets(): Ticket[] {
    const projectId = this.selectedProjectId();
    return this.data()?.tickets.filter((ticket) => ticket.projectId === projectId) ?? [];
  }
}
