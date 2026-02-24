import axios from 'axios';

// Strict fallback for production builds if env var is missing
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL
    ? `${process.env.NEXT_PUBLIC_API_URL}/api/v1`
    : process.env.NODE_ENV === 'production'
        ? 'https://verisolve-ai.onrender.com/api/v1'
        : 'http://localhost:8080/api/v1';

export const api = axios.create({
    baseURL: API_BASE_URL,
});

export interface QuestionRequest {
    text?: string;
    image?: File;
    subject?: 'APTITUDE' | 'CODING';
}

export interface ModelScoreDetail {
    baseConfidence: number;
    weight: number;
    weightedScore: number;
}

export interface AnswerResponse {
    verifiedAnswer: string;
    explanation: string;
    confidence: number;
    steps: string[];
    modelAgreement: string; // High, Medium, Low
    providerScores: Record<string, ModelScoreDetail>;
    arbitrationMode: 'CONSENSUS' | 'SINGLE_SENSOR' | 'ERROR';
    validationFlags: string[];
    subject: 'APTITUDE' | 'CODING';
}

export const solveQuestion = async (data: QuestionRequest) => {
    const formData = new FormData();
    if (data.text) formData.append('text', data.text);
    if (data.image) formData.append('image', data.image);
    if (data.subject) formData.append('subject', data.subject);

    const response = await api.post<AnswerResponse>('/questions/solve', formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        },
    });
    return response.data;
};
