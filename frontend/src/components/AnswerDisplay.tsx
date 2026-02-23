
import { AnswerResponse } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { CheckCircle2, AlertTriangle, Info, Copy, Check } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useState } from 'react';

import { ConsensusDashboard } from './ConsensusDashboard';

interface AnswerDisplayProps {
    data: AnswerResponse;
}

export function AnswerDisplay({ data }: AnswerDisplayProps) {
    const [copied, setCopied] = useState(false);

    const getBadgeColor = (level: string) => {
        if (!level) return 'bg-muted text-muted-foreground border-border';
        if (level.includes('High')) return 'bg-success/10 text-success border-success/30';
        if (level.includes('Medium')) return 'bg-warning/10 text-warning border-warning/30';
        return 'bg-error/10 text-error border-error/30';
    };

    const copyToClipboard = () => {
        navigator.clipboard.writeText(data.verifiedAnswer);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="space-y-8 w-full max-w-2xl mx-auto animate-in fade-in slide-in-from-bottom-8 duration-700 pb-12">

            {/* Verified Answer - Hero Section */}
            <div className="relative group">
                <div className="absolute -inset-1 bg-gradient-to-r from-primary/30 to-secondary/30 rounded-2xl blur opacity-20 group-hover:opacity-30 transition duration-1000 group-hover:duration-200"></div>
                <div className="relative border border-primary/30 bg-card/50 backdrop-blur-xl rounded-2xl p-6 shadow-2xl overflow-hidden block w-full">
                    <div className="absolute top-0 right-0 p-3 opacity-10 pointer-events-none">
                        <CheckCircle2 className="h-24 w-24 text-primary" />
                    </div>

                    <div className="block w-full">
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6 relative z-10 w-full">
                            <div className="flex items-center gap-3">
                                <div className="p-2 bg-primary/10 rounded-lg">
                                    <CheckCircle2 className="h-5 w-5 text-primary" />
                                </div>
                                <h3 className="text-sm font-black uppercase tracking-[0.2em] text-primary/80 m-0">Arbitration Result</h3>
                            </div>

                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={copyToClipboard}
                                className="text-xs font-bold gap-2 text-muted-foreground hover:text-foreground hover:bg-muted/30 rounded-lg transition-all"
                            >
                                {copied ? (
                                    <><Check className="h-3.5 w-3.5 text-success" /> Copied!</>
                                ) : (
                                    <><Copy className="h-3.5 w-3.5" /> {data.subject === 'CODING' ? 'Copy Code' : 'Copy Answer'}</>
                                )}
                            </Button>
                        </div>

                        <div className={`w-full max-w-full overflow-hidden relative z-10 ${data.subject === 'CODING'
                            ? 'p-6 bg-[#0d1117] text-[#e6edf3] rounded-xl font-mono text-[13px] leading-relaxed border border-border/20 shadow-inner'
                            : 'text-2xl md:text-4xl font-black'}`}>
                            {data.subject === 'CODING' ? (
                                <div className="w-full overflow-x-auto">
                                    <pre className="min-w-full">
                                        <code>{data.verifiedAnswer}</code>
                                    </pre>
                                </div>
                            ) : (
                                <span className="text-foreground leading-tight block break-words">{data.verifiedAnswer}</span>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {/* Confidence Metrics */}
            <div className="flex flex-wrap gap-4 items-center justify-between px-2">
                <div className="flex items-center gap-3">
                    <Badge variant="outline" className={`px-4 py-1.5 text-xs font-bold rounded-full tracking-wide border-2 ${getBadgeColor(data.modelAgreement)}`}>
                        {data.modelAgreement.toUpperCase()} CONFIDENCE
                    </Badge>
                    <div className="h-1.5 w-32 bg-muted rounded-full overflow-hidden">
                        <div
                            className="h-full bg-success transition-all duration-1000"
                            style={{ width: `${data.confidence * 100}%` }}
                        />
                    </div>
                    <span className="text-xs font-mono font-bold text-success">
                        {(data.confidence * 100).toFixed(1)}%
                    </span>
                </div>
                <span className="text-[10px] uppercase font-bold tracking-widest text-muted-foreground/60 flex items-center gap-2">
                    <Info className="h-3 w-3" /> Consensus of {data.providerScores ? Object.keys(data.providerScores).length : 0} nodes
                </span>
            </div>

            {/* Consensus Dashboard */}
            <ConsensusDashboard data={data} />

            {/* Logic & Explanation */}
            <Card className="border-border/40 bg-card/30 backdrop-blur-md rounded-2xl overflow-hidden shadow-xl">
                <CardHeader className="border-b border-border/20 bg-muted/5">
                    <div className="flex items-center gap-2 text-primary">
                        <Info className="h-4 w-4" />
                        <CardTitle className="text-sm font-bold uppercase tracking-widest">Inference Reasoning</CardTitle>
                    </div>
                </CardHeader>
                <CardContent className="space-y-6 pt-6">
                    <p className="text-md text-foreground/90 leading-relaxed font-medium bg-muted/20 p-4 rounded-xl border border-border/10">
                        {data.explanation}
                    </p>

                    {data.steps && data.steps.length > 0 && (
                        <div className="space-y-4">
                            <h4 className="text-xs font-bold uppercase tracking-widest text-muted-foreground/50 ml-1">Step-by-Step Breakdown</h4>
                            <div className="space-y-3">
                                {data.steps.map((step, index) => (
                                    <div key={index} className="flex gap-4 p-4 rounded-xl bg-muted/5 border border-border/5 hover:border-primary/20 transition-colors group">
                                        <span className="flex-shrink-0 flex items-center justify-center w-6 h-6 rounded-full bg-primary/10 text-primary text-[10px] font-black border border-primary/20 group-hover:bg-primary group-hover:text-primary-foreground transition-all">
                                            {index + 1}
                                        </span>
                                        <p className="text-sm text-foreground/80 leading-snug">{step}</p>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
