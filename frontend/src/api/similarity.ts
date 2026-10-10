import api from './axios'

export const similarityApi = {
  // 批量生成任务的向量嵌入
  batchGenerateEmbeddings: (projectId: number, batchSize: number = 10) => {
    return api.post('/similarity/batch-generate', null, {
      params: {
        projectId,
        batchSize,
      },
    })
  },

  // 为单个任务生成向量嵌入
  generateTaskEmbedding: (taskId: number) => {
    return api.post(`/similarity/tasks/${taskId}/embedding`)
  },

  // 搜索相似任务
  searchSimilarTasks: (request: {
    taskId?: number
    title?: string
    description?: string
    projectId?: number
    topK?: number
  }) => {
    return api.post('/similarity/search', request)
  },
}
