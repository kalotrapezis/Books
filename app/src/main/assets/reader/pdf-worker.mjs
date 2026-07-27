// The PDF.js worker has its own global, so the reader page cannot polyfill it: the page
// points `workerSrc` here instead, and this shim patches the worker's own global first.
// Dynamic imports, not static: a static import would run the worker before the fills.
await import('./polyfills.mjs')
await import('../vendor/pdfjs/pdf.worker.mjs')
