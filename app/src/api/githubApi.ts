import axios from "axios";
import type { GitHubAnalysis, ContributionCalendar, ApiError } from "@/types/github";

const API_BASE_URL = import.meta.env.VITE_API_URL || "";

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 30000,
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError<Partial<ApiError>>(error)) {
      const message = error.response?.data?.message;
      if (typeof message === "string") error.message = message;
    }
    return Promise.reject(error);
  },
);

export async function fetchGitHubAnalysis(username: string): Promise<GitHubAnalysis> {
  const response = await apiClient.get<GitHubAnalysis>(`/api/github/${username}`);
  return response.data;
}

export async function fetchContributionCalendar(username: string): Promise<ContributionCalendar> {
  const response = await apiClient.get<ContributionCalendar>(`/api/github/${username}/contributions`);
  return response.data;
}

export interface AiInsight { title: string; detail: string; repositories: string[] }
export interface AiSummary {
  summary: string | null;
  degraded: boolean;
  message?: string;
  provider?: string;
  generatedAt?: string;
  sampleSize?: number;
  developerType?: { label: string; confidence: "low" | "medium" | "high"; explanation: string; repositories: string[] };
  projectTypes?: AiInsight[];
  strengths?: AiInsight[];
  notableProjects?: { repository: string; reason: string }[];
  nextSteps?: AiInsight[];
  limitations?: string[];
}
export async function fetchAiSummary(username: string): Promise<AiSummary> {
  return (await apiClient.get<AiSummary>(`/api/ai/summary/${encodeURIComponent(username)}`)).data;
}
