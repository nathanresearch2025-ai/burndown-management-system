import api from './axios'

export interface Task {
  id: number
  projectId: number
  sprintId: number
  taskKey: string
  title: string
  description: string
  type: string
  status: string
  priority: string
  storyPoints: number
  originalEstimate: number
  remainingEstimate: number
  timeSpent: number
  assigneeId: number
  reporterId: number
  createdAt: string
}

export interface CreateTaskRequest {
  projectId: number
  sprintId?: number
  title: string
  description?: string
  type: string
  priority?: string
  storyPoints?: number
  originalEstimate?: number
  assigneeId?: number
}

export const taskApi = {
  getBySprint: (sprintId: number) => api.get<Task[]>(`/tasks/sprint/${sprintId}`),
  getByProject: (projectId: number) => api.get<Task[]>(`/tasks/project/${projectId}`),
  getById: (id: number) => api.get<Task>(`/tasks/${id}`),
  create: (data: CreateTaskRequest) => api.post<Task>('/tasks', data),
  update: (id: number, data: CreateTaskRequest) => api.put<Task>(`/tasks/${id}`, data),
  updateStatus: (id: number, status: string) => api.patch<Task>(`/tasks/${id}/status?status=${status}`),
}

export const createTask = async (sprintId: number, data: Omit<CreateTaskRequest, 'sprintId'>) => {
  const response = await taskApi.create({ ...data, sprintId })
  return response.data
}
