import { apiClient } from './client';
import type {
  ChangePasswordInput,
  CreateUserInput,
  ManagedUser,
  UpdateUserInput,
  User,
} from './types';

export async function listUsers(): Promise<ManagedUser[]> {
  const { data } = await apiClient.get<ManagedUser[]>('/users');
  return data;
}

export async function createUser(input: CreateUserInput): Promise<User> {
  const { data } = await apiClient.post<User>('/users', input);
  return data;
}

export async function updateUser(id: string, input: UpdateUserInput): Promise<ManagedUser> {
  const { data } = await apiClient.patch<ManagedUser>(`/users/${id}`, input);
  return data;
}

export async function resetUserPassword(id: string, newPassword: string): Promise<void> {
  await apiClient.post(`/users/${id}/password`, { newPassword });
}

export async function changePassword(input: ChangePasswordInput): Promise<void> {
  await apiClient.post('/auth/password', input);
}
