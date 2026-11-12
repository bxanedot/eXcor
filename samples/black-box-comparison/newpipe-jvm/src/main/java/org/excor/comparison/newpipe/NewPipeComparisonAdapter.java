package org.excor.comparison.newpipe;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.ExtractorLogger;
import org.schabi.newpipe.extractor.utils.Logger;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Isolated signed-out NewPipeExtractor comparator. */
public final class NewPipeComparisonAdapter {
    private static final String ADAPTER_VERSION = "0.1.0-newpipe-0.27.1";
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern COMMIT = Pattern.compile("^(?:[0-9a-f]{40}|[0-9a-f]{64})$");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2,3}(?:-[A-Z]{2})?$");
    private static final Pattern MIME = Pattern.compile("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$");
    private static final Pattern CODEC = Pattern.compile("^[A-Za-z0-9._+-]{1,80}$");
    private static final Set<String> NETWORK_CATEGORIES = Set.of("home", "mobile", "ci", "other");
    private static final Set<String> CONTENT_CATEGORIES = Set.of(
            "stable_public_video", "music_video", "live", "unavailable_case", "other_public");
    private static final Set<String> PHASES = Set.of("cold", "warm");
    private static final Set<String> ACCESS_CHALLENGES = Set.of(
            "ReCaptchaException", "SignInConfirmNotBotException", "AgeRestrictedContentException",
            "GeographicRestrictionException", "PrivateContentException", "PaidContentException");

    private static final Logger SILENT_LOGGER = new Logger() {
        @Override public void debug(String tag, String message) { }
        @Override public void debug(String tag, String message, Throwable throwable) { }
        @Override public void error(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable throwable) { }
        @Override public void warn(String tag, String message) { }
        @Override public void warn(String tag, String message, Throwable throwable) { }
    };

    private NewPipeComparisonAdapter() { }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) System.exit(exitCode);
    }

    private static int run(String[] args) {
        Options options;
        try {
            options = parseOptions(args);
        } catch (HelpRequested help) {
            printUsage();
            return 0;
        } catch (IllegalArgumentException invalid) {
            System.err.println("adapter error: invalid arguments");
            return 2;
        }

        long sampleStarted = System.nanoTime();
        Deadline deadline = new Deadline(sampleStarted + Duration.ofSeconds(options.timeoutSeconds()).toNanos());
        Metrics metrics = new Metrics();
        ExecutorService httpExecutor = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "newpipe-comparison-http");
            thread.setDaemon(true);
            return thread;
        });
        BoundedDownloader downloader = new BoundedDownloader(metrics, deadline, options.locale(), httpExecutor);
        AttemptResult result;
        try {
            ExtractorLogger.setLogger(SILENT_LOGGER);
            NewPipe.init(downloader, localization(options.locale()));

            if ("warm".equals(options.phase())) {
                AttemptResult warmup = attempt(options, downloader, metrics, deadline, false);
                if (!"PASS".equals(warmup.outcome())) {
                    System.out.println(toJson(options, metrics, warmup));
                    return 0;
                }
                metrics.reset();
            }

            result = attempt(options, downloader, metrics, deadline, true);
        } catch (Exception unexpected) {
            result = classify(unexpected, downloader, deadline, null);
        } finally {
            httpExecutor.shutdownNow();
        }
        System.out.println(toJson(options, metrics, result));
        return 0;
    }

    private static Options parseOptions(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) throw new HelpRequested();
        Set<String> allowed = Set.of(
                "--video-id", "--comparison-id", "--main-commit", "--run-date-utc",
                "--network-category", "--content-category", "--locale", "--timeout-seconds",
                "--phase", "--sample-index");
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < args.length;) {
            String key = args[index];
            if (!allowed.contains(key) || values.containsKey(key) || index + 1 >= args.length) {
                throw new IllegalArgumentException("invalid arguments");
            }
            values.put(key, args[index + 1]);
            index += 2;
        }

        String videoId = required(values, "--video-id");
        String comparisonId = required(values, "--comparison-id");
        String mainCommit = required(values, "--main-commit");
        String runDateUtc = required(values, "--run-date-utc");
        String networkCategory = required(values, "--network-category");
        String contentCategory = required(values, "--content-category");
        String locale = values.getOrDefault("--locale", "en-US");
        String phase = required(values, "--phase");
        int timeoutSeconds = parseInt(values.getOrDefault("--timeout-seconds", "30"));
        int sampleIndex = parseInt(required(values, "--sample-index"));

        try {
            if (!VIDEO_ID.matcher(videoId).matches() || !SAFE_ID.matcher(comparisonId).matches()
                    || !COMMIT.matcher(mainCommit).matches() || !runDateUtc.endsWith("Z")) {
                throw new IllegalArgumentException("invalid identifier");
            }
            Instant.parse(runDateUtc);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid identifier");
        }
        if (!LOCALE.matcher(locale).matches() || !NETWORK_CATEGORIES.contains(networkCategory)
                || !CONTENT_CATEGORIES.contains(contentCategory) || !PHASES.contains(phase)
                || timeoutSeconds < 1 || timeoutSeconds > 30 || sampleIndex < 1) {
            throw new IllegalArgumentException("invalid option value");
        }
        return new Options(videoId, comparisonId, mainCommit, runDateUtc, detectOs(),
                networkCategory, contentCategory, locale, timeoutSeconds, phase, sampleIndex);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("missing option");
        return value;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid numeric option");
        }
    }

    private static String detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("windows")) return "windows";
        if (name.contains("mac") || name.contains("darwin")) return "macos";
        if (name.contains("linux")) return "linux";
        throw new IllegalArgumentException("unsupported operating system");
    }

    private static Localization localization(String locale) {
        int separator = locale.indexOf('-');
        if (separator < 0) return new Localization(locale);
        return new Localization(locale.substring(0, separator), locale.substring(separator + 1));
    }

    private static AttemptResult attempt(Options options, BoundedDownloader downloader, Metrics metrics,
                                         Deadline deadline, boolean probeMedia) {
        long attemptStarted = System.nanoTime();
        metrics.beginAttempt(attemptStarted);
        long extractionStarted = 0L;
        Double extractMs = null;
        String codec = null;
        String mimeType = null;
        try {
            deadline.requireTime();
            extractionStarted = System.nanoTime();
            StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + options.videoId());
            extractMs = elapsedMs(extractionStarted);

            AudioStream audio = info.getAudioStreams().stream()
                    .filter(stream -> stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
                    .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
                    .orElse(null);
            if (audio == null) return new AttemptResult("UNSUPPORTED", "UNSUPPORTED_TRANSPORT", extractMs, null, null);

            codec = safeValue(audio.getCodec(), CODEC);
            MediaFormat format = audio.getFormat();
            mimeType = format == null ? null : safeMime(format.getMimeType());
            if (probeMedia) downloader.probeMedia(audio.getContent());
            return new AttemptResult("PASS", null, extractMs, codec, mimeType);
        } catch (Exception failure) {
            if (extractMs == null && extractionStarted != 0L) extractMs = elapsedMs(extractionStarted);
            AttemptResult classified = classify(failure, downloader, deadline, extractMs);
            return new AttemptResult(classified.outcome(), classified.failureClass(), extractMs, codec, mimeType);
        }
    }

    private static AttemptResult classify(Exception failure, BoundedDownloader downloader,
                                          Deadline deadline, Double extractMs) {
        if (failure instanceof ProbeFailure probeFailure) {
            return new AttemptResult(probeFailure.outcome(), probeFailure.failureClass(), extractMs, null, null);
        }
        if (deadline.expired() || hasCause(failure, HttpTimeoutException.class)
                || hasCause(failure, InterruptedException.class)) {
            if (hasCause(failure, InterruptedException.class)) Thread.currentThread().interrupt();
            return new AttemptResult("FAIL", "TIMEOUT", extractMs, null, null);
        }
        int status = downloader == null ? -1 : downloader.lastStatusCode();
        if (status == 401 || status == 403 || hasAnyCauseNamed(failure, ACCESS_CHALLENGES)) {
            return new AttemptResult("BLOCKED", "ACCESS_DENIED", extractMs, null, null);
        }
        if (status >= 400) return new AttemptResult("FAIL", "HTTP", extractMs, null, null);
        if (hasCauseNamed(failure, "BodyLimitException")) {
            return new AttemptResult("FAIL", "HTTP", extractMs, null, null);
        }
        if (hasCauseNamed(failure, "ContentNotAvailableException")) {
            return new AttemptResult("FAIL", "OTHER", extractMs, null, null);
        }
        if (hasCauseNamed(failure, "ParsingException") || hasCauseNamed(failure, "StreamExtractException")) {
            return new AttemptResult("FAIL", "PARSE", extractMs, null, null);
        }
        return new AttemptResult("FAIL", "OTHER", extractMs, null, null);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static boolean hasCauseNamed(Throwable throwable, String simpleName) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (simpleName.equals(current.getClass().getSimpleName())) return true;
        }
        return false;
    }

    private static boolean hasAnyCauseNamed(Throwable throwable, Set<String> simpleNames) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (simpleNames.contains(current.getClass().getSimpleName())) return true;
        }
        return false;
    }

    private static String safeValue(String input, Pattern pattern) {
        return input != null && pattern.matcher(input).matches() ? input : null;
    }

    private static String safeMime(String input) {
        if (input == null) return null;
        String baseType = input.split(";", 2)[0].trim();
        return safeValue(baseType, MIME);
    }

    private static Double elapsedMs(long startedAt) {
        return Math.max(0L, System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private static String toJson(Options options, Metrics metrics, AttemptResult result) {
        long firstByteAt = metrics.firstMediaByteAtNanos.get();
        Double firstMediaByteMs = firstByteAt == 0L ? null
                : Math.max(0L, firstByteAt - metrics.attemptStartedNanos.get()) / 1_000_000.0;
        long requestCount = metrics.downloaderRequests.get() + metrics.mediaRequests.get();
        return "{\"schemaVersion\":1,\"comparison\":{" +
                "\"id\":" + json(options.comparisonId()) + "," +
                "\"mainCommit\":" + json(options.mainCommit()) + "," +
                "\"runDateUtc\":" + json(options.runDateUtc()) + "," +
                "\"os\":" + json(options.os()) + "," +
                "\"networkCategory\":" + json(options.networkCategory()) + "," +
                "\"contentCategory\":" + json(options.contentCategory()) + "," +
                "\"sessionCategory\":\"signed_out\",\"timeoutSeconds\":" + options.timeoutSeconds() + "," +
                "\"locale\":" + json(options.locale()) + ",\"decoderVersion\":null}," +
                "\"adapter\":\"newpipe\",\"adapterVersion\":" + json(ADAPTER_VERSION) + "," +
                "\"runtime\":\"jvm\",\"lane\":\"direct_audio\",\"phase\":" + json(options.phase()) + "," +
                "\"sampleIndex\":" + options.sampleIndex() + ",\"videoId\":" + json(options.videoId()) + "," +
                "\"outcome\":" + json(result.outcome()) + ",\"failureClass\":" + json(result.failureClass()) + "," +
                "\"measurements\":{" +
                "\"metadataMs\":null,\"extractMs\":" + number(result.extractMs()) + ",\"startupMs\":null," +
                "\"firstMediaByteMs\":" + number(firstMediaByteMs) + ",\"decodeMs\":null," +
                "\"requestCount\":" + requestCount + ",\"mediaRequestCount\":" + metrics.mediaRequests.get() + "," +
                "\"bytesRead\":" + metrics.bytesRead.get() + ",\"rangeRequests\":" + metrics.rangeRequests.get() + "," +
                "\"heapDeltaBytes\":null,\"backwardSeek\":\"NOT_TESTED\",\"forwardSeek\":\"NOT_TESTED\"," +
                "\"decode\":\"NOT_TESTED\",\"rangeProbeMethod\":\"NOT_TESTED\"," +
                "\"rangeForwardProbe\":\"NOT_TESTED\",\"rangeBackwardProbe\":\"NOT_TESTED\",\"codec\":" + json(result.codec()) + "," +
                "\"mimeType\":" + json(result.mimeType()) + "}}";
    }

    private static String number(Double value) {
        if (value == null || !Double.isFinite(value)) return "null";
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String json(String value) {
        if (value == null) return "null";
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static void printUsage() {
        System.out.println("Usage: newpipe-black-box-adapter --video-id ID --comparison-id ID --main-commit SHA " +
                "--run-date-utc UTC --network-category home|mobile|ci|other --content-category CATEGORY " +
                "--phase cold|warm --sample-index N [--locale en-US] [--timeout-seconds 30]");
    }

    private record Options(String videoId, String comparisonId, String mainCommit, String runDateUtc,
                           String os, String networkCategory, String contentCategory, String locale,
                           int timeoutSeconds, String phase, int sampleIndex) { }

    private record AttemptResult(String outcome, String failureClass, Double extractMs,
                                 String codec, String mimeType) { }

    static final class Metrics {
        final AtomicLong downloaderRequests = new AtomicLong();
        final AtomicLong mediaRequests = new AtomicLong();
        final AtomicLong rangeRequests = new AtomicLong();
        final AtomicLong bytesRead = new AtomicLong();
        final AtomicLong firstMediaByteAtNanos = new AtomicLong();
        final AtomicLong attemptStartedNanos = new AtomicLong();

        void beginAttempt(long startedAt) {
            attemptStartedNanos.set(startedAt);
            firstMediaByteAtNanos.set(0L);
        }

        void reset() {
            downloaderRequests.set(0L);
            mediaRequests.set(0L);
            rangeRequests.set(0L);
            bytesRead.set(0L);
            firstMediaByteAtNanos.set(0L);
        }
    }

    static final class Deadline {
        final long deadlineNanos;

        Deadline(long deadlineNanos) { this.deadlineNanos = deadlineNanos; }

        boolean expired() { return System.nanoTime() >= deadlineNanos; }

        Duration remaining() throws ProbeFailure {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0L) throw new ProbeFailure("FAIL", "TIMEOUT");
            return Duration.ofNanos(nanos);
        }

        void requireTime() throws ProbeFailure { remaining(); }
    }

    static final class ProbeFailure extends java.io.IOException {
        final String outcome;
        final String failureClass;

        ProbeFailure(String outcome, String failureClass) {
            super("bounded comparison request failed");
            this.outcome = outcome;
            this.failureClass = failureClass;
        }

        String outcome() { return outcome; }
        String failureClass() { return failureClass; }
    }

    private static final class HelpRequested extends RuntimeException { }
}
