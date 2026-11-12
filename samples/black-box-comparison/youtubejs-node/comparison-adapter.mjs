import { Innertube } from "youtubei.js";
import { performance } from "node:perf_hooks";

const ADAPTER_VERSION = "0.1.1-youtubei.js18.3.0";
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/;
const COMMIT = /^(?:[0-9a-f]{40}|[0-9a-f]{64})$/;
const LOCALE = /^[a-z]{2,3}(?:-[A-Z]{2})?$/;
const SAFE_MIME = /^[A-Za-z0-9.+-]{1,80}\/[A-Za-z0-9.+-]{1,80}$/;
const SAFE_CODEC = /^[A-Za-z0-9._+-]{1,80}$/;
const MAX_MEDIA_BYTES = 65_536;
const MAX_API_BODY_BYTES = 5 * 1024 * 1024;
const MAX_API_REQUEST_BYTES = 1024 * 1024;
const REDIRECTS = new Set([301, 302, 303, 307, 308]);

class SampleFailure extends Error {
  constructor(outcome, failureClass, extractMs = null) {
    super("sample failed");
    this.outcome = outcome;
    this.failureClass = failureClass;
    this.extractMs = extractMs;
  }
}

function detectOs() {
  if (process.platform === "win32") return "windows";
  if (process.platform === "darwin") return "macos";
  if (process.platform === "linux") return "linux";
  throw new Error("unsupported operating system");
}

function parseOptions(args) {
  const allowed = new Set([
    "--video-id", "--comparison-id", "--main-commit", "--run-date-utc",
    "--network-category", "--content-category", "--locale", "--timeout-seconds",
    "--phase", "--sample-index",
  ]);
  if (args.length === 1 && args[0] === "--help") return { help: true };
  const values = new Map();
  for (let index = 0; index < args.length;) {
    const key = args[index];
    if (!allowed.has(key) || values.has(key) || index + 1 >= args.length) {
      throw new Error("invalid arguments");
    }
    values.set(key, args[index + 1]);
    index += 2;
  }
  const get = (key) => {
    const value = values.get(key);
    if (value === undefined) throw new Error("missing required option");
    return value;
  };
  const videoId = get("--video-id");
  const comparisonId = get("--comparison-id");
  const mainCommit = get("--main-commit");
  const runDateUtc = get("--run-date-utc");
  const networkCategory = get("--network-category");
  const contentCategory = get("--content-category");
  const locale = values.get("--locale") ?? "en-US";
  const timeoutSeconds = Number(values.get("--timeout-seconds") ?? "30");
  const phase = get("--phase");
  const sampleIndex = Number(get("--sample-index"));
  const validDate = /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(runDateUtc) &&
    Number.isFinite(Date.parse(runDateUtc));
  if (!VIDEO_ID.test(videoId) || !SAFE_ID.test(comparisonId) || !COMMIT.test(mainCommit) ||
      !validDate || !LOCALE.test(locale) ||
      !new Set(["home", "mobile", "ci", "other"]).has(networkCategory) ||
      !new Set(["stable_public_video", "music_video", "live", "unavailable_case", "other_public"]).has(contentCategory) ||
      !Number.isInteger(timeoutSeconds) || timeoutSeconds < 1 || timeoutSeconds > 30 ||
      !new Set(["cold", "warm"]).has(phase) || !Number.isInteger(sampleIndex) || sampleIndex < 1) {
    throw new Error("invalid option value");
  }
  return {
    videoId, comparisonId, mainCommit, runDateUtc, os: detectOs(),
    networkCategory, contentCategory, locale, timeoutSeconds, phase, sampleIndex,
  };
}

function combinedSignal(existing, deadlineSignal) {
  return existing ? AbortSignal.any([existing, deadlineSignal]) : deadlineSignal;
}

class ApiBodyLimitError extends Error {
  constructor() {
    super("API body limit exceeded");
    this.name = "ApiBodyLimitError";
  }
}

function requestBodySize(input, init) {
  const body = init.body ?? null;
  if (body === null && input instanceof Request && input.body !== null) return null;
  if (body === null) return 0;
  if (typeof body === "string") return Buffer.byteLength(body);
  if (body instanceof ArrayBuffer) return body.byteLength;
  if (ArrayBuffer.isView(body)) return body.byteLength;
  if (body instanceof URLSearchParams) return Buffer.byteLength(body.toString());
  if (typeof Blob !== "undefined" && body instanceof Blob) return body.size;
  return null;
}

