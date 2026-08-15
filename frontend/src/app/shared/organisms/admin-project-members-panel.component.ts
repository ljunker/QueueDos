import {ChangeDetectionStrategy, Component, computed, input, output, signal} from '@angular/core';
import {Project, ProjectMembership, ProjectRole, PublicUser} from '../../core/api.models';

@Component({
  selector: 'qd-admin-project-members-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <div class="section-heading">
        <div>
          <h3>Project members</h3>
          <p class="muted">Assign users to {{ project()?.name ?? 'the selected project' }} and manage their project role.</p>
        </div>
      </div>

      @if (project(); as selectedProject) {
        <form class="member-search" (submit)="$event.preventDefault(); search()">
          <input
            type="search"
            placeholder="Search active users by name or email"
            [value]="query()"
            (input)="query.set(valueOf($event))">
          <button type="submit" [disabled]="query().trim().length < 2">Search</button>
        </form>

        @if (candidates().length) {
          <div class="admin-list">
            @for (user of candidates(); track user.id) {
              <div class="admin-item">
                <div><strong>{{ user.displayName }}</strong><small>{{ user.email }}</small></div>
                <div class="actions">
                  <button type="button" (click)="membershipSaved.emit({projectId: selectedProject.id, userId: user.id, role: 'MEMBER'})">Add as member</button>
                  <button type="button" (click)="membershipSaved.emit({projectId: selectedProject.id, userId: user.id, role: 'ADMIN'})">Add as admin</button>
                </div>
              </div>
            }
          </div>
        }

        <div class="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Project role</th><th></th></tr></thead>
            <tbody>
              @for (item of members(); track item.membership.userId) {
                <tr>
                  <td><strong>{{ item.user.displayName }}</strong></td>
                  <td>{{ item.user.email }}</td>
                  <td>
                    <select [value]="item.membership.role" (change)="changeRole(selectedProject.id, item.user.id, $event)">
                      <option value="MEMBER">Member</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  </td>
                  <td><button type="button" class="danger" (click)="remove(selectedProject.id, item.user)">Remove</button></td>
                </tr>
              } @empty {
                <tr class="empty-row"><td colspan="4">No users are assigned to this project.</td></tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="muted">Select a project to manage its members.</p>
      }
    </section>
  `
})
export class AdminProjectMembersPanelComponent {
  readonly project = input<Project | null>(null);
  readonly memberships = input<ProjectMembership[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly candidates = input<PublicUser[]>([]);

  readonly memberSearchRequested = output<{projectId: string; query: string}>();
  readonly membershipSaved = output<{projectId: string; userId: string; role: ProjectRole}>();
  readonly membershipDeleted = output<{projectId: string; userId: string}>();

  protected readonly query = signal('');
  protected readonly members = computed(() => {
    const projectId = this.project()?.id;
    return this.memberships()
      .filter((membership) => membership.projectId === projectId)
      .map((membership) => ({membership, user: this.users().find((user) => user.id === membership.userId)}))
      .filter((item): item is {membership: ProjectMembership; user: PublicUser} => Boolean(item.user))
      .sort((left, right) => left.user.displayName.localeCompare(right.user.displayName));
  });

  protected search(): void {
    const projectId = this.project()?.id;
    const query = this.query().trim();
    if (projectId && query.length >= 2) this.memberSearchRequested.emit({projectId, query});
  }

  protected changeRole(projectId: string, userId: string, event: Event): void {
    this.membershipSaved.emit({projectId, userId, role: (event.target as HTMLSelectElement).value as ProjectRole});
  }

  protected remove(projectId: string, user: PublicUser): void {
    if (window.confirm(`Remove ${user.displayName} from this project? Existing ticket references will be kept.`)) {
      this.membershipDeleted.emit({projectId, userId: user.id});
    }
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
