package org.excor.comparison.newpipe;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.IntConsumer;

/** Request boundary and bounded media probe for the isolated NewPipe comparator. */
final class BoundedDownloader extends Downloader {
    private static final int MAX_API_BODY_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final int MAX_MEDIA_BYTES = 65_536;
    private static final int MAX_REDIRECTS = 4;
    private static final Pattern CONTENT_RANGE = Pattern.compile("^bytes 0-(\\d+)/(\\d+|\\*)$");
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Map<String, String> SAFE_HEADERS = Map.ofEntries(
            Map.entry("accept", "Accept"),
            Map.entry("accept-language", "Accept-Language"),
            Map.entry("content-type", "Content-Type"),
            Map.entry("origin", "Origin"),
            Map.entry("referer", "Referer"),
            Map.entry("range", "Range"),
            Map.entry("user-agent", "User-Agent"),
            Map.entry("x-goog-visitor-id", "X-Goog-Visitor-Id"),
            Map.entry("x-youtube-client-name", "X-YouTube-Client-Name"),
            Map.entry("x-youtube-client-version", "X-YouTube-Client-Version"),
            Map.entry("x-youtube-page-cl", "X-YouTube-Page-CL"),
            Map.entry("x-youtube-page-label", "X-YouTube-Page-Label"),
            Map.entry("x-youtube-time-zone", "X-YouTube-Time-Zone"),
            Map.entry("x-youtube-utc-offset", "X-YouTube-Utc-Offset"));

    private final NewPipeComparisonAdapter.Metrics metrics;
    private final NewPipeComparisonAdapter.Deadline deadline;
    private final String locale;
    private final HttpClient client;
    private final AtomicInteger lastStatusCode = new AtomicInteger(-1);