function boundedApiResponse(response) {
  const contentLength = response.headers.get("content-length");
  if (contentLength !== null && /^\d+$/.test(contentLength) && Number(contentLength) > MAX_API_BODY_BYTES) {
    void response.body?.cancel().catch(() => {});
    throw new ApiBodyLimitError();
  }
  if (!response.body) return response;

  let received = 0;
  const body = response.body.pipeThrough(new TransformStream({
    transform(chunk, controller) {
      if (!(chunk instanceof Uint8Array)) throw new ApiBodyLimitError();
      received += chunk.byteLength;
      if (received > MAX_API_BODY_BYTES) throw new ApiBodyLimitError();
      controller.enqueue(chunk);
    },
  }));
  const headers = new Headers(response.headers);
  headers.delete("content-length");
  headers.delete("content-encoding");
  headers.delete("transfer-encoding");
  return new Response(body, { status: response.status, statusText: response.statusText, headers });
}

function allowedApiUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  const host = url.hostname.toLowerCase();
  const hostAllowed = host === "youtube.com" || host.endsWith(".youtube.com") ||
    host === "youtubei.googleapis.com" ||
    host === "googlevideo.com" || host.endsWith(".googlevideo.com");
  return url.protocol === "https:" && (url.port === "" || url.port === "443") &&
    hostAllowed && url.username === "" && url.password === "" && url.hash === "";
}

function createApiFetch(state, deadlineSignal) {
  return async (input, init = {}) => {
    const requestBytes = requestBodySize(input, init);
    if (requestBytes === null || requestBytes > MAX_API_REQUEST_BYTES) {
      throw new SampleFailure("FAIL", "HTTP");
    }
    const headers = new Headers(input instanceof Request ? input.headers : undefined);
    new Headers(init.headers).forEach((value, name) => headers.set(name, value));
    for (const name of ["authorization", "cookie", "cookie2", "proxy-authorization"]) {
      headers.delete(name);
    }
    let currentUrl = input instanceof Request ? input.url : String(input);
    if (!allowedApiUrl(currentUrl)) throw new SampleFailure("FAIL", "PARSE");
    const method = String(init.method ?? (input instanceof Request ? input.method : "GET")).toUpperCase();
    let currentInput = input;
    let currentInit = {
      ...init,
      headers,
      credentials: "omit",
      redirect: "manual",
      signal: combinedSignal(init.signal, deadlineSignal),
    };
    for (let hop = 0; hop < 4; hop += 1) {
      state.apiRequests += 1;
      let response;
      try {
        response = await fetch(currentInput, currentInit);
      } catch (error) {
        const failure = classifyError(error, deadlineSignal);
        throw new SampleFailure(failure.outcome, failure.failureClass);
      }
      if (!REDIRECTS.has(response.status)) {
        return boundedApiResponse(response);
      }
      const location = response.headers.get("location");
      try {
        await response.body?.cancel();
      } catch {
        // Redirect bodies are ignored.
      }
      if (hop === 3 || !location || (method !== "GET" && method !== "HEAD")) {
        throw new SampleFailure("FAIL", "HTTP");
      }
      try {
        currentUrl = new URL(location, currentUrl).toString();
      } catch {
        throw new SampleFailure("FAIL", "HTTP");
      }
      if (!allowedApiUrl(currentUrl)) throw new SampleFailure("FAIL", "HTTP");
      currentInput = currentUrl;
      currentInit = {
        method,
        headers,
        credentials: "omit",
        redirect: "manual",
        signal: combinedSignal(init.signal, deadlineSignal),
      };
    }
    throw new SampleFailure("FAIL", "HTTP");
  };
}

function allowedMediaUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  const host = url.hostname.toLowerCase();
  const hostAllowed = host === "youtube.com" || host.endsWith(".youtube.com") ||
    host === "googlevideo.com" || host.endsWith(".googlevideo.com");
  return url.protocol === "https:" && (url.port === "" || url.port === "443") &&
    hostAllowed && url.pathname === "/videoplayback" &&
    url.username === "" && url.password === "" && url.hash === "";
}

