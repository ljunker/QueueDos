import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Role, Workflow } from '../../core/api.models';
import { WorkflowTransitionPatch } from '../../state/queue.models';
import { sortedStatuses } from '../../state/queue.selectors';

@Component({
  selector: 'qd-workflow-transition-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <div class="section-heading">
        <span>Transitions</span>
        <button type="button" (click)="transitionAdded.emit()" [disabled]="workflow().statuses.length < 2">Add transition</button>
      </div>
      <div class="editor-list">
        @for (transition of workflow().transitions; track transition.id; let index = $index) {
          <div class="editor-row transition-row">
            <select
              [value]="transition.fromStatusId ?? ''"
              aria-label="From status"
              (change)="patchTransition(index, { fromStatusId: valueOf($event) || null, globalTransition: !valueOf($event) })">
              <option value="">Any status</option>
              @for (status of sortedStatuses(workflow()); track status.id) {
                <option [value]="status.id">{{ status.name }}</option>
              }
            </select>
            <select
              [value]="transition.toStatusId"
              aria-label="To status"
              (change)="patchTransition(index, { toStatusId: valueOf($event) })">
              @for (status of sortedStatuses(workflow()); track status.id) {
                <option [value]="status.id">{{ status.name }}</option>
              }
            </select>
            <select
              [value]="roleSelectValue(transition.allowedRoles)"
              aria-label="Allowed roles"
              (change)="patchTransition(index, { allowedRoles: rolesFromSelect($event) })">
              <option value="BOTH">Admin + Member</option>
              <option value="ADMIN">Admin</option>
              <option value="MEMBER">Member</option>
            </select>
            <input
              [value]="transition.requiredFields.join(', ')"
              placeholder="required fields"
              (input)="patchTransition(index, { requiredFields: fieldsFromInput($event) })">
            <label class="inline-check">
              <input
                type="checkbox"
                [checked]="transition.globalTransition"
                (change)="patchTransition(index, { globalTransition: checked($event), fromStatusId: checked($event) ? null : workflow().statuses[0]?.id ?? null })">
              Global
            </label>
            <label class="inline-check">
              <input
                type="checkbox"
                [checked]="transition.allowBackward !== false"
                (change)="patchTransition(index, { allowBackward: checked($event) })">
              Back
            </label>
            <button type="button" (click)="transitionRemoved.emit(index)">Remove</button>
          </div>
        }
      </div>
    </div>
  `
})
export class WorkflowTransitionEditorComponent {
  readonly workflow = input.required<Workflow>();

  readonly transitionAdded = output<void>();
  readonly transitionPatched = output<WorkflowTransitionPatch>();
  readonly transitionRemoved = output<number>();

  protected readonly sortedStatuses = sortedStatuses;

  protected patchTransition(index: number, changes: WorkflowTransitionPatch['changes']): void {
    this.transitionPatched.emit({ index, changes });
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected fieldsFromInput(event: Event): string[] {
    return this.valueOf(event).split(',').map((value) => value.trim()).filter(Boolean);
  }

  protected roleSelectValue(roles: Role[]): 'BOTH' | Role {
    return roles.includes('ADMIN') && roles.includes('MEMBER') ? 'BOTH' : roles[0] ?? 'MEMBER';
  }

  protected rolesFromSelect(event: Event): Role[] {
    const value = this.valueOf(event);
    return value === 'BOTH' ? ['ADMIN', 'MEMBER'] : [value as Role];
  }
}
