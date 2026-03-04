import api from './axios'

export interface Sprint {
  id: number
  projectId: number
  name: string
  goal: string
  startDate: string
  endDate: string
  status: string
  totalCapacity: number
  createdAt: string
}

export interface CreateSprintRequest {
  projectId: number
  name: string
  goal?: string
  startDate: string
  endDate: string
  totalCapacity?: number
}

export const sprintApi = {
  getByProject: (projectId: number) => api.get<Sprint[]>(`/sprints/project/${projectId}`),
  getById: (id: number) => api.get<Sprint>(`/sprints/${id}`),
  create: (data: CreateSprintRequest) => api.post<Sprint>('/sprints', data),
  start: (id: number) => api.post<Sprint>(`/sprints/${id}/start`),
  complete: (id: number) => api.post<Sprint>(`/sprints/${id}/complete`),
}

export const getSprints = async (projectId: number) => {
  const response = await sprintApi.getByProject(projectId)
  return response.data
}

export const createSprint = async (projectId: number, data: Omit<CreateSprintRequest, 'projectId'>) => {
  const response = await sprintApi.create({ ...data, projectId })
  return response.data
}