function classifyError(error, deadlineSignal) {
  let current = error;
  for (let depth = 0; current && depth < 8; depth += 1, current = current.cause) {
    if (current instanceof ApiBodyLimitError || current?.name === "ApiBodyLimitError") {
      return { outcome: "FAIL", failureClass: "HTTP" };
    }
  }
  if (deadlineSignal.aborted || error?.name === "AbortError" || error?.name === "TimeoutError") {
    return { outcome: "FAIL", failureClass: "TIMEOUT" };
  }
  if (error?.name === "UnsupportedTransportError") {
    return { outcome: "UNSUPPORTED", failureClass: "UNSUPPORTED_TRANSPORT" };
  }
  const status = Number.isInteger(error?.status) ? error.status :
    Number.isInteger(error?.statusCode) ? error.statusCode :
    Number.isInteger(error?.response?.status) ? error.response.status : null;
  if (status === 401 || status === 403) return { outcome: "BLOCKED", failureClass: "ACCESS_DENIED" };
  if (status !== null) return { outcome: "FAIL", failureClass: "HTTP" };
  return { outcome: "FAIL", failureClass: "OTHER" };
}

function sampleFailure(error, deadlineSignal) {
  if (error instanceof SampleFailure) return error;
  const failure = classifyError(error, deadlineSignal);
  return new SampleFailure(failure.outcome, failure.failureClass);
}

function validateContentRange(value, bytesRead) {
  if (typeof value !== "string") return false;
  const match = /^bytes 0-(\d+)\/(\d+|\*)$/.exec(value);
  if (!match) return false;
  const end = Number(match[1]);
  const total = match[2] === "*" ? null : Number(match[2]);
  return Number.isSafeInteger(end) && end >= 0 && end < MAX_MEDIA_BYTES &&
    bytesRead === end + 1 && (total === null || (Number.isSafeInteger(total) && total > end));
}

async function readBoundedBody(response, state, attemptStarted) {
  if (!response.body) throw new SampleFailure("FAIL", "HTTP");
  const reader = response.body.getReader();
  let total = 0;
  try {
    while (total < MAX_MEDIA_BYTES) {
      const result = await reader.read();
      if (result.done) break;
      const chunk = result.value;
      if (!(chunk instanceof Uint8Array)) throw new SampleFailure("FAIL", "PARSE");
      if (chunk.byteLength > 0 && state.firstMediaByteMs === null) {
        state.firstMediaByteMs = performance.now() - attemptStarted;
      }
      const remaining = MAX_MEDIA_BYTES - total;
      const accepted = Math.min(chunk.byteLength, remaining);
      total += accepted;
      state.bytesRead += accepted;
      if (accepted < chunk.byteLength) break;
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      // The capped read is complete; cancellation errors do not change the sample.
    }
  }
  if (total === 0) throw new SampleFailure("FAIL", "HTTP");
  return total;
}

async function probeMedia(initialUrl, state, deadlineSignal, attemptStarted) {
  if (!allowedMediaUrl(initialUrl)) throw new SampleFailure("FAIL", "PARSE");
  let current = initialUrl;
  for (let hop = 0; hop < 4; hop += 1) {
    state.mediaRequests += 1;
    state.rangeRequests += 1;
    let response;
    try {
      response = await fetch(current, {
        method: "GET",
        headers: { Range: "bytes=0-65535" },
        credentials: "omit",
        redirect: "manual",
        signal: deadlineSignal,
      });
    } catch (error) {
      const failure = classifyError(error, deadlineSignal);
      throw new SampleFailure(failure.outcome, failure.failureClass);
    }
    if (REDIRECTS.has(response.status)) {
      const location = response.headers.get("location");
      try {
        await response.body?.cancel();
      } catch {
        // Redirect bodies are ignored.
      }
      if (hop === 3 || !location) throw new SampleFailure("FAIL", "HTTP");
      let next;
      try {
        next = new URL(location, current).toString();
      } catch {
        throw new SampleFailure("FAIL", "HTTP");
      }
      if (!allowedMediaUrl(next)) throw new SampleFailure("FAIL", "HTTP");
      current = next;
      continue;
    }
    if (response.status === 401 || response.status === 403) {
      try { await response.body?.cancel(); } catch {}
      throw new SampleFailure("BLOCKED", "ACCESS_DENIED");
    }
    if (response.status !== 200 && response.status !== 206) {
      try { await response.body?.cancel(); } catch {}
      throw new SampleFailure("FAIL", "HTTP");
    }
    const bytesRead = await readBoundedBody(response, state, attemptStarted);
    if (response.status === 206 && !validateContentRange(response.headers.get("content-range"), bytesRead)) {
      throw new SampleFailure("FAIL", "HTTP");
    }
    return;
  }
  throw new SampleFailure("FAIL", "HTTP");
}

