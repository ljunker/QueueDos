import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Project } from '../../core/api.models';

@Component({
  selector: 'qd-workspace-toolbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="toolbar">
      <div>
        <p class="eyebrow">{{ project()?.key ?? 'Project' }}</p>
        <h2>{{ project()?.name ?? 'No project' }}</h2>
      </div>
      <button type="button" class="primary" [disabled]="!project()" (click)="newTicketRequested.emit()">New ticket</button>
    </header>
  `
})
export class WorkspaceToolbarComponent {
  readonly project = input<Project | null>(null);
  readonly newTicketRequested = output<void>();
}
