import api from './axios'

export interface WorkLog {
  id: number
  taskId: number
  userId: number
  workDate: string
  timeSpent: number
  remainingEstimate: number
  comment: string
  createdAt: string
}

export interface LogWorkRequest {
  taskId: number
  workDate: string
  timeSpent: number
  remainingEstimate: number
  comment?: string
}

export const workLogApi = {
  getByTask: (taskId: number) => api.get<WorkLog[]>(`/worklogs/task/${taskId}`),
  getMyWorkLogs: () => api.get<WorkLog[]>('/worklogs/my-worklogs'),
  create: (data: LogWorkRequest) => api.post<WorkLog>('/worklogs', data),
}
