import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Role, Workflow } from '../../core/api.models';
import { WorkflowTransitionPatch } from '../../state/queue.models';
import { sortedStatuses } from '../../state/queue.selectors';

@Component({
  selector: 'qd-workflow-transition-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="workflow-section">
      <div class="section-heading">
        <div>
          <h4>Transitions</h4>
          <p>Control which status changes are available and who may use them.</p>
        </div>
        <button type="button" (click)="transitionAdded.emit()" [disabled]="workflow().statuses.length < 2">Add transition</button>
      </div>
      <div class="editor-list transition-list">
        @for (transition of workflow().transitions; track transition.id; let index = $index) {
          <div class="editor-row transition-row">
            <label class="editor-field">
              <span>From</span>
              <select
                [value]="transition.fromStatusId ?? ''"
                (change)="patchTransition(index, { fromStatusId: valueOf($event) || null, globalTransition: !valueOf($event) })">
                <option value="">Any status</option>
                @for (status of sortedStatuses(workflow()); track status.id) {
                  <option [value]="status.id">{{ status.name }}</option>
                }
              </select>
            </label>
            <label class="editor-field">
              <span>To</span>
              <select
                [value]="transition.toStatusId"
                (change)="patchTransition(index, { toStatusId: valueOf($event) })">
                @for (status of sortedStatuses(workflow()); track status.id) {
                  <option [value]="status.id">{{ status.name }}</option>
                }
              </select>
            </label>
            <label class="editor-field">
              <span>Allowed roles</span>
              <select
                [value]="roleSelectValue(transition.allowedRoles)"
                (change)="patchTransition(index, { allowedRoles: rolesFromSelect($event) })">
                <option value="BOTH">Admin + Member</option>
                <option value="ADMIN">Admin</option>
                <option value="MEMBER">Member</option>
              </select>
            </label>
            <label class="editor-field">
              <span>Required fields</span>
              <input
                [value]="transition.requiredFields.join(', ')"
                placeholder="e.g. assignee, priority"
                (input)="patchTransition(index, { requiredFields: fieldsFromInput($event) })">
            </label>
            <div class="transition-controls">
              <div class="transition-flags">
                <label class="inline-check">
                  <input
                    type="checkbox"
                    [checked]="transition.globalTransition"
                    (change)="patchTransition(index, { globalTransition: checked($event), fromStatusId: checked($event) ? null : workflow().statuses[0]?.id ?? null })">
                  From any status
                </label>
                <label class="inline-check">
                  <input
                    type="checkbox"
                    [checked]="transition.allowBackward !== false"
                    (change)="patchTransition(index, { allowBackward: checked($event) })">
                  Allow backward move
                </label>
              </div>
              <button type="button" class="danger" (click)="transitionRemoved.emit(index)">Remove</button>
            </div>
          </div>
        }
      </div>
    </section>
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