function safeMime(value) {
  if (typeof value !== "string") return null;
  const base = value.split(";", 1)[0].trim();
  return SAFE_MIME.test(base) ? base : null;
}

function codecFromMime(value) {
  if (typeof value !== "string") return null;
  const match = /(?:^|;)\s*codecs\s*=\s*"?([A-Za-z0-9._+-]{1,80})/i.exec(value);
  return match && SAFE_CODEC.test(match[1]) ? match[1] : null;
}

function resetCounters(state) {
  state.apiRequests = 0;
  state.mediaRequests = 0;
  state.rangeRequests = 0;
  state.bytesRead = 0;
  state.firstMediaByteMs = null;
}

async function createClient(options, state, deadlineSignal) {
  const region = options.locale.includes("-") ? options.locale.split("-").at(-1) : "US";
  return Innertube.create({
    fetch: createApiFetch(state, deadlineSignal),
    lang: options.locale.split("-")[0],
    location: region,
    enable_session_cache: false,
    retrieve_player: false,
  });
}

async function getAudioFormat(innertube, options, state, deadlineSignal) {
  const extractionStarted = performance.now();
  try {
    if (!innertube) innertube = await createClient(options, state, deadlineSignal);
    const info = await innertube.getBasicInfo(options.videoId);
    const format = (info.streaming_data?.adaptive_formats ?? [])
      .filter((candidate) => candidate.has_audio === true && candidate.has_video !== true &&
        typeof candidate.url === "string" && candidate.url.length > 0)
      .sort((left, right) => (right.average_bitrate ?? right.bitrate ?? 0) -
        (left.average_bitrate ?? left.bitrate ?? 0))[0] ?? null;
    if (!format) {
      throw new SampleFailure("UNSUPPORTED", "UNSUPPORTED_TRANSPORT");
    }
    return {
      innertube,
      format,
      extractMs: performance.now() - extractionStarted,
    };
  } catch (error) {
    const failure = sampleFailure(error, deadlineSignal);
    failure.extractMs = performance.now() - extractionStarted;
    throw failure;
  }
}

async function runAttempt(options, state, deadlineSignal, innertube) {
  const attemptStarted = performance.now();
  const heapBefore = process.memoryUsage().heapUsed;
  let extractMs = null;
  try {
    const extracted = await getAudioFormat(innertube, options, state, deadlineSignal);
    innertube = extracted.innertube;
    const format = extracted.format;
    extractMs = extracted.extractMs;
    if (!format || typeof format.url !== "string" || format.url.length === 0) {
      throw new SampleFailure("UNSUPPORTED", "UNSUPPORTED_TRANSPORT", extractMs);
    }
    if (format.has_audio !== true || format.has_video === true ||
        (typeof format.mime_type === "string" && !format.mime_type.toLowerCase().startsWith("audio/"))) {
      throw new SampleFailure("UNSUPPORTED", "UNSUPPORTED_TRANSPORT", extractMs);
    }
    await probeMedia(format.url, state, deadlineSignal, attemptStarted);
    const heapDelta = Math.max(0, process.memoryUsage().heapUsed - heapBefore);
    return {
      innertube,
      outcome: "PASS",
      failureClass: null,
      extractMs,
      heapDeltaBytes: heapDelta,
      mimeType: safeMime(format.mime_type),
      codec: codecFromMime(format.mime_type),
    };
  } catch (error) {
    const failure = sampleFailure(error, deadlineSignal);
    if (failure.extractMs === null) failure.extractMs = extractMs;
    failure.heapDeltaBytes = Math.max(0, process.memoryUsage().heapUsed - heapBefore);
    throw failure;
  }
}

