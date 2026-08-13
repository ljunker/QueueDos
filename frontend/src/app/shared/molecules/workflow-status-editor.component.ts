import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Workflow } from '../../core/api.models';
import { WorkflowStatusPatch } from '../../state/queue.models';
import { sortedStatuses } from '../../state/queue.selectors';

@Component({
  selector: 'qd-workflow-status-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="workflow-section">
      <div class="section-heading">
        <div>
          <h4>Statuses</h4>
          <p>Define the ordered steps tickets can move through.</p>
        </div>
        <button type="button" (click)="statusAdded.emit()">Add status</button>
      </div>
      <div class="editor-list status-list">
        @for (status of sortedStatuses(workflow()); track status.id; let index = $index) {
          <div class="editor-row status-row">
            <label class="editor-field">
              <span>Name</span>
              <input [value]="status.name" (input)="patchStatus(index, { name: valueOf($event) })">
            </label>
            <label class="editor-field">
              <span>Category</span>
              <select [value]="status.category" (change)="patchStatus(index, { category: valueOf($event) })">
                <option value="TODO">Todo</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="DONE">Done</option>
              </select>
            </label>
            <div class="row-actions">
              <div class="order-buttons" aria-label="Change status order">
                <button type="button" class="icon-button" aria-label="Move status up" title="Move up" [disabled]="index === 0" (click)="statusMoved.emit({index, direction: -1})">↑</button>
                <button type="button" class="icon-button" aria-label="Move status down" title="Move down" [disabled]="index === workflow().statuses.length - 1" (click)="statusMoved.emit({index, direction: 1})">↓</button>
              </div>
              <button type="button" class="danger" (click)="statusRemoved.emit(index)">Remove</button>
            </div>
          </div>
        }
      </div>
    </section>
  `
})
export class WorkflowStatusEditorComponent {
  readonly workflow = input.required<Workflow>();

  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();
  readonly statusMoved = output<{index: number; direction: -1 | 1}>();

  protected readonly sortedStatuses = sortedStatuses;

  protected patchStatus(index: number, changes: WorkflowStatusPatch['changes']): void {
    this.statusPatched.emit({ index, changes });
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
