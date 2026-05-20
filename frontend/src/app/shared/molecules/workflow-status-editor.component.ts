import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Workflow } from '../../core/api.models';
import { WorkflowStatusPatch } from '../../state/queue.models';
import { sortedStatuses } from '../../state/queue.selectors';

@Component({
  selector: 'qd-workflow-status-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <div class="section-heading">
        <span>Statuses</span>
        <button type="button" (click)="statusAdded.emit()">Add status</button>
      </div>
      <div class="editor-list">
        @for (status of sortedStatuses(workflow()); track status.id; let index = $index) {
          <div class="editor-row status-row">
            <input [value]="status.name" aria-label="Status name" (input)="patchStatus(index, { name: valueOf($event) })">
            <select [value]="status.category" aria-label="Status category" (change)="patchStatus(index, { category: valueOf($event) })">
              <option value="TODO">Todo</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="DONE">Done</option>
            </select>
            <button type="button" (click)="statusRemoved.emit(index)">Remove</button>
          </div>
        }
      </div>
    </div>
  `
})
export class WorkflowStatusEditorComponent {
  readonly workflow = input.required<Workflow>();

  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();

  protected readonly sortedStatuses = sortedStatuses;

  protected patchStatus(index: number, changes: WorkflowStatusPatch['changes']): void {
    this.statusPatched.emit({ index, changes });
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
