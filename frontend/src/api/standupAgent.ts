import axios from './axios';

export interface StandupQueryRequest {
  question: string;
  projectId: number;
  sprintId?: number;
  timezone?: string;
}

export interface StandupSummary {
  inProgressCount?: number;
  burndownDeviationHours?: number;
  riskLevel?: string;
  additionalInfo?: Record<string, any>;
}

export interface StandupQueryResponse {
  answer: string;
  summary: StandupSummary;
  toolsUsed: string[];
  evidence: string[];
}

export interface ApiResponse<T> {
  code: string;
  message: string;
  traceId: string;
  data: T;
}

export const standupAgentApi = {
  query: (request: StandupQueryRequest) =>
    axios.post<ApiResponse<StandupQueryResponse>>('/agent/standup/query', request),
};
