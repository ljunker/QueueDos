export type Role = 'ADMIN' | 'MEMBER';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Organization {
  id: string;
  name: string;
}

export interface PublicUser {
  id: string;
  organizationId: string;
  email: string;
  displayName: string;
  role: Role;
  active: boolean;
}

export interface Project {
  id: string;
  organizationId: string;
  key: string;
  name: string;
  description: string;
  nextTicketNumber: number;
  archived: boolean;
}

export interface TicketType {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  description: string;
  color: string;
}

export interface WorkflowStatus {
  id: string;
  name: string;
  category: 'TODO' | 'IN_PROGRESS' | 'DONE' | string;
  sortOrder: number;
}

export interface WorkflowTransition {
  id: string;
  fromStatusId: string | null;
  toStatusId: string;
  allowedRoles: Role[];
  requiredFields: string[];
  globalTransition: boolean;
  allowBackward: boolean;
}

export interface Workflow {
  id: string;
  organizationId: string;
  projectId: string;
  statuses: WorkflowStatus[];
  transitions: WorkflowTransition[];
}

export interface Ticket {
  id: string;
  organizationId: string;
  projectId: string;
  number: number;
  key: string;
  title: string;
  description: string;
  statusId: string;
  typeId: string;
  priority: Priority;
  assigneeId: string | null;
  labels: string[];
  dueDate: string | null;
  estimate: number | null;
  reporterId: string;
  createdAt: string;
  updatedAt: string;
}

export interface TicketComment {
  id: string;
  organizationId: string;
  ticketId: string;
  authorId: string;
  body: string;
  createdAt: string;
}

export interface TicketChange {
  id: string;
  organizationId: string;
  ticketId: string;
  actorId: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  createdAt: string;
}

export type SavedTicketFilterView = 'PROJECT_LIST' | 'MY_TICKETS';

export interface SavedTicketFilterCriteria {
  projectId: string | null;
  q: string;
  statusId: string;
  typeId: string;
  priority: Priority | null;
  assigneeId: string;
  label: string;
  sort: string;
}

export interface SavedTicketFilter {
  id: string;
  organizationId: string;
  ownerId: string;
  name: string;
  view: SavedTicketFilterView;
  projectId: string | null;
  filters: SavedTicketFilterCriteria;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: PublicUser;
}

export interface BootstrapResponse {
  currentUser: PublicUser;
  organizations: Organization[];
  users: PublicUser[];
  projects: Project[];
  ticketTypes: TicketType[];
  workflows: Workflow[];
  tickets: Ticket[];
  comments: TicketComment[];
  ticketChanges: TicketChange[];
  savedTicketFilters: SavedTicketFilter[];
  priorities: Priority[];
}

export interface TicketDetailResponse {
  ticket: Ticket;
  comments: TicketComment[];
  changes: TicketChange[];
}

export interface CreateTicketCommentRequest {
  body: string;
}

export interface CreateProjectRequest {
  key: string;
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  key?: string;
  name?: string;
  description?: string;
  archived?: boolean;
}

export interface CreateUserRequest {
  email: string;
  displayName: string;
  role: Role;
  password: string;
}

export interface UpdateUserRequest {
  displayName?: string;
  role?: Role;
  active?: boolean;
  password?: string;
}

export interface CreateTicketTypeRequest {
  projectId: string;
  name: string;
  description?: string;
  color?: string;
}

export interface UpdateTicketTypeRequest {
  name?: string;
  description?: string;
  color?: string;
}

export interface CreateTicketRequest {
  projectId: string;
  title: string;
  description: string;
  typeId: string;
  priority: Priority;
  assigneeId: string | null;
  statusId: string | null;
  labels: string[];
  dueDate: string | null;
  estimate: number | null;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  typeId?: string;
  priority?: Priority;
  assigneeId?: string | null;
  labels?: string[];
  dueDate?: string | null;
  estimate?: number | null;
  clearDueDate?: boolean;
  clearEstimate?: boolean;
}

export interface TransitionTicketRequest {
  toStatusId: string;
}

export interface BulkUpdateTicketsRequest {
  ticketIds: string[];
  assigneeId?: string | null;
  clearAssignee?: boolean;
  priority?: Priority | null;
}

export interface SaveWorkflowRequest {
  statuses: WorkflowStatus[];
  transitions: WorkflowTransition[];
}

export interface CreateSavedTicketFilterRequest {
  name: string;
  view: SavedTicketFilterView;
  projectId: string | null;
  filters: SavedTicketFilterCriteria;
}

export interface UpdateSavedTicketFilterRequest {
  name?: string;
  filters?: SavedTicketFilterCriteria;
}
