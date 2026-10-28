package com.excor

import com.excor.internal.EJS_CORE
import com.excor.internal.EJS_LIB
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

internal data class PlayerJsOutput(
    val preparedPlayer: String?,
    val signatures: Map<String, String>,
    val throttling: Map<String, String>,
)

internal suspend fun solvePlayerJs(
    playerScript: String?,
    preparedPlayer: String?,
    signatures: List<String>,
    throttling: List<String>,
): PlayerJsOutput? {
    if (playerScript == null && preparedPlayer == null) return null
    val requests = buildJsonArray {
        if (signatures.isNotEmpty()) add(challengeRequest("sig", signatures))
        if (throttling.isNotEmpty()) add(challengeRequest("n", throttling))
    }
    val input = buildJsonObject {
        if (preparedPlayer != null) {
            put("type", "preprocessed")
            put("preprocessed_player", preparedPlayer)
        } else {
            put("type", "player")
            put("player", playerScript!!)
            put("output_preprocessed", true)
        }
        put("requests", requests)
    }
    val encoded = try {
        runEjs(input.toString())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        return null
    }
    val result = try {
        Json.parseToJsonElement(encoded).jsonObject
    } catch (_: Exception) {
        return null
    }
    if (result["type"]?.jsonPrimitive?.content != "result") return null
    val responses = result["responses"]?.jsonArray ?: return PlayerJsOutput(
        result["preprocessed_player"]?.jsonPrimitive?.content,
        emptyMap(),
        emptyMap(),
    )
    val responseTypes = buildList {
        if (signatures.isNotEmpty()) add("sig")
        if (throttling.isNotEmpty()) add("n")
    }
    val sigValues = mutableMapOf<String, String>()
    val nValues = mutableMapOf<String, String>()
    responses.forEachIndexed { index, element ->
        val response = element.jsonObject
        if (response["type"]?.jsonPrimitive?.content != "result") return@forEachIndexed
        val data = response["data"]?.jsonObject ?: return@forEachIndexed
        val target = when (responseTypes.getOrNull(index)) {
            "sig" -> sigValues
            "n" -> nValues
            else -> return@forEachIndexed
        }
        data.forEach { (key, value) -> value.jsonPrimitive.contentOrNull?.let { target[key] = it } }
    }
    return PlayerJsOutput(
        preparedPlayer = result["preprocessed_player"]?.jsonPrimitive?.content,
        signatures = sigValues,
        throttling = nValues,
    )
}

private fun challengeRequest(type: String, challenges: List<String>) = buildJsonObject {
    put("type", type)
    put("challenges", JsonArray(challenges.map(::JsonPrimitive)))
}

private val SIGNATURE_TIMESTAMP = Regex("(?:signatureTimestamp|sts)\\s*:\\s*([0-9]{5})")

internal fun signatureTimestamp(playerScript: String): Int? =
    SIGNATURE_TIMESTAMP.find(playerScript)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal suspend fun runEjs(input: String): String {
    require(input.length <= 2_000_000) { "Player challenge input is too large" }
    val inputLiteral = JsonPrimitive(input).toString()
    val program = "$EJS_LIB\nObject.assign(globalThis, lib.lib);\n" +
        "globalThis.require = name => { if (name === 'meriyah' || name === 'astring') return lib.lib[name]; throw new Error('Unsupported EJS dependency'); };\n" +
        "$URL_SHIM\n$EJS_CORE\nJSON.stringify(jsc.default(JSON.parse($inputLiteral)))"
    return withContext(Dispatchers.Default) {
        QuickJs.create(Dispatchers.Default).use { runtime ->
            runtime.memoryLimit = 64L * 1024 * 1024
            runtime.maxStackSize = 1024L * 1024
            runtime.evaluationTimeoutMillis = 10_000
            runtime.evaluate<String>(program)
        }
    }
}

private val URL_SHIM = """
if (typeof URLSearchParams === 'undefined') {
  globalThis.URLSearchParams = class URLSearchParams {
    constructor(query) {
      this.entries = [];
      const source = String(query || '').replace(/^\?/, '');
      for (const part of source.split('&')) {
        if (!part) continue;
        const pair = part.split('=', 2);
        this.entries.push([decodeURIComponent(pair[0].replace(/\+/g, ' ')), decodeURIComponent((pair[1] || '').replace(/\+/g, ' '))]);
      }
    }
    append(key, value) { this.entries.push([String(key), String(value)]); }
    delete(key) { this.entries = this.entries.filter(pair => pair[0] !== String(key)); }
    get(key) { return this.entries.find(pair => pair[0] === String(key))?.[1] ?? null; }
    getAll(key) { return this.entries.filter(pair => pair[0] === String(key)).map(pair => pair[1]); }
    has(key) { return this.entries.some(pair => pair[0] === String(key)); }
    set(key, value) {
      key = String(key); value = String(value);
      const index = this.entries.findIndex(pair => pair[0] === key);
      this.delete(key);
      this.entries.splice(index < 0 ? this.entries.length : index, 0, [key, value]);
    }
    toString() { return this.entries.map(pair => encodeURIComponent(pair[0]) + '=' + encodeURIComponent(pair[1])).join('&'); }
  };
}
if (typeof URL === 'undefined') {
  globalThis.URL = class URL {
    constructor(input, base) { this.setHref(String(input), base); }
    setHref(input, base) {
      let value = input;
      if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(value)) {
        if (!base) throw new TypeError('Invalid URL');
        const parent = new URL(base);
        value = value.startsWith('/') ? parent.origin + value : parent.origin + parent.pathname.slice(0, parent.pathname.lastIndexOf('/') + 1) + value;
      }
      const schemeEnd = value.indexOf('://');
      if (schemeEnd < 1) throw new TypeError('Invalid URL');
      this.protocol = value.slice(0, schemeEnd + 1);
      const rest = value.slice(schemeEnd + 3);
      const authorityEnd = rest.search(/[/?#]/);
      this.host = authorityEnd < 0 ? rest : rest.slice(0, authorityEnd);
      this.hostname = this.host;
      this.port = '';
      this.username = '';
      this.password = '';
      this.origin = this.protocol + '//' + this.host;
      let tail = authorityEnd < 0 ? '/' : rest.slice(authorityEnd);
      const hashAt = tail.indexOf('#');
      this.hash = hashAt < 0 ? '' : tail.slice(hashAt);
      if (hashAt >= 0) tail = tail.slice(0, hashAt);
      const queryAt = tail.indexOf('?');
      this.pathname = (queryAt < 0 ? tail : tail.slice(0, queryAt)) || '/';
      this.searchParams = new URLSearchParams(queryAt < 0 ? '' : tail.slice(queryAt + 1));
    }
    get search() { const query = this.searchParams.toString(); return query ? '?' + query : ''; }
    set search(value) { this.searchParams = new URLSearchParams(value); }
    get href() { return this.origin + this.pathname + this.search + this.hash; }
    set href(value) { this.setHref(String(value)); }
    toString() { return this.href; }
    toJSON() { return this.href; }
  };
}
""".trimIndent()