    BoundedDownloader(NewPipeComparisonAdapter.Metrics metrics,
                      NewPipeComparisonAdapter.Deadline deadline,
                      String locale,
                      ExecutorService executor) {
        this.metrics = metrics;
        this.deadline = deadline;
        this.locale = locale;
        this.client = HttpClient.newBuilder()
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Response execute(Request request) throws IOException {
        String method = request.httpMethod().toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "HEAD", "POST").contains(method)) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
        }
        byte[] requestBody = request.dataToSend();
        if (requestBody != null && requestBody.length > MAX_REQUEST_BODY_BYTES) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
        }
        URI current = validateUrl(request.url(), false);
        Map<String, List<String>> headers = safeRequestHeaders(request.headers());
        headers.putIfAbsent("Accept-Language", List.of(locale));

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            metrics.downloaderRequests.incrementAndGet();
            lastStatusCode.set(-1);
            HttpRequest outbound = buildRequest(current, method, requestBody, headers);
            var response = send(outbound, MAX_API_BODY_BYTES, false, ignored -> { });
            int status = response.statusCode();
            lastStatusCode.set(status);
            if (!REDIRECTS.contains(status)) {
                return new Response(status, Integer.toString(status), safeResponseHeaders(response.headers().map()),
                        new String(response.body(), StandardCharsets.UTF_8), current.toString());
            }

            String location = response.headers().firstValue("location").orElse(null);
            if (location == null || hop == MAX_REDIRECTS) {
                throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
            }
            URI next;
            try {
                next = validateUrl(current.resolve(location).toString(), false);
            } catch (IllegalArgumentException unsafe) {
                throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
            }
            if (!sameOrigin(current, next)) {
                headers.remove("Origin");
                headers.remove("Referer");
            }
            if (status == 303 && !"HEAD".equals(method)
                    || (status == 301 || status == 302) && "POST".equals(method)) {
                method = "GET";
                requestBody = null;
                headers.remove("Content-Type");
            }
            current = next;
        }
        throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
    }

    int lastStatusCode() {
        return lastStatusCode.get();
    }

    void probeMedia(String mediaUrl) throws IOException {
        URI current = validateUrl(mediaUrl, true);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            metrics.mediaRequests.incrementAndGet();
            lastStatusCode.set(-1);
            metrics.rangeRequests.incrementAndGet();
            HttpRequest outbound = HttpRequest.newBuilder(current)
                    .timeout(deadline.remaining())
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=0-65535")
                    .GET()
                    .build();
            var response = send(outbound, MAX_MEDIA_BYTES, true, byteCount -> {
                metrics.firstMediaByteAtNanos.compareAndSet(0L, System.nanoTime());
                metrics.bytesRead.addAndGet(byteCount);
            });
            int status = response.statusCode();
            lastStatusCode.set(status);
            if (REDIRECTS.contains(status)) {
                String location = response.headers().firstValue("location").orElse(null);
                if (location == null || hop == MAX_REDIRECTS) {
                    throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
                }
                try {
                    current = validateUrl(current.resolve(location).toString(), true);
                } catch (IllegalArgumentException unsafe) {
                    throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
                }
                continue;
            }
            if (status == 401 || status == 403) {
                throw new NewPipeComparisonAdapter.ProbeFailure("BLOCKED", "ACCESS_DENIED");
            }
            if (status != 200 && status != 206) {
                throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
            }
            int bytes = response.body().length;
            if (bytes == 0 || status == 206 && !validContentRange(response.headers().firstValue("content-range").orElse(null), bytes)) {
                throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
            }
            return;
        }
        throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
    }

    private HttpRequest buildRequest(URI uri, String method, byte[] body, Map<String, List<String>> headers)
            throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(deadline.remaining())
                .header("Accept-Encoding", "identity");
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (("GET".equals(method) || "HEAD".equals(method)) && body != null && body.length > 0) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        switch (method) {
            case "GET" -> builder.GET();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case "POST" -> builder.POST(publisher);
            default -> throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "HTTP");
        }
        return builder.build();
    }

    private java.net.http.HttpResponse<byte[]> send(HttpRequest request, int bodyLimit, boolean truncate,
                                                    IntConsumer onBytes) throws IOException {
        try {
            return client.send(request, responseInfo -> {
                lastStatusCode.set(responseInfo.statusCode());
                return new LimitedBodySubscriber(bodyLimit, truncate, onBytes);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("request interrupted", interrupted);
        } catch (HttpTimeoutException timeout) {
            throw timeout;
        }
    }

    private static URI validateUrl(String value, boolean media) throws NewPipeComparisonAdapter.ProbeFailure {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException invalid) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", media ? "PARSE" : "HTTP");
        }
        String host = uri.getHost();
        if (host == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getPort() != -1 && uri.getPort() != 443
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !allowedHost(host.toLowerCase(Locale.ROOT))) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", media ? "PARSE" : "HTTP");
        }
        if (media && !"/videoplayback".equals(uri.getRawPath())) {
            throw new NewPipeComparisonAdapter.ProbeFailure("FAIL", "PARSE");
        }
        return uri;
    }

    private static boolean allowedHost(String host) {
        return host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtubei.googleapis.com") || host.equals("youtube.googleapis.com")
                || host.equals("www.youtube-nocookie.com")
                || host.equals("googlevideo.com") || host.endsWith(".googlevideo.com");
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private static Map<String, List<String>> safeRequestHeaders(Map<String, List<String>> input) {
        Map<String, List<String>> result = new HashMap<>();
        if (input == null) return result;
        input.forEach((inputName, values) -> {
            if (inputName == null || values == null) return;
            String canonical = SAFE_HEADERS.get(inputName.toLowerCase(Locale.ROOT));
            if (canonical == null) return;
            List<String> safeValues = new ArrayList<>();
            for (String value : values) {
                if (value != null && value.length() <= 4096
                        && value.chars().allMatch(character -> character >= 0x20 && character <= 0x7e)) {
                    safeValues.add(value);
                }
            }
            if (!safeValues.isEmpty()) result.put(canonical, List.copyOf(safeValues));
        });
        return result;
    }

    private static Map<String, List<String>> safeResponseHeaders(Map<String, List<String>> input) {
        Map<String, List<String>> result = new HashMap<>();
        input.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals("set-cookie") && !lower.equals("set-cookie2")
                    && !lower.equals("www-authenticate") && !lower.equals("proxy-authenticate")) {
                result.put(name, List.copyOf(values));
            }
        });
        return result;
    }

    private static boolean validContentRange(String value, int bytesRead) {
        if (value == null) return false;
        Matcher match = CONTENT_RANGE.matcher(value);
        if (!match.matches()) return false;
        try {
            long end = Long.parseLong(match.group(1));
            String totalText = match.group(2);
            return end >= 0L && end < MAX_MEDIA_BYTES && bytesRead == end + 1L
                    && ("*".equals(totalText) || Long.parseLong(totalText) > end);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static final class LimitedBodySubscriber implements BodySubscriber<byte[]> {
        private final int limit;
        private final boolean truncate;
        private final IntConsumer onBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int limit, boolean truncate, IntConsumer onBytes) {
            this.limit = limit;
            this.truncate = truncate;
            this.onBytes = onBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> chunks) {
            if (body.isDone()) return;
            try {
                for (ByteBuffer chunk : chunks) {
                    ByteBuffer data = chunk.slice();
                    int received = data.remaining();
                    if (received == 0) continue;
                    int remaining = limit - output.size();
                    if (received > remaining && !truncate) {
                        subscription.cancel();
                        body.completeExceptionally(new BodyLimitException());
                        return;
                    }
                    int accepted = Math.min(received, remaining);
                    byte[] copy = new byte[accepted];
                    data.get(copy);
                    output.write(copy, 0, accepted);
                    if (accepted > 0) onBytes.accept(accepted);
                    if (truncate && (accepted < received || output.size() >= limit)) {
                        subscription.cancel();
                        body.complete(output.toByteArray());
                        return;
                    }
                }
                subscription.request(1);
            } catch (RuntimeException failure) {
                subscription.cancel();
                body.completeExceptionally(failure);
            }
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

    private static final class BodyLimitException extends RuntimeException { }
}