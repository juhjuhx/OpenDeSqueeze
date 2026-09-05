package io.opendesqueeze.media;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.opendesqueeze.core.CodecPolicy;
import io.opendesqueeze.core.DesqueezeMath;
import io.opendesqueeze.core.OutputNaming;
import io.opendesqueeze.model.ExportSettings;
import io.opendesqueeze.model.SelectedMedia;

/**
 * Framework-only video path:
 * MediaExtractor -> MediaCodec decoder -> SurfaceTexture/OpenGL -> MediaCodec encoder -> MediaMuxer.
 */
public final class VideoTranscoder {
    private static final long CODEC_TIMEOUT_US = 10_000L;

    public interface Callback {
        boolean isCancelled();
        void onProgress(float progress01);
    }

    private VideoTranscoder() {}

    public static Uri transcode(Context context, SelectedMedia media, ExportSettings settings, Callback callback) throws Exception {
        if (CodecPolicy.AV1.equals(settings.videoMime) && Build.VERSION.SDK_INT < 34) {
            throw new IOException("AV1-in-MP4 export requires Android 14 or newer");
        }

        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor inputPfd = resolver.openFileDescriptor(media.uri, "r")) {
            if (inputPfd == null) throw new IOException("Unable to open input video");

            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(inputPfd.getFileDescriptor());
                TrackInfo tracks = findTracks(extractor);
                if (tracks.videoIndex < 0) {
                    throw new IOException("No video track found");
                }
                MediaFormat inputVideoFormat = extractor.getTrackFormat(tracks.videoIndex);
                String inputMime = inputVideoFormat.getString(MediaFormat.KEY_MIME);
                if (inputMime == null) {
                    throw new IOException("Video track has no MIME type");
                }

                int sourceWidth = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
                int sourceHeight = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
                DesqueezeMath.Size target = DesqueezeMath.calculate(sourceWidth, sourceHeight, settings.factor, settings.mode);
                boolean hdr = isHdr(inputVideoFormat);
                if (hdr && !settings.experimentalHdr) {
                    throw new IOException("HDR source detected. Enable experimental HDR handling or process this clip on desktop to avoid uncontrolled tone mapping.");
                }

                List<CodecPolicy.Candidate> candidates = CodecCapabilityScanner.scanForSize(target.width(), target.height());
                List<CodecPolicy.Candidate> ordered = CodecPolicy.orderedCandidates(
                        settings.videoMime, candidates, target.width(), target.height());

                int frameRate = getInt(inputVideoFormat, MediaFormat.KEY_FRAME_RATE, 30);
                frameRate = Math.max(1, Math.min(frameRate, 120));

                String outputName = OutputNaming.withSuffix(media.displayName, "mp4");
                try (PreparedEncoder prepared = prepareEncoder(ordered, target, frameRate, settings, inputVideoFormat);
                     MediaStoreWriter.PendingItem pending = MediaStoreWriter.createVideo(context, outputName, "video/mp4");
                     ParcelFileDescriptor outputPfd = resolver.openFileDescriptor(pending.uri, "rw")) {
                    if (outputPfd == null) throw new IOException("Unable to open output video");
                    transcodeInternal(inputPfd, extractor, tracks, inputVideoFormat, inputMime, prepared,
                            target, media, settings, outputPfd, callback);
                    pending.finish();
                    return pending.uri;
                }
            } finally {
                extractor.release();
            }
        }
    }

    private static void transcodeInternal(
            ParcelFileDescriptor inputPfd,
            MediaExtractor extractor,
            TrackInfo tracks,
            MediaFormat inputVideoFormat,
            String inputMime,
            PreparedEncoder prepared,
            DesqueezeMath.Size target,
            SelectedMedia media,
            ExportSettings settings,
            ParcelFileDescriptor outputPfd,
            Callback callback) throws Exception {

        MediaCodec decoder = null;
        MediaCodec encoder = prepared.codec;
        MediaMuxer muxer = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;
        Surface encoderSurface = prepared.takeSurface();
        boolean muxerStarted = false;
        int videoMuxerTrack = -1;
        int audioMuxerTrack = -1;
        long durationUs = media.durationMs > 0 ? media.durationMs * 1000L : getLong(inputVideoFormat, MediaFormat.KEY_DURATION, 0L);

        try {
            inputSurface = new InputSurface(encoderSurface);
            encoderSurface = null; // owned by InputSurface
            inputSurface.makeCurrent();
            outputSurface = new OutputSurface();

            decoder = MediaCodec.createDecoderByType(inputMime);
            decoder.configure(inputVideoFormat, outputSurface.surface(), null, 0);
            decoder.start();

            extractor.selectTrack(tracks.videoIndex);
            muxer = new MediaMuxer(outputPfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (media.rotationDegrees != 0) muxer.setOrientationHint(media.rotationDegrees);

            MediaCodec.BufferInfo decoderInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encoderInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean decoderDone = false;
            boolean encoderDone = false;

            while (!encoderDone) {
                if (callback != null && callback.isCancelled()) throw new InterruptedException("Cancelled");

                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("Decoder returned null input buffer");
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0);
                            extractor.advance();
                        }
                    }
                }

                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(decoderInfo, CODEC_TIMEOUT_US);
                    if (decoderStatus >= 0) {
                        boolean render = decoderInfo.size > 0;
                        decoder.releaseOutputBuffer(decoderStatus, render);
                        if (render) {
                            outputSurface.awaitAndDraw(target.width(), target.height());
                            inputSurface.setPresentationTime(decoderInfo.presentationTimeUs * 1000L);
                            if (!inputSurface.swapBuffers()) throw new IOException("EGL swapBuffers failed");
                            if (callback != null && durationUs > 0) {
                                callback.onProgress(Math.min(0.98f, decoderInfo.presentationTimeUs / (float) durationUs));
                            }
                        }
                        if ((decoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true;
                            encoder.signalEndOfInputStream();
                        }
                    }
                }

                boolean drainEncoder = true;
                while (drainEncoder) {
                    int encoderStatus = encoder.dequeueOutputBuffer(encoderInfo, CODEC_TIMEOUT_US);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        drainEncoder = decoderDone;
                        if (!decoderDone) break;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw new IOException("Encoder format changed twice");
                        MediaFormat actualVideoFormat = encoder.getOutputFormat();
                        videoMuxerTrack = muxer.addTrack(actualVideoFormat);
                        if (settings.copyAudio && tracks.audioIndex >= 0) {
                            try {
                                audioMuxerTrack = muxer.addTrack(tracks.audioFormat);
                            } catch (IllegalArgumentException e) {
                                throw new IOException("Input audio codec cannot be copied into MP4. Disable 'copy original audio' and retry.", e);
                            }
                        }
                        muxer.start();
                        muxerStarted = true;
                    } else if (encoderStatus >= 0) {
                        ByteBuffer encoded = encoder.getOutputBuffer(encoderStatus);
                        if (encoded == null) throw new IOException("Encoder returned null output buffer");
                        if ((encoderInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) encoderInfo.size = 0;
                        if (encoderInfo.size > 0) {
                            if (!muxerStarted) throw new IOException("Encoder produced data before output format");
                            encoded.position(encoderInfo.offset);
                            encoded.limit(encoderInfo.offset + encoderInfo.size);
                            muxer.writeSampleData(videoMuxerTrack, encoded, encoderInfo);
                        }
                        encoderDone = (encoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encoderStatus, false);
                        if (encoderDone) break;
                    }
                }
            }

            if (!muxerStarted) throw new IOException("Encoder never produced an output format");
            if (settings.copyAudio && audioMuxerTrack >= 0) {
                copyAudio(inputPfd, tracks.audioIndex, audioMuxerTrack, muxer, callback);
            }
            if (callback != null) callback.onProgress(1f);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                decoder.release();
            }
            if (outputSurface != null) outputSurface.close();
            if (inputSurface != null) inputSurface.close();
            if (encoderSurface != null) encoderSurface.release();
            if (muxer != null) {
                try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
                muxer.release();
            }
        }
    }

    private static PreparedEncoder prepareEncoder(
            List<CodecPolicy.Candidate> orderedCandidates,
            DesqueezeMath.Size target,
            int frameRate,
            ExportSettings settings,
            MediaFormat inputVideoFormat) throws IOException {
        Exception lastFailure = null;
        for (CodecPolicy.Candidate candidate : orderedCandidates) {
            if (CodecPolicy.AV1.equals(candidate.mime()) && Build.VERSION.SDK_INT < 34) continue;
            MediaCodec codec = null;
            Surface surface = null;
            try {
                int bitRate = settings.videoBitrateMbps > 0
                        ? settings.videoBitrateMbps * 1_000_000
                        : autoBitrate(target.width(), target.height(), frameRate, candidate.mime());
                MediaFormat outputFormat = MediaFormat.createVideoFormat(candidate.mime(), target.width(), target.height());
                outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
                outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
                outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                if (settings.experimentalHdr) copyColorMetadata(inputVideoFormat, outputFormat);

                codec = MediaCodec.createByCodecName(candidate.name());
                codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                surface = codec.createInputSurface();
                codec.start();
                return new PreparedEncoder(candidate, codec, surface);
            } catch (Exception e) {
                lastFailure = e;
                if (surface != null) surface.release();
                if (codec != null) {
                    try { codec.stop(); } catch (Exception ignored) {}
                    codec.release();
                }
            }
        }
        throw new IOException("No advertised encoder could be started for " + target.width() + "x" + target.height(), lastFailure);
    }

    private static final class PreparedEncoder implements AutoCloseable {
        final CodecPolicy.Candidate candidate;
        final MediaCodec codec;
        private Surface surface;

        PreparedEncoder(CodecPolicy.Candidate candidate, MediaCodec codec, Surface surface) {
            this.candidate = candidate;
            this.codec = codec;
            this.surface = surface;
        }

        Surface takeSurface() {
            Surface result = surface;
            surface = null;
            return result;
        }

        @Override public void close() {
            if (surface != null) {
                surface.release();
                surface = null;
            }
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release();
        }
    }

    private static void copyAudio(ParcelFileDescriptor inputPfd, int inputTrack, int outputTrack,
                                  MediaMuxer muxer, Callback callback) throws IOException, InterruptedException {
        MediaExtractor audioExtractor = new MediaExtractor();
        try {
            audioExtractor.setDataSource(inputPfd.getFileDescriptor());
            audioExtractor.selectTrack(inputTrack);
            MediaFormat format = audioExtractor.getTrackFormat(inputTrack);
            int capacity = getInt(format, MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
            capacity = Math.max(64 * 1024, Math.min(capacity, 8 * 1024 * 1024));
            ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                if (callback != null && callback.isCancelled()) throw new InterruptedException("Cancelled");
                buffer.clear();
                int size = audioExtractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = audioExtractor.getSampleTime();
                info.flags = audioExtractor.getSampleFlags();
                buffer.position(0);
                buffer.limit(size);
                muxer.writeSampleData(outputTrack, buffer, info);
                audioExtractor.advance();
            }
        } finally {
            audioExtractor.release();
        }
    }

    private static TrackInfo findTracks(MediaExtractor extractor) {
        int video = -1;
        int audio = -1;
        MediaFormat audioFormat = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) continue;
            if (video < 0 && mime.startsWith("video/")) video = i;
            if (audio < 0 && mime.startsWith("audio/")) {
                audio = i;
                audioFormat = format;
            }
        }
        return new TrackInfo(video, audio, audioFormat);
    }

    private static int autoBitrate(int width, int height, int fps, String mime) {
        double bpp = CodecPolicy.AVC.equals(mime) ? 0.12 : 0.075;
        long estimate = Math.round(width * (double) height * fps * bpp);
        return (int) Math.max(2_000_000L, Math.min(120_000_000L, estimate));
    }

    private static boolean isHdr(MediaFormat format) {
        if (!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return false;
        int transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
        return transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG;
    }

    private static void copyColorMetadata(MediaFormat from, MediaFormat to) {
        copyInt(from, to, MediaFormat.KEY_COLOR_STANDARD);
        copyInt(from, to, MediaFormat.KEY_COLOR_TRANSFER);
        copyInt(from, to, MediaFormat.KEY_COLOR_RANGE);
        if (from.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
            ByteBuffer hdr = from.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);
            if (hdr != null) to.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, hdr.duplicate());
        }
    }

    private static void copyInt(MediaFormat from, MediaFormat to, String key) {
        if (from.containsKey(key)) to.setInteger(key, from.getInteger(key));
    }

    private static int getInt(MediaFormat format, String key, int fallback) {
        try { return format.containsKey(key) ? format.getInteger(key) : fallback; }
        catch (ClassCastException e) { return fallback; }
    }

    private static long getLong(MediaFormat format, String key, long fallback) {
        try { return format.containsKey(key) ? format.getLong(key) : fallback; }
        catch (ClassCastException e) { return fallback; }
    }

    private static final class TrackInfo {
        final int videoIndex;
        final int audioIndex;
        final MediaFormat audioFormat;
        TrackInfo(int videoIndex, int audioIndex, MediaFormat audioFormat) {
            this.videoIndex = videoIndex;
            this.audioIndex = audioIndex;
            this.audioFormat = audioFormat;
        }
    }
}
