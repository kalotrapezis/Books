/**
 * PDF.js 5 is built for a newer engine than the Android WebView ships: it uses the
 * ES2025 `Uint8Array` hex/base64 helpers and the upsert `Map`/`WeakMap` methods. Only
 * PDF loads this, and only the missing pieces are filled in, so a WebView that already
 * has them keeps its own. Delete a block once the shipping WebView provides it.
 */
if (!Uint8Array.prototype.toHex) {
    Uint8Array.prototype.toHex = function () {
        return Array.from(this, byte => byte.toString(16).padStart(2, '0')).join('')
    }
}
if (!Uint8Array.fromHex) {
    Uint8Array.fromHex = hex => new Uint8Array(
        (hex.match(/../g) ?? []).map(pair => parseInt(pair, 16)))
}
if (!Uint8Array.prototype.toBase64) {
    Uint8Array.prototype.toBase64 = function () {
        return btoa(Array.from(this, byte => String.fromCharCode(byte)).join(''))
    }
}
if (!Uint8Array.fromBase64) {
    Uint8Array.fromBase64 = value =>
        Uint8Array.from(atob(value), character => character.charCodeAt(0))
}

for (const Collection of [Map, WeakMap]) {
    if (!Collection.prototype.getOrInsert) {
        Collection.prototype.getOrInsert = function (key, value) {
            if (!this.has(key)) this.set(key, value)
            return this.get(key)
        }
    }
    if (!Collection.prototype.getOrInsertComputed) {
        Collection.prototype.getOrInsertComputed = function (key, compute) {
            if (!this.has(key)) this.set(key, compute(key))
            return this.get(key)
        }
    }
}
