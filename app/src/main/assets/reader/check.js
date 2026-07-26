const status = document.querySelector('#status')

try {
    const CFI = await import('../epubcfi.js')
    const sample = 'epubcfi(/6/2!/4/2:0)'
    CFI.parse(sample)
    if (CFI.compare(sample, sample) !== 0)
        throw new Error('CFI comparison failed')

    if (!new URLSearchParams(location.search).has('book')) {
        status.textContent = 'foliate-js loaded: EPUB CFI ready'
    } else {
        status.textContent = 'Opening EPUB…'
        const { makeBook } = await import('../view.js')
        const book = await makeBook('/book/selected.epub')
        document.body.dataset.readerStage = 'book-parsed'
        secureBookContent(book)

        const view = document.createElement('foliate-view')
        document.body.append(view)
        await view.open(book)
        document.body.dataset.readerStage = 'view-open'
        view.addEventListener('relocate', ({ detail }) => {
            document.body.dataset.readerStage = 'relocated'
            const percent = Number.isFinite(detail.fraction)
                ? `${Math.round(detail.fraction * 100)}%`
                : 'Reading'
            status.textContent = detail.cfi ? `${percent} · CFI ready` : percent
        })
        requestAnimationFrame(() => {
            Promise.resolve(view.renderer.next())
                .then(() => view.renderer.render())
                .catch(error => {
                    document.body.dataset.readerStage = 'failed'
                    status.textContent = `Reader check failed: ${error.message}`
                })
        })
        document.body.dataset.readerStage = 'initialized'
        status.textContent = ''
    }
} catch (error) {
    status.textContent = `Reader check failed: ${error.message}`
}

function secureBookContent(book) {
    const target = book.transformTarget
    if (!target) return

    target.addEventListener('load', event => {
        if (event.detail.isScript) event.detail.allow = false
    })
    target.addEventListener('data', event => {
        if (!['application/xhtml+xml', 'text/html', 'image/svg+xml']
            .includes(event.detail.type)) return
        event.detail.data = Promise.resolve(event.detail.data)
            .then(data => sanitizeDocument(data, event.detail.type))
    })
}

function sanitizeDocument(source, type) {
    const doc = new DOMParser().parseFromString(source, type)
    doc.querySelectorAll('script, iframe, object, embed, form, base')
        .forEach(element => element.remove())
    doc.querySelectorAll('meta[http-equiv]').forEach(element => {
        if (element.getAttribute('http-equiv').toLowerCase() === 'refresh')
            element.remove()
    })
    doc.querySelectorAll('*').forEach(element => {
        for (const attribute of [...element.attributes]) {
            const name = attribute.name.toLowerCase()
            const value = attribute.value.trim().toLowerCase()
            if (name.startsWith('on') || name === 'srcdoc'
                || value.startsWith('javascript:'))
                element.removeAttribute(attribute.name)
        }
    })

    const namespace = doc.documentElement.namespaceURI
    const head = doc.querySelector('head')
    if (head) {
        const policy = namespace
            ? doc.createElementNS(namespace, 'meta')
            : doc.createElement('meta')
        policy.setAttribute('http-equiv', 'Content-Security-Policy')
        policy.setAttribute(
            'content',
            "default-src 'none'; style-src 'unsafe-inline' blob:; " +
            "img-src blob: data:; font-src blob: data:; media-src blob: data:; " +
            "object-src 'none'; frame-src 'none'; form-action 'none'",
        )
        head.prepend(policy)
    }
    return new XMLSerializer().serializeToString(doc)
}
