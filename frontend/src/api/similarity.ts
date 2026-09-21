import axios from './axios';

export const similarityApi = {
  // 批量生成任务向量嵌入
  batchGenerateEmbeddings: (projectId: number, batchSize: number = 100) => {
    return axios.post(`/tasks/similarity/batch-generate`, null, {
      params: { projectId, batchSize }
    });
  },

  // 查找相似任务
  findSimilarTasks: (taskId: number, topK: number = 5) => {
    return axios.get(`/tasks/similarity/${taskId}`, {
      params: { topK }
    });
  },

  // 重新生成单个任务的向量
  regenerateEmbedding: (taskId: number) => {
    return axios.post(`/tasks/similarity/${taskId}/regenerate`);
  }
};
