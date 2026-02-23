
"use client";

import { useState, useRef, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Upload, X, Loader2, Camera, RefreshCw, Sparkles, Code2, Calculator } from 'lucide-react';
import { api, QuestionRequest, AnswerResponse } from '@/lib/api';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface UploadAreaProps {
    onSuccess: (data: AnswerResponse) => void;
    onError: (error: string) => void;
}

export function UploadArea({ onSuccess, onError }: UploadAreaProps) {
    const [image, setImage] = useState<File | null>(null);
    const [text, setText] = useState('');
    const [subject, setSubject] = useState<'APTITUDE' | 'CODING'>('APTITUDE');
    const [isLoading, setIsLoading] = useState(false);

    // Camera State
    const [isCameraOpen, setIsCameraOpen] = useState(false);
    const [cameraError, setCameraError] = useState<string | null>(null);
    const videoRef = useRef<HTMLVideoElement>(null);
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const streamRef = useRef<MediaStream | null>(null);

    const onDrop = (acceptedFiles: File[]) => {
        if (acceptedFiles.length > 0) {
            setImage(acceptedFiles[0]);
            setCameraError(null);
        }
    };

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop,
        accept: { 'image/*': [] },
        maxFiles: 1,
        disabled: isCameraOpen, // Disable dropzone when camera is active
    });

    // Start Camera
    const startCamera = async () => {
        try {
            setCameraError(null);
            const stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: 'environment' } // Prefer back camera on mobile
            });

            streamRef.current = stream;
            if (videoRef.current) {
                videoRef.current.srcObject = stream;
            }
            setIsCameraOpen(true);
            setImage(null); // Clear existing image if any
        } catch (err) {
            console.error("Error accessing camera:", err);
            setCameraError("Unable to access camera. Please ensure permissions are granted.");
        }
    };

    // Stop Camera
    const stopCamera = () => {
        if (streamRef.current) {
            streamRef.current.getTracks().forEach(track => track.stop());
            streamRef.current = null;
        }
        setIsCameraOpen(false);
    };

    // Capture Image
    const captureImage = () => {
        if (videoRef.current && canvasRef.current) {
            const video = videoRef.current;
            const canvas = canvasRef.current;

            // Set canvas dimensions to match video
            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;

            // Draw video frame to canvas
            const context = canvas.getContext('2d');
            if (context) {
                context.drawImage(video, 0, 0, canvas.width, canvas.height);

                // Convert to blob/file
                canvas.toBlob((blob) => {
                    if (blob) {
                        const file = new File([blob], `capture-${Date.now()}.jpg`, { type: 'image/jpeg' });
                        setImage(file);
                        stopCamera();
                    }
                }, 'image/jpeg', 0.8);
            }
        }
    };

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            stopCamera();
        };
    }, []);

    const handleSubmit = async () => {
        if (!text && !image) {
            onError("Please provide either text or an image.");
            return;
        }

        setIsLoading(true);
        try {
            const formData = new FormData();
            formData.append('text', text || ''); // Backend expects 'text', ensure it's not undefined
            formData.append('subject', subject);

            if (image) {
                formData.append('image', image);
            }

            // Note: When sending FormData, Axios/Browser automatically sets Content-Type to multipart/form-data with boundary
            // We should NOT manually set Content-Type header here, or it will lack the boundary.
            const response = await api.post<AnswerResponse>('/questions/solve', formData);

            onSuccess(response.data);
        } catch (err: any) {
            console.error(err);
            onError(err.response?.data?.error || "Failed to process request");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Card className="w-full max-w-2xl mx-auto shadow-2xl border-border/40 bg-card/30 backdrop-blur-md rounded-2xl overflow-hidden animate-in fade-in zoom-in duration-500">
            <CardHeader className="text-center pb-2">
                <CardTitle className="text-2xl font-bold tracking-tight text-foreground">Ask VeriSolve AI</CardTitle>
                <CardDescription className="text-muted-foreground/80">Upload a screenshot, use your camera, or type your question.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6 p-6">

                {/* Camera View */}
                {isCameraOpen && (
                    <div className="relative border-2 border-primary/20 rounded-2xl overflow-hidden bg-black aspect-video flex items-center justify-center group shadow-inner">
                        <video
                            ref={videoRef}
                            autoPlay
                            playsInline
                            muted
                            className="w-full h-full object-cover opacity-90 transition-opacity group-hover:opacity-100"
                        />
                        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent pointer-events-none" />
                        <div className="absolute bottom-6 flex gap-4 z-10">
                            <Button variant="outline" onClick={stopCamera} className="bg-black/40 backdrop-blur-md border-white/10 hover:bg-black/60 rounded-xl px-6">
                                Cancel
                            </Button>
                            <Button onClick={captureImage} className="bg-primary hover:primary/90 text-primary-foreground rounded-xl px-8 shadow-lg shadow-primary/20 border-none transition-all hover:scale-105 active:scale-95">
                                <Camera className="mr-2 h-5 w-5" /> Capture
                            </Button>
                        </div>
                    </div>
                )}

                {/* Dropzone & Camera Trigger */}
                {!isCameraOpen && !image && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                        <div
                            {...getRootProps()}
                            className={`group relative border-2 border-dashed rounded-2xl p-8 text-center cursor-pointer transition-all flex flex-col items-center justify-center min-h-[220px] bg-muted/5
                            ${isDragActive ? 'border-primary bg-primary/5 scale-[1.02]' : 'border-border/50 hover:border-primary/40 hover:bg-primary/5'}`}
                        >
                            <input {...getInputProps()} />
                            <div className="p-4 bg-primary/5 rounded-2xl mb-4 group-hover:bg-primary/10 transition-colors">
                                <Upload className="h-10 w-10 text-primary/70 group-hover:text-primary transition-colors" />
                            </div>
                            <p className="text-sm font-medium text-foreground/80 group-hover:text-foreground">Drag & drop image</p>
                            <p className="text-xs text-muted-foreground mt-1">or click to browse local files</p>
                        </div>

                        <div
                            onClick={startCamera}
                            className="group border-2 border-dashed border-border/50 hover:border-secondary/40 hover:bg-secondary/5 rounded-2xl p-8 text-center cursor-pointer transition-all flex flex-col items-center justify-center min-h-[220px] bg-muted/5"
                        >
                            <div className="p-4 bg-secondary/5 rounded-2xl mb-4 group-hover:bg-secondary/10 transition-colors">
                                <Camera className="h-10 w-10 text-secondary/70 group-hover:text-secondary transition-colors" />
                            </div>
                            <p className="text-sm font-medium text-foreground/80 group-hover:text-foreground">Direct Access</p>
                            <p className="text-xs text-muted-foreground mt-1">Open camera for instant capture</p>
                        </div>
                    </div>
                )}

                {/* Selected Image Preview */}
                {image && (
                    <div className="relative border border-border/60 rounded-2xl p-4 flex items-center gap-5 bg-primary/5 animate-in slide-in-from-top-2">
                        <div className="h-20 w-20 bg-background rounded-xl border border-border/40 shadow-sm flex items-center justify-center overflow-hidden">
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src={URL.createObjectURL(image)} alt="Preview" className="object-cover w-full h-full" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold truncate text-foreground">{image.name}</p>
                            <p className="text-xs text-muted-foreground/70 tracking-wider">{(image.size / 1024).toFixed(1)} KB • READY</p>
                        </div>
                        <Button variant="ghost" size="icon" onClick={() => setImage(null)} className="rounded-full hover:bg-destructive/10 hover:text-destructive">
                            <X className="h-5 w-5" />
                        </Button>
                    </div>
                )}

                {/* Hidden Canvas for Capture */}
                <canvas ref={canvasRef} className="hidden" />

                {/* Subject Selection */}
                <div className="flex flex-col gap-3">
                    <label className="text-[10px] tracking-[0.2em] uppercase font-bold text-muted-foreground/60 ml-1">
                        Domain focus
                    </label>
                    <Tabs value={subject} onValueChange={(v) => { setSubject(v as any); setText(''); }} className="w-full">
                        <TabsList className="grid w-full grid-cols-2 bg-muted/10 h-12 p-1 border border-border/20 rounded-xl">
                            <TabsTrigger value="APTITUDE" className="rounded-lg data-[state=active]:bg-primary/20 data-[state=active]:text-primary transition-all">
                                <Calculator className="w-4 h-4 mr-2" /> Aptitude
                            </TabsTrigger>
                            <TabsTrigger value="CODING" className="rounded-lg data-[state=active]:bg-secondary/20 data-[state=active]:text-secondary transition-all">
                                <Code2 className="w-4 h-4 mr-2" /> Coding
                            </TabsTrigger>
                        </TabsList>
                    </Tabs>
                </div>

                {/* Text Input */}
                <div className="space-y-2">
                    <label className="text-[10px] tracking-[0.2em] uppercase font-bold text-muted-foreground/60 ml-1">
                        Problem details
                    </label>
                </div>

                <Textarea
                    placeholder="Paste context, question text, or code snippet here..."
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            handleSubmit();
                        }
                    }}
                    className="min-h-[120px] bg-muted/5 border-border/50 focus:border-primary/50 focus:ring-primary/20 rounded-2xl transition-all resize-none shadow-inner"
                />

                <Button
                    onClick={handleSubmit}
                    disabled={isLoading}
                    className="w-full h-14 text-md font-bold rounded-2xl bg-gradient-to-r from-primary to-secondary hover:shadow-lg hover:shadow-primary/25 transition-all active:scale-[0.98] border-none group"
                >
                    {isLoading ? (
                        <>
                            <Loader2 className="mr-3 h-5 w-5 animate-spin" />
                            Synchronizing Neural Consensus...
                        </>
                    ) : (
                        <span className="flex items-center gap-2">
                            Generate Verified Solution <Sparkles className="h-4 w-4 group-hover:animate-pulse" />
                        </span>
                    )}
                </Button>
            </CardContent>
        </Card>
    );
}

