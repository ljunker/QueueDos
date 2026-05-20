import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Organization, Project, PublicUser } from '../../core/api.models';
import { roleLabel } from '../../state/queue.selectors';
import { WorkspaceTab } from '../../state/queue.models';

@Component({
  selector: 'qd-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="sidebar">
      <div class="brand">
        <span class="mark">Q</span>
        <div>
          <strong>QueueDos</strong>
          <small>{{ organizations()[0]?.name ?? 'Organization' }}</small>
        </div>
      </div>

      <label class="project-switcher">
        Project
        <select [value]="selectedProjectId() ?? ''" (change)="projectSelected.emit(valueOf($event))">
          @for (project of projects(); track project.id) {
            <option [value]="project.id">{{ project.key }} - {{ project.name }}</option>
          }
        </select>
      </label>

      <nav class="tabs" aria-label="Primary">
        <button type="button" class="tab" [class.active]="activeTab() === 'board'" (click)="tabSelected.emit('board')">Board</button>
        <button type="button" class="tab" [class.active]="activeTab() === 'list'" (click)="tabSelected.emit('list')">List</button>
        @if (isAdmin()) {
          <button type="button" class="tab" [class.active]="activeTab() === 'admin'" (click)="tabSelected.emit('admin')">Admin</button>
        }
      </nav>

      <div class="sidebar-footer">
        @if (user(); as currentUser) {
          <span>{{ currentUser.displayName }} ({{ roleLabel(currentUser.role) }})</span>
        }
        <button type="button" class="ghost" (click)="logoutRequested.emit()">Sign out</button>
      </div>
    </aside>
  `
})
export class SidebarComponent {
  readonly organizations = input<Organization[]>([]);
  readonly projects = input<Project[]>([]);
  readonly selectedProjectId = input<string | null>(null);
  readonly user = input<PublicUser | null>(null);
  readonly activeTab = input<WorkspaceTab>('board');
  readonly isAdmin = input(false);

  readonly projectSelected = output<string>();
  readonly tabSelected = output<WorkspaceTab>();
  readonly logoutRequested = output<void>();

  protected readonly roleLabel = roleLabel;

  protected valueOf(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }
}
