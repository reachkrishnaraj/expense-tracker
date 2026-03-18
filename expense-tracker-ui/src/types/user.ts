import type { Role } from './auth';

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  managerId: string | null;
  managerName: string | null;
  organizationId: string;
  active: boolean;
  createdAt: string;
}

export interface Category {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
}
