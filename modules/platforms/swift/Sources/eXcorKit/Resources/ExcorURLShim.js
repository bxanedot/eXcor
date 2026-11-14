if (typeof URLSearchParams === "undefined") {
  globalThis.URLSearchParams = class URLSearchParams {
    constructor(query = "") {
      this.items = [];
      for (const part of String(query).replace(/^\?/, "").split("&")) {
        if (!part) continue;
        const [key, ...rest] = part.split("=");
        this.items.push([decodeURIComponent(key.replace(/\+/g, " ")), decodeURIComponent(rest.join("=").replace(/\+/g, " "))]);
      }
    }
    append(key, value) { this.items.push([String(key), String(value)]); }
    delete(key) { this.items = this.items.filter(pair => pair[0] !== String(key)); }
    get(key) { return this.items.find(pair => pair[0] === String(key))?.[1] ?? null; }
    getAll(key) { return this.items.filter(pair => pair[0] === String(key)).map(pair => pair[1]); }
    has(key) { return this.items.some(pair => pair[0] === String(key)); }
    set(key, value) {
      key = String(key); value = String(value);
      const index = this.items.findIndex(pair => pair[0] === key);
      this.delete(key);
      this.items.splice(index < 0 ? this.items.length : index, 0, [key, value]);
    }
    toString() { return this.items.map(pair => encodeURIComponent(pair[0]) + "=" + encodeURIComponent(pair[1])).join("&"); }
  };
}

if (typeof URL === "undefined") {
  globalThis.URL = class URL {
    constructor(input, base) { this.assign(String(input), base); }
    assign(input, base) {
      if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(input)) {
        if (!base) throw new TypeError("Invalid URL");
        const parent = new URL(base);
        input = input.startsWith("/") ? parent.origin + input : parent.origin + parent.pathname.slice(0, parent.pathname.lastIndexOf("/") + 1) + input;
      }
      const match = /^([a-z][a-z0-9+.-]*:)\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/i.exec(input);
      if (!match) throw new TypeError("Invalid URL");
      this.protocol = match[1];
      this.host = match[2];
      this.hostname = this.host.replace(/:\d+$/, "");
      this.port = this.host.match(/:(\d+)$/)?.[1] ?? "";
      this.username = "";
      this.password = "";
      this.origin = this.protocol + "//" + this.host;
      this.pathname = match[3] || "/";
      this.searchParams = new URLSearchParams(match[4] ?? "");
      this.hash = match[5] ?? "";
    }
    get search() { const query = this.searchParams.toString(); return query ? "?" + query : ""; }
    set search(value) { this.searchParams = new URLSearchParams(value); }
    get href() { return this.origin + this.pathname + this.search + this.hash; }
    set href(value) { this.assign(String(value)); }
    toString() { return this.href; }
    toJSON() { return this.href; }
  };
}
