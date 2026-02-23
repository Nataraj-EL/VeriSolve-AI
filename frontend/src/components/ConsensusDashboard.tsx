import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { AnswerResponse, ModelScoreDetail } from "@/lib/api";
import { BarChart, Check, Zap, Gauge, ArrowUpRight } from "lucide-react";

interface ConsensusDashboardProps {
    data: AnswerResponse;
}

export function ConsensusDashboard({ data }: ConsensusDashboardProps) {
    if (!data.providerScores) return null;

    const sortedProviders = Object.entries(data.providerScores).sort(([, a], [, b]) => b.weightedScore - a.weightedScore);
    const maxScore = Math.max(...Object.values(data.providerScores).map(s => s.weightedScore));

    return (
        <Card className="border-primary/20 bg-card/60 backdrop-blur-xl rounded-2xl overflow-hidden shadow-2xl animate-in zoom-in-95 duration-700">
            <CardHeader className="pb-4 border-b border-border/10 bg-transparent pt-6 px-6">
                <div className="flex items-center justify-between">
                    <CardTitle className="text-sm font-black uppercase tracking-[0.2em] flex items-center gap-2 text-primary">
                        <Gauge className="h-4 w-4" />
                        Neural Arbitration Matrix
                    </CardTitle>
                    <Badge variant="outline" className="bg-background/50 text-[10px] font-bold uppercase tracking-widest border-primary/30 text-primary">
                        {Object.keys(data.providerScores).length} SENSORS ACTIVE
                    </Badge>
                </div>
            </CardHeader>
            <CardContent className="pt-8 px-6 pb-2">
                <div className="space-y-4">
                    {/* Header Row */}
                    <div className="grid grid-cols-12 text-[10px] font-black text-muted-foreground/40 uppercase tracking-[0.2em] mb-4 px-4">
                        <div className="col-span-4">Processing Node</div>
                        <div className="col-span-2 text-center">Bias Weight</div>
                        <div className="col-span-2 text-center">Confidence</div>
                        <div className="col-span-4 text-right">Inference Impact</div>
                    </div>

                    {/* Rows */}
                    {sortedProviders.map(([provider, score]) => {
                        const isWinner = score.weightedScore === maxScore && score.weightedScore > 0;
                        const isSuppressed = score.weightedScore === 0;
                        const width = maxScore > 0 ? (score.weightedScore / maxScore) * 100 : 0;

                        return (
                            <div
                                key={provider}
                                className={`relative group p-4 rounded-xl border transition-all duration-500 
                                ${isWinner
                                        ? 'bg-background/80 border-primary/30 shadow-lg shadow-primary/5 scale-[1.02] z-10'
                                        : 'border-border/10 hover:border-border/30 hover:bg-muted/5'}
                                ${isSuppressed ? 'opacity-40 grayscale' : 'opacity-100'}`}
                            >
                                <div className="grid grid-cols-12 items-center gap-4 relative z-10">
                                    {/* Model Name */}
                                    <div className="col-span-4 font-bold text-sm flex items-center gap-3">
                                        <div className={`w-2 h-2 rounded-full ${isWinner ? 'bg-success animate-pulse' : (isSuppressed ? 'bg-muted' : 'bg-secondary')}`} />
                                        <span className={isWinner ? 'text-primary' : (isSuppressed ? 'text-muted-foreground' : 'text-foreground/90')}>
                                            {provider.toUpperCase()}
                                        </span>
                                        {isWinner && <Check className="h-4 w-4 text-success" />}
                                    </div>

                                    {/* Weight Badge */}
                                    <div className="col-span-2 text-center">
                                        <code className="text-[10px] px-2 py-0.5 rounded bg-muted/50 border border-border/20 font-mono text-muted-foreground">
                                            {score.weight.toFixed(1)}W
                                        </code>
                                    </div>

                                    {/* Raw Confidence */}
                                    <div className="col-span-2 text-center font-mono text-xs font-bold text-foreground/70">
                                        {(score.baseConfidence * 100).toFixed(0)}%
                                    </div>

                                    {/* Weighted Score Bar */}
                                    <div className="col-span-4">
                                        <div className="flex items-center justify-end gap-3">
                                            <div className="flex-1 h-1.5 bg-muted/30 rounded-full overflow-hidden max-w-[140px] ml-auto">
                                                <div
                                                    className={`h-full rounded-full transition-all duration-1000 ease-out shadow-sm shadow-primary/20
                                                    ${isWinner ? 'bg-primary' : 'bg-secondary/60'}`}
                                                    style={{ width: `${width}%` }}
                                                />
                                            </div>
                                            <span className={`font-black text-sm w-12 text-right font-mono ${isWinner ? 'text-primary' : 'text-muted-foreground/60'}`}>
                                                {score.weightedScore.toFixed(2)}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* Legend & Metadata */}
                <div className="mt-8 pt-6 border-t border-border/10 flex flex-col gap-6">
                    <div className="flex flex-wrap gap-3">
                        {data.arbitrationMode === 'SINGLE_SENSOR' && (
                            <Badge variant="outline" className="bg-warning/10 text-warning border-warning/30 text-[10px] font-bold uppercase tracking-wider animate-pulse">
                                <Zap className="h-3 w-3 mr-1" />
                                Single-Sensor Inference (Reduced Weight)
                            </Badge>
                        )}
                        {data.validationFlags?.includes('REASONING_INCONSISTENT') && (
                            <Badge variant="outline" className="bg-error/10 text-error border-error/30 text-[10px] font-bold uppercase tracking-wider">
                                <BarChart className="h-3 w-3 mr-1" />
                                Logical Inconsistency Detected
                            </Badge>
                        )}
                    </div>

                    <div className="flex items-start gap-4 bg-muted/10 -mx-6 p-6">
                        <div className="bg-primary/10 p-2.5 rounded-xl border border-primary/20">
                            <Zap className="h-4 w-4 text-primary" />
                        </div>
                        <div className="space-y-1.5">
                            <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-primary">
                                Arbitration Logic: {data.arbitrationMode || 'STANDARD'}
                            </h4>
                            <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                                VeriSolve executes cross-node inference validation.
                                {data.arbitrationMode === 'SINGLE_SENSOR'
                                    ? " Only one node provided a valid response; arbitration confidence is capped at 0.85 per safety protocol."
                                    : " Final scores are derived from Statistical Bias Weights multiplied by Vector-based Confidence."}
                                {data.validationFlags?.includes('REASONING_INCONSISTENT') && " WARNING: Programmatic math verification detected inconsistencies in the reasoning steps."}
                            </p>
                        </div>
                    </div>
                </div>
            </CardContent>
        </Card>
    )
}

