import {ChangeDetectionStrategy, Component, inject, input, OnDestroy, output, signal} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

import {CreateUserRequest, PublicUser, Role, UpdateUserRequest} from '../../core/api.models';
import {TemporaryPasswordService} from '../../core/temporary-password.service';
import {roleLabel} from '../../state/queue.selectors';

type RoleFilter = 'ALL' | Role;
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

@Component({
  selector: 'qd-admin-users-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel admin-users-page">
      <div class="section-heading">
        <div>
          <h3>Users</h3>
          <p class="muted">Manage access, roles and local sign-in for every account.</p>
        </div>
        <button type="button" class="primary" (click)="openCreate()">Add user</button>
      </div>

      <div class="user-filters">
        <label>
          Search
          <input type="search" placeholder="Name or email" [formControl]="searchControl">
        </label>
        <label>
          Role
          <select [formControl]="roleFilterControl">
            <option value="ALL">All roles</option>
            <option value="ADMIN">Admin</option>
            <option value="MEMBER">Member</option>
          </select>
        </label>
        <label>
          Status
          <select [formControl]="statusFilterControl">
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </label>
      </div>

      <div class="table-wrap user-table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Local login</th>
              <th>Password change</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (user of filteredUsers(); track user.id) {
              <tr (click)="openEdit(user)">
                <td><strong>{{ user.displayName }}</strong></td>
                <td>{{ user.email }}</td>
                <td>{{ roleLabel(user.role) }}</td>
                <td><span class="status-pill" [class.inactive]="!user.active">{{ user.active ? 'Active' : 'Inactive' }}</span></td>
                <td>{{ user.localLoginEnabled ? 'Enabled' : 'Azure only' }}</td>
                <td>{{ user.mustChangePassword ? 'Pending' : 'No' }}</td>
                <td><button type="button" class="ghost" (click)="$event.stopPropagation(); openEdit(user)">Edit</button></td>
              </tr>
            } @empty {
              <tr class="empty-row"><td colspan="7">No users match these filters.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (createOpen()) {
      <div class="dialog-backdrop" role="presentation" (click)="closeCreate()">
        <section class="dialog user-dialog" role="dialog" aria-modal="true" aria-labelledby="createUserTitle" (click)="$event.stopPropagation()">
          <form class="dialog-body" [formGroup]="createForm" (ngSubmit)="createUser()">
            <header>
              <div>
                <h3 id="createUserTitle">Add user</h3>
                <p class="muted">Email cannot be changed after creation.</p>
              </div>
              <button type="button" class="ghost" (click)="closeCreate()">Close</button>
            </header>
            <label>Email <input type="email" autocomplete="off" formControlName="email"></label>
            <label>Display name <input autocomplete="off" formControlName="displayName"></label>
            <label>
              Role
              <select formControlName="role">
                <option value="MEMBER">Member</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>
            <label class="checkbox-row">
              <input type="checkbox" formControlName="generateTemporaryPassword">
              <span>
                Generate temporary password
                <small>Without a temporary password, this account signs in through Microsoft only.</small>
              </span>
            </label>
            <footer>
              <button type="button" class="ghost" (click)="closeCreate()">Cancel</button>
              <span></span>
              <button type="submit" class="primary" [disabled]="createForm.invalid">Add user</button>
            </footer>
          </form>
        </section>
      </div>
    }

    @if (editingUser(); as user) {
      <div class="dialog-backdrop" role="presentation" (click)="closeEdit()">
        <section class="dialog user-dialog" role="dialog" aria-modal="true" aria-labelledby="editUserTitle" (click)="$event.stopPropagation()">
          <form class="dialog-body" [formGroup]="editForm" (ngSubmit)="saveUser(user)">
            <header>
              <div>
                <h3 id="editUserTitle">Edit user</h3>
                <p class="muted">{{ user.email }}</p>
              </div>
              <button type="button" class="ghost" (click)="closeEdit()">Close</button>
            </header>
            <label>Display name <input formControlName="displayName"></label>
            <label>
              Role
              <select formControlName="role" [attr.disabled]="isEditingSelf() ? true : null">
                <option value="MEMBER">Member</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>
            <label class="checkbox-row">
              <input type="checkbox" formControlName="active" [attr.disabled]="isEditingSelf() ? true : null">
              <span>Active account</span>
            </label>
            @if (isEditingSelf()) {
              <p class="muted">You cannot deactivate your own account or remove your own admin role.</p>
            }
            <div class="user-password-action">
              <div>
                <strong>Local login</strong>
                <small>{{ user.localLoginEnabled ? 'Enabled' : 'Azure only' }}{{ user.mustChangePassword ? ' · password change pending' : '' }}</small>
              </div>
              <button type="button" (click)="generatePassword(user)">Generate temporary password</button>
            </div>
            <footer>
              <button type="button" class="ghost" (click)="closeEdit()">Cancel</button>
              <span></span>
              <button type="submit" class="primary" [disabled]="editForm.invalid">Save changes</button>
            </footer>
          </form>
        </section>
      </div>
    }

    @if (temporaryPassword(); as credential) {
      <div class="dialog-backdrop" role="presentation">
        <section class="dialog temporary-password-dialog" role="dialog" aria-modal="true" aria-labelledby="temporaryPasswordTitle">
          <div class="dialog-body">
            <header>
              <div>
                <h3 id="temporaryPasswordTitle">Temporary password</h3>
                <p class="muted">Copy this password for {{ credential.displayName }}. It will not be shown again.</p>
              </div>
            </header>
            <div class="temporary-password-value">
              <code>{{ credential.temporaryPassword }}</code>
              <button type="button" (click)="copyPassword(credential.temporaryPassword)">{{ copied() ? 'Copied' : 'Copy' }}</button>
            </div>
            <p class="muted">The user must choose a new password after signing in.</p>
            <footer>
              <span></span><span></span>
              <button type="button" class="primary" (click)="closeTemporaryPassword()">Done</button>
            </footer>
          </div>
        </section>
      </div>
    }
  `
})
export class AdminUsersPanelComponent implements OnDestroy {
  private readonly temporaryPasswords = inject(TemporaryPasswordService);
  readonly users = input<PublicUser[]>([]);
  readonly currentUser = input<PublicUser | null>(null);

  readonly userCreated = output<{request: CreateUserRequest; generateTemporaryPassword: boolean}>();
  readonly userUpdated = output<{userId: string; request: UpdateUserRequest}>();
  readonly temporaryPasswordRequested = output<PublicUser>();

  protected readonly roleLabel = roleLabel;
  protected readonly createOpen = signal(false);
  protected readonly editingUser = signal<PublicUser | null>(null);
  protected readonly copied = signal(false);
  protected readonly temporaryPassword = this.temporaryPasswords.credential;
  protected readonly searchControl = new FormControl('', {nonNullable: true});
  protected readonly roleFilterControl = new FormControl<RoleFilter>('ALL', {nonNullable: true});
  protected readonly statusFilterControl = new FormControl<StatusFilter>('ALL', {nonNullable: true});
  protected readonly createForm = new FormGroup({
    email: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.email]}),
    displayName: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    role: new FormControl<Role>('MEMBER', {nonNullable: true}),
    generateTemporaryPassword: new FormControl(false, {nonNullable: true})
  });
  protected readonly editForm = new FormGroup({
    displayName: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    role: new FormControl<Role>('MEMBER', {nonNullable: true}),
    active: new FormControl(true, {nonNullable: true})
  });

  protected filteredUsers(): PublicUser[] {
    const query = this.searchControl.value.trim().toLowerCase();
    const role = this.roleFilterControl.value;
    const status = this.statusFilterControl.value;
    return [...this.users()]
      .filter((user) => !query || `${user.displayName} ${user.email}`.toLowerCase().includes(query))
      .filter((user) => role === 'ALL' || user.role === role)
      .filter((user) => status === 'ALL' || (status === 'ACTIVE' ? user.active : !user.active))
      .sort((left, right) => left.displayName.localeCompare(right.displayName, undefined, {sensitivity: 'base'}));
  }

  protected openCreate(): void {
    this.createForm.reset({email: '', displayName: '', role: 'MEMBER', generateTemporaryPassword: false});
    this.createOpen.set(true);
  }

  protected closeCreate(): void {
    this.createOpen.set(false);
  }

  protected createUser(): void {
    if (this.createForm.invalid) return;
    const {email, displayName, role, generateTemporaryPassword} = this.createForm.getRawValue();
    this.userCreated.emit({request: {email, displayName, role}, generateTemporaryPassword});
    this.closeCreate();
  }

  protected openEdit(user: PublicUser): void {
    this.editingUser.set(user);
    this.editForm.reset({displayName: user.displayName, role: user.role, active: user.active});
  }

  protected closeEdit(): void {
    this.editingUser.set(null);
  }

  protected isEditingSelf(): boolean {
    return this.editingUser()?.id === this.currentUser()?.id;
  }

  protected saveUser(user: PublicUser): void {
    if (this.editForm.invalid) return;
    const value = this.editForm.getRawValue();
    this.userUpdated.emit({userId: user.id, request: value});
    this.closeEdit();
  }

  protected generatePassword(user: PublicUser): void {
    if (!window.confirm(`Replace the local password for ${user.displayName} with a temporary password?`)) return;
    this.closeEdit();
    this.temporaryPasswordRequested.emit(user);
  }

  protected async copyPassword(password: string): Promise<void> {
    await navigator.clipboard.writeText(password);
    this.copied.set(true);
  }

  protected closeTemporaryPassword(): void {
    this.copied.set(false);
    this.temporaryPasswords.clear();
  }

  ngOnDestroy(): void {
    this.temporaryPasswords.clear();
  }
}
