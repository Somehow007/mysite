import { get, put, del } from './client'

export interface AdminUser {
  id: string
  username: string
  realName: string
  email: string
  phoneNumber: string
  sex: number
  role: string
  status: number
  followingCount: number
  followerCount: number
  createTime: string
  updateTime: string
}

export interface UserOperationLog {
  id: string
  operatorId: string
  operatorName: string
  targetUserId: string
  targetUserName: string
  operationType: string
  detail: string
  createTime: string
}

export interface AdminUserPage {
  records: AdminUser[]
  total: number
  size: number
  current: number
  pages: number
}

export interface OperationLogPage {
  records: UserOperationLog[]
  total: number
  size: number
  current: number
  pages: number
}

export function getAdminUsers(params: { current: number; size: number; keyword?: string }): Promise<AdminUserPage> {
  return get('/v1/admin/users', params)
}

export function getAdminUserDetail(id: string): Promise<AdminUser> {
  return get(`/v1/admin/users/${id}`)
}

export function updateUserRole(id: string, role: string): Promise<void> {
  return put(`/v1/admin/users/${id}/role`, { role })
}

export function updateUserStatus(id: string, status: number): Promise<void> {
  return put(`/v1/admin/users/${id}/status`, { status })
}

export function deleteUser(id: string): Promise<void> {
  return del(`/v1/admin/users/${id}`)
}

export function getOperationLogs(params: { current: number; size: number; targetUserId?: string }): Promise<OperationLogPage> {
  return get('/v1/admin/users/operation-logs', params)
}
