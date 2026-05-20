import { FormControl, FormGroup } from '@angular/forms';

import { Priority } from '../../core/api.models';

export interface TicketFormControls {
  title: FormControl<string>;
  description: FormControl<string>;
  labels: FormControl<string>;
  dueDate: FormControl<string>;
  typeId: FormControl<string>;
  priority: FormControl<Priority>;
  assigneeId: FormControl<string>;
  statusId: FormControl<string>;
  estimate: FormControl<string>;
}

export type TicketFormGroup = FormGroup<TicketFormControls>;
