
"use client";

import { useState } from 'react';
import { UploadArea } from '@/components/UploadArea';
import { AnswerDisplay } from '@/components/AnswerDisplay';
import { AnswerResponse } from '@/lib/api';
import { BrainCircuit } from 'lucide-react';

export default function Home() {
    const [result, setResult] = useState<AnswerResponse | null>(null);
    const [error, setError] = useState<string | null>(null);

    const handleSuccess = (data: AnswerResponse) => {
        setResult(data);
        setError(null);
    };

    const handleError = (msg: string) => {
        setError(msg);
        setResult(null);
    };

    return (
        <main className="min-h-screen bg-background p-6 md:p-12 selection:bg-primary/30">
            <div className="max-w-4xl mx-auto space-y-12">

                {/* Header */}
                <div className="text-center space-y-6 pt-8">
                    <div className="inline-flex items-center justify-center p-4 bg-primary/5 rounded-3xl border border-primary/10 mb-2 relative group transition-all hover:border-primary/30">
                        <div className="absolute inset-0 bg-primary/5 blur-xl group-hover:bg-primary/10 transition-all rounded-full" />
                        <BrainCircuit className="h-12 w-12 text-primary relative z-10" />
                    </div>
                    <h1 className="text-5xl md:text-6xl font-black tracking-tighter bg-gradient-to-br from-primary via-secondary to-primary/80 bg-clip-text text-transparent animate-in fade-in slide-in-from-bottom-4 duration-1000">
                        VeriSolve AI
                    </h1>
                    <p className="text-lg md:text-xl text-muted-foreground max-w-2xl mx-auto leading-relaxed">
                        Intelligent inference arbitration. <span className="text-foreground/80 font-medium">Consensus-driven accuracy</span> for tactical aptitude and architectural coding queries.
                    </p>
                </div>

                {/* Main Interface */}
                <div className="flex flex-col items-center gap-10 pb-20">
                    <UploadArea onSuccess={handleSuccess} onError={handleError} />

                    {error && (
                        <div className="p-4 rounded-2xl bg-destructive/10 border border-destructive/20 text-destructive text-center w-full max-w-2xl mx-auto animate-in shake-in">
                            {error}
                        </div>
                    )}

                    {result && <AnswerDisplay data={result} />}
                </div>

                {/* Footer */}
                <footer className="text-center text-xs text-muted-foreground/50 border-t border-border/30 pt-8 pb-12">
                    <p className="tracking-widest uppercase">© 2026 NATARAJ EL. ALL RIGHTS RESERVED.</p>
                </footer>
            </div>
        </main>
    );
}
