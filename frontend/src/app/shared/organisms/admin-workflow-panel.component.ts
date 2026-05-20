import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Workflow } from '../../core/api.models';
import { WorkflowStatusPatch, WorkflowTransitionPatch } from '../../state/queue.models';
import { WorkflowStatusEditorComponent } from '../molecules/workflow-status-editor.component';
import { WorkflowTransitionEditorComponent } from '../molecules/workflow-transition-editor.component';

@Component({
  selector: 'qd-admin-workflow-panel',
  standalone: true,
  imports: [WorkflowStatusEditorComponent, WorkflowTransitionEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel wide">
      <h3>Workflow</h3>
      @if (workflowDraft(); as workflow) {
        <div class="workflow-editor">
          <qd-workflow-status-editor
            [workflow]="workflow"
            (statusAdded)="statusAdded.emit()"
            (statusPatched)="statusPatched.emit($event)"
            (statusRemoved)="statusRemoved.emit($event)" />
          <qd-workflow-transition-editor
            [workflow]="workflow"
            (transitionAdded)="transitionAdded.emit()"
            (transitionPatched)="transitionPatched.emit($event)"
            (transitionRemoved)="transitionRemoved.emit($event)" />
        </div>
        <div class="actions">
          <button type="button" class="primary" (click)="workflowSaved.emit(workflow)">Save workflow</button>
        </div>
      } @else {
        <p class="muted">No workflow configured.</p>
      }
    </section>
  `
})
export class AdminWorkflowPanelComponent {
  readonly workflowDraft = input<Workflow | null>(null);

  readonly statusAdded = output<void>();
  readonly statusPatched = output<WorkflowStatusPatch>();
  readonly statusRemoved = output<number>();
  readonly transitionAdded = output<void>();
  readonly transitionPatched = output<WorkflowTransitionPatch>();
  readonly transitionRemoved = output<number>();
  readonly workflowSaved = output<Workflow>();
}
