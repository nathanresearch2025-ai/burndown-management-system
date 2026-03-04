import api from './axios'

export interface BurndownPoint {
  id: number
  sprintId: number
  pointDate: string
  idealRemaining: number
  actualRemaining: number
  completedPoints: number
  totalTasks: number
  completedTasks: number
  inProgressTasks: number
}

export const burndownApi = {
  getData: (sprintId: number) => api.get<BurndownPoint[]>(`/burndown/sprints/${sprintId}`),
  calculate: (sprintId: number) => api.post(`/burndown/sprints/${sprintId}/calculate`),
}
