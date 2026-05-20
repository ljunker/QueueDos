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
  fromStatusId: string;
  toStatusId: string;
  allowedRoles: Role[];
  requiredFields: string[];
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
  reporterId: string;
  createdAt: string;
  updatedAt: string;
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
  priorities: Priority[];
}

export interface CreateTicketRequest {
  projectId: string;
  title: string;
  description: string;
  typeId: string;
  priority: Priority;
  assigneeId: string | null;
  statusId: string | null;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  typeId?: string;
  priority?: Priority;
  assigneeId?: string | null;
}

export interface TransitionTicketRequest {
  toStatusId: string;
}