function jsonNumber(value) {
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 1000) / 1000 : null;
}

function makeSample(options, state, result) {
  const outcome = result.outcome;
  const failureClass = result.failureClass;
  const measurements = {
    metadataMs: null,
    extractMs: jsonNumber(result.extractMs),
    startupMs: null,
    firstMediaByteMs: jsonNumber(state.firstMediaByteMs),
    decodeMs: null,
    requestCount: state.apiRequests + state.mediaRequests,
    mediaRequestCount: state.mediaRequests,
    bytesRead: state.bytesRead,
    rangeRequests: state.rangeRequests,
    heapDeltaBytes: result.heapDeltaBytes ?? null,
    backwardSeek: "NOT_TESTED",
    forwardSeek: "NOT_TESTED",
    decode: "NOT_TESTED",
    codec: result.codec ?? null,
    mimeType: result.mimeType ?? null,
  };
  return JSON.stringify({
    schemaVersion: 1,
    comparison: {
      id: options.comparisonId,
      mainCommit: options.mainCommit,
      runDateUtc: options.runDateUtc,
      os: options.os,
      networkCategory: options.networkCategory,
      contentCategory: options.contentCategory,
      sessionCategory: "signed_out",
      timeoutSeconds: options.timeoutSeconds,
      locale: options.locale,
      decoderVersion: null,
    },
    adapter: "youtubejs",
    adapterVersion: ADAPTER_VERSION,
    runtime: "node",
    lane: "direct_audio",
    phase: options.phase,
    sampleIndex: options.sampleIndex,
    videoId: options.videoId,
    outcome,
    failureClass,
    measurements,
  });
}

function printUsage() {
  process.stdout.write(
    "Usage: node comparison-adapter.mjs --video-id ID --comparison-id ID --main-commit SHA --run-date-utc UTC " +
    "--network-category home|mobile|ci|other --content-category CATEGORY --phase cold|warm --sample-index N " +
    "[--locale en-US] [--timeout-seconds 30]\n",
  );
}

async function main() {
  let options;
  try {
    options = parseOptions(process.argv.slice(2));
  } catch (error) {
    process.stderr.write("adapter error: " + (error?.message ?? "invalid input") + "\n");
    return 2;
  }
  if (options.help) {
    printUsage();
    return 0;
  }

  const state = {
    apiRequests: 0,
    mediaRequests: 0,
    rangeRequests: 0,
    bytesRead: 0,
    firstMediaByteMs: null,
  };
  const deadlineController = new AbortController();
  const deadlineTimer = setTimeout(
    () => deadlineController.abort(new DOMException("sample deadline reached", "TimeoutError")),
    options.timeoutSeconds * 1000,
  );
  let result = {
    outcome: "FAIL",
    failureClass: "OTHER",
    extractMs: null,
    heapDeltaBytes: null,
    mimeType: null,
    codec: null,
  };

  try {
    let client = null;
    if (options.phase === "warm") {
      try {
        const warm = await runAttempt(options, state, deadlineController.signal, null);
        client = warm.innertube;
        resetCounters(state);
      } catch (error) {
        const failure = sampleFailure(error, deadlineController.signal);
        result = {
          ...result,
          outcome: failure.outcome,
          failureClass: failure.failureClass,
          extractMs: failure.extractMs,
          heapDeltaBytes: failure.heapDeltaBytes ?? null,
        };
        process.stdout.write(makeSample(options, state, result) + "\n");
        return 0;
      }
    }
    try {
      result = await runAttempt(options, state, deadlineController.signal, client);
    } catch (error) {
      const failure = sampleFailure(error, deadlineController.signal);
      result = {
        ...result,
        outcome: failure.outcome,
        failureClass: failure.failureClass,
        extractMs: failure.extractMs,
        heapDeltaBytes: failure.heapDeltaBytes ?? null,
      };
    }
    process.stdout.write(makeSample(options, state, result) + "\n");
    return 0;
  } finally {
    clearTimeout(deadlineTimer);
  }
}

main().then((code) => {
  process.exitCode = code;
}).catch(() => {
  process.stderr.write("adapter error: unexpected failure\n");
  process.exitCode = 2;
});
