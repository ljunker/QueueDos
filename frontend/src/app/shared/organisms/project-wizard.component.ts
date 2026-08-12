import {ChangeDetectionStrategy, Component, effect, input, output, signal} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

import {
  CreateProjectRequest,
  CreateProjectStatusRequest,
  Project
} from '../../core/api.models';

type StatusCategory = CreateProjectStatusRequest['category'];

interface TicketTypeDraft {
  localId: number;
  name: string;
  description: string;
  color: string;
}

interface StatusDraft {
  localId: number;
  name: string;
  category: StatusCategory;
}

@Component({
  selector: 'qd-project-wizard',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="dialog-backdrop" (click)="requestClose()">
        <section class="dialog project-wizard" role="dialog" aria-modal="true" aria-labelledby="project-wizard-title" (click)="$event.stopPropagation()">
          <form class="dialog-body wizard-body" [formGroup]="detailsForm" (ngSubmit)="submit()">
            <header>
              <div>
                <h3 id="project-wizard-title">New project</h3>
                <p class="muted">Set up a project that is ready for its first ticket.</p>
              </div>
              <button type="button" class="icon-button" aria-label="Close" [disabled]="creating()" (click)="requestClose()">x</button>
            </header>

            <ol class="wizard-steps" aria-label="Project setup progress">
              @for (label of stepLabels; track label; let index = $index) {
                <li [class.active]="step() === index" [class.complete]="step() > index">
                  <span>{{ index + 1 }}</span>{{ label }}
                </li>
              }
            </ol>

            <div class="wizard-content">
              @switch (step()) {
                @case (0) {
                  <div class="wizard-section">
                    <div class="form-grid">
                      <label>
                        Project name
                        <input formControlName="name" maxlength="160" autocomplete="off" required>
                      </label>
                      <label>
                        Key
                        <input formControlName="key" maxlength="10" autocomplete="off" placeholder="PROJ" required (input)="uppercaseKey()">
                      </label>
                    </div>
                    <label>
                      Description <span class="optional">Optional</span>
                      <textarea rows="4" formControlName="description"></textarea>
                    </label>
                    <label class="color-field">
                      Project color
                      <span class="color-control">
                        <input type="color" aria-label="Project color" formControlName="color">
                        <code>{{ detailsForm.controls.color.value }}</code>
                      </span>
                    </label>
                    @if (detailsForm.controls.key.touched && detailsForm.controls.key.invalid) {
                      <p class="error">Use 2-10 uppercase letters or numbers and start with a letter.</p>
                    } @else if (keyExists()) {
                      <p class="error">This project key already exists.</p>
                    }
                  </div>
                }
                @case (1) {
                  <div class="wizard-section">
                    <div class="section-heading">
                      <div>
                        <span>Ticket types</span>
                        <small>At least one type is required.</small>
                      </div>
                      <button type="button" (click)="addTicketType()">Add ticket type</button>
                    </div>
                    <div class="wizard-editor-list">
                      @for (type of ticketTypes(); track type.localId) {
                        <div class="wizard-editor-row ticket-type-draft">
                          <input aria-label="Ticket type name" maxlength="160" placeholder="Type name" [value]="type.name" (input)="patchTicketType(type.localId, 'name', valueOf($event))">
                          <input aria-label="Ticket type description" placeholder="Description (optional)" [value]="type.description" (input)="patchTicketType(type.localId, 'description', valueOf($event))">
                          <input type="color" aria-label="Ticket type color" [value]="type.color" (input)="patchTicketType(type.localId, 'color', valueOf($event))">
                          <button type="button" aria-label="Remove ticket type" (click)="removeTicketType(type.localId)">Remove</button>
                        </div>
                      } @empty {
                        <div class="wizard-empty">
                          <strong>No ticket types yet</strong>
                          <span>Add the types people can choose when creating tickets.</span>
                        </div>
                      }
                    </div>
                    @if (ticketTypes().length > 0 && !ticketTypesValid()) {
                      <p class="error">Every type needs a unique name.</p>
                    }
                  </div>
                }
                @case (2) {
                  <div class="wizard-section">
                    <div class="section-heading">
                      <div>
                        <span>Board columns</span>
                        <small>At least two columns are required.</small>
                      </div>
                      <button type="button" (click)="addStatus()">Add column</button>
                    </div>
                    <div class="wizard-editor-list">
                      @for (status of statuses(); track status.localId; let index = $index) {
                        <div class="wizard-editor-row status-draft">
                          <input aria-label="Column name" maxlength="160" placeholder="Column name" [value]="status.name" (input)="patchStatus(status.localId, 'name', valueOf($event))">
                          <select aria-label="Column category" [value]="status.category" (change)="patchStatus(status.localId, 'category', categoryValue($event))">
                            <option value="TODO">Open</option>
                            <option value="IN_PROGRESS">In progress</option>
                            <option value="DONE">Done</option>
                          </select>
                          <div class="row-actions">
                            <button type="button" class="icon-button" aria-label="Move column up" [disabled]="index === 0" (click)="moveStatus(index, -1)">↑</button>
                            <button type="button" class="icon-button" aria-label="Move column down" [disabled]="index === statuses().length - 1" (click)="moveStatus(index, 1)">↓</button>
                            <button type="button" (click)="removeStatus(status.localId)">Remove</button>
                          </div>
                        </div>
                      } @empty {
                        <div class="wizard-empty">
                          <strong>No columns yet</strong>
                          <span>Add columns in the order they should appear on the board.</span>
                        </div>
                      }
                    </div>
                    @if (statuses().length > 0 && !statusesValid()) {
                      <p class="error">Add at least two columns and give each one a unique name.</p>
                    }
                  </div>
                }
                @case (3) {
                  <div class="wizard-section wizard-review">
                    <section>
                      <h4>Project</h4>
                      <p><span class="project-color-dot" [style.background]="detailsForm.controls.color.value"></span><strong>{{ detailsForm.controls.key.value }} · {{ detailsForm.controls.name.value }}</strong></p>
                      @if (detailsForm.controls.description.value) {
                        <p class="muted">{{ detailsForm.controls.description.value }}</p>
                      }
                    </section>
                    <section>
                      <h4>Ticket types</h4>
                      <div class="review-chips">
                        @for (type of ticketTypes(); track type.localId) {
                          <span class="review-chip"><span class="project-color-dot" [style.background]="type.color"></span>{{ type.name }}</span>
                        }
                      </div>
                    </section>
                    <section>
                      <h4>Board columns</h4>
                      <ol class="review-columns">
                        @for (status of statuses(); track status.localId) {
                          <li><strong>{{ status.name }}</strong><span>{{ categoryLabel(status.category) }}</span></li>
                        }
                      </ol>
                      <p class="muted">Tickets can move between all configured columns.</p>
                    </section>
                  </div>
                }
              }
            </div>

            @if (error()) {
              <p class="error wizard-error" role="alert">{{ error() }}</p>
            }

            <footer class="wizard-footer">
              <button type="button" [disabled]="creating()" (click)="requestClose()">Cancel</button>
              <span></span>
              <button type="button" [disabled]="creating() || step() === 0" (click)="back()">Back</button>
              @if (step() < 3) {
                <button type="button" class="primary" [disabled]="creating() || !canContinue()" (click)="next()">Continue</button>
              } @else {
                <button type="submit" class="primary" [disabled]="creating() || !allValid()">{{ creating() ? 'Creating…' : 'Create project' }}</button>
              }
            </footer>
          </form>
        </section>
      </div>
    }
  `
})
export class ProjectWizardComponent {
  readonly open = input(false);
  readonly creating = input(false);
  readonly error = input('');
  readonly projects = input<Project[]>([]);

  readonly closed = output<void>();
  readonly submitted = output<CreateProjectRequest>();

  protected readonly stepLabels = ['Details', 'Ticket types', 'Columns', 'Review'];
  protected readonly step = signal(0);
  protected readonly ticketTypes = signal<TicketTypeDraft[]>([]);
  protected readonly statuses = signal<StatusDraft[]>([]);
  protected readonly detailsForm = new FormGroup({
    name: new FormControl('', {nonNullable: true, validators: [Validators.required, Validators.maxLength(160)]}),
    key: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9]{1,9}$/)]
    }),
    description: new FormControl('', {nonNullable: true}),
    color: new FormControl('#2563eb', {nonNullable: true, validators: [Validators.required]})
  });

  private nextLocalId = 1;
  private wasOpen = false;

  constructor() {
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !this.wasOpen) this.reset();
      this.wasOpen = isOpen;
    });
  }

  protected requestClose(): void {
    if (!this.creating()) this.closed.emit();
  }

  protected uppercaseKey(): void {
    const control = this.detailsForm.controls.key;
    const upper = control.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (upper !== control.value) control.setValue(upper);
  }

  protected keyExists(): boolean {
    const key = this.detailsForm.controls.key.value.trim().toUpperCase();
    return Boolean(key) && this.projects().some((project) => project.key === key);
  }

  protected addTicketType(): void {
    this.ticketTypes.update((types) => [
      ...types,
      {localId: this.nextLocalId++, name: '', description: '', color: '#2563eb'}
    ]);
  }

  protected patchTicketType(localId: number, field: 'name' | 'description' | 'color', value: string): void {
    this.ticketTypes.update((types) => types.map((type) => type.localId === localId ? {...type, [field]: value} : type));
  }

  protected removeTicketType(localId: number): void {
    this.ticketTypes.update((types) => types.filter((type) => type.localId !== localId));
  }

  protected addStatus(): void {
    this.statuses.update((statuses) => [
      ...statuses,
      {localId: this.nextLocalId++, name: '', category: 'TODO'}
    ]);
  }

  protected patchStatus(localId: number, field: 'name' | 'category', value: string): void {
    this.statuses.update((statuses) => statuses.map((status) =>
      status.localId === localId ? {...status, [field]: value} as StatusDraft : status
    ));
  }

  protected removeStatus(localId: number): void {
    this.statuses.update((statuses) => statuses.filter((status) => status.localId !== localId));
  }

  protected moveStatus(index: number, direction: -1 | 1): void {
    this.statuses.update((statuses) => {
      const target = index + direction;
      if (target < 0 || target >= statuses.length) return statuses;
      const reordered = [...statuses];
      [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
      return reordered;
    });
  }

  protected ticketTypesValid(): boolean {
    const names = this.ticketTypes().map((type) => type.name.trim().toLowerCase());
    return names.length >= 1 && names.every(Boolean) && new Set(names).size === names.length;
  }

  protected statusesValid(): boolean {
    const names = this.statuses().map((status) => status.name.trim().toLowerCase());
    return names.length >= 2 && names.every(Boolean) && new Set(names).size === names.length;
  }

  protected canContinue(): boolean {
    if (this.step() === 0) return this.detailsForm.valid && !this.keyExists();
    if (this.step() === 1) return this.ticketTypesValid();
    if (this.step() === 2) return this.statusesValid();
    return this.allValid();
  }

  protected allValid(): boolean {
    return this.detailsForm.valid && !this.keyExists() && this.ticketTypesValid() && this.statusesValid();
  }

  protected next(): void {
    if (this.step() === 0) this.detailsForm.markAllAsTouched();
    if (this.canContinue()) this.step.update((step) => Math.min(3, step + 1));
  }

  protected back(): void {
    this.step.update((step) => Math.max(0, step - 1));
  }

  protected submit(): void {
    if (this.step() !== 3 || !this.allValid() || this.creating()) return;
    const details = this.detailsForm.getRawValue();
    this.submitted.emit({
      key: details.key.trim().toUpperCase(),
      name: details.name.trim(),
      description: details.description.trim(),
      color: details.color,
      ticketTypes: this.ticketTypes().map((type) => ({
        name: type.name.trim(),
        description: type.description.trim(),
        color: type.color
      })),
      statuses: this.statuses().map((status) => ({
        name: status.name.trim(),
        category: status.category
      }))
    });
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement).value;
  }

  protected categoryValue(event: Event): StatusCategory {
    return this.valueOf(event) as StatusCategory;
  }

  protected categoryLabel(category: StatusCategory): string {
    if (category === 'IN_PROGRESS') return 'In progress';
    if (category === 'DONE') return 'Done';
    return 'Open';
  }

  private reset(): void {
    this.step.set(0);
    this.ticketTypes.set([]);
    this.statuses.set([]);
    this.detailsForm.reset({name: '', key: '', description: '', color: '#2563eb'});
    this.nextLocalId = 1;
  }
}
