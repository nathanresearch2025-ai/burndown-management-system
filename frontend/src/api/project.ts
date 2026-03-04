import api from './axios'

export interface Project {
  id: number
  name: string
  description: string
  projectKey: string
  type: string
  ownerId: number
  createdAt: string
}

export interface CreateProjectRequest {
  name: string
  description?: string
  projectKey: string
  type: string
  visibility?: string
  startDate?: string
  endDate?: string
}

export const projectApi = {
  getAll: () => api.get<Project[]>('/projects'),
  getById: (id: number) => api.get<Project>(`/projects/${id}`),
  getMyProjects: () => api.get<Project[]>('/projects/my-projects'),
  create: (data: CreateProjectRequest) => api.post<Project>('/projects', data),
}

export const getProjects = async () => {
  const response = await projectApi.getAll()
  return response.data
}

export const createProject = async (data: CreateProjectRequest) => {
  const response = await projectApi.create(data)
  return response.data
}
