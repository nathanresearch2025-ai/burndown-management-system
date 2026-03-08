import { create } from 'zustand'

interface SimilarTask {
  id: number
  taskKey: string
  title: string
  similarity: number
}

interface AiGenerationState {
  isGenerating: boolean
  generatedDescription: string | null
  similarTasks: SimilarTask[]
  error: string | null
  setGenerating: (isGenerating: boolean) => void
  setGeneratedDescription: (description: string, similarTasks: SimilarTask[]) => void
  setError: (error: string | null) => void
  clearGeneration: () => void
}

export const useAiGenerationStore = create<AiGenerationState>((set) => ({
  isGenerating: false,
  generatedDescription: null,
  similarTasks: [],
  error: null,
  setGenerating: (isGenerating) => set({ isGenerating, error: null }),
  setGeneratedDescription: (description, similarTasks) =>
    set({
      generatedDescription: description,
      similarTasks,
      isGenerating: false,
      error: null,
    }),
  setError: (error) =>
    set({
      error,
      isGenerating: false,
    }),
  clearGeneration: () =>
    set({
      generatedDescription: null,
      similarTasks: [],
      error: null,
      isGenerating: false,
    }),
}))
