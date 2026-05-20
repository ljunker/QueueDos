import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { CreateUserRequest, PublicUser, Role } from '../../core/api.models';
import { roleLabel } from '../../state/queue.selectors';

@Component({
  selector: 'qd-admin-users-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h3>Users</h3>
      <form class="stack" [formGroup]="userForm" (ngSubmit)="submitUser()">
        <input type="email" placeholder="email@example.com" formControlName="email">
        <input placeholder="Display name" formControlName="displayName">
        <div class="split">
          <select formControlName="role">
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Admin</option>
          </select>
          <input type="password" placeholder="Password" formControlName="password">
        </div>
        <button type="submit" [disabled]="userForm.invalid">Add user</button>
      </form>
      <div class="admin-list">
        @for (user of users(); track user.id) {
          <div class="admin-item">
            <div>
              <strong>{{ user.displayName }}</strong>
              <small>{{ user.email }} · {{ roleLabel(user.role) }} · {{ user.active ? 'active' : 'inactive' }}</small>
            </div>
            <button type="button" (click)="userToggled.emit({ userId: user.id, active: !user.active })">
              {{ user.active ? 'Disable' : 'Enable' }}
            </button>
          </div>
        }
      </div>
    </section>
  `
})
export class AdminUsersPanelComponent {
  readonly users = input<PublicUser[]>([]);

  readonly userCreated = output<CreateUserRequest>();
  readonly userToggled = output<{ userId: string; active: boolean }>();

  protected readonly roleLabel = roleLabel;
  protected readonly userForm = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    role: new FormControl<Role>('MEMBER', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected submitUser(): void {
    if (this.userForm.invalid) return;
    this.userCreated.emit(this.userForm.getRawValue());
    this.userForm.reset({ email: '', displayName: '', role: 'MEMBER', password: '' });
  }
}
