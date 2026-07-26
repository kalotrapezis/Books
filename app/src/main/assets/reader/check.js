const status = document.querySelector('#status')
let CFI

const send = message => globalThis.booksBridge?.postMessage(JSON.stringify(message))
const text = value => typeof value === 'string'
    ? value.replace(/\s+/g, ' ').trim().slice(0, 512)
    : ''
const languageMap = value => typeof value === 'string'
    ? value : typeof value === 'object' && value
        ? Object.values(value).find(item => typeof item === 'string') ?? ''
        : ''
const contributor = value => Array.isArray(value)
    ? value.map(contributor).filter(Boolean).join(', ')
    : text(typeof value === 'string' ? value : languageMap(value?.name))
const validCfi = cfi => typeof cfi === 'string' && cfi.startsWith('epubcfi(')
    && cfi.length <= 8192 && (() => {
        try {
            CFI.parse(cfi)
            return true
        } catch {
            return false
        }
    })()

try {
    CFI = await import('../epubcfi.js')
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
        const { cfi: lastLocation } = await fetch('/state/last-location.json')
            .then(response => response.json())
        const restoreLocation = validCfi(lastLocation) ? lastLocation : null
        let restoring = Boolean(restoreLocation)

        const view = document.createElement('foliate-view')
        document.body.append(view)
        await view.open(book)
        send({
            type: 'BookReady',
            title: text(languageMap(book.metadata?.title)) || 'Untitled book',
            author: contributor(book.metadata?.author),
            identifier: text(book.metadata?.identifier),
        })
        document.body.dataset.readerStage = 'view-open'
        view.addEventListener('relocate', ({ detail }) => {
            if (restoring) {
                if (!validCfi(detail.cfi)
                    || CFI.compare(detail.cfi, restoreLocation) !== 0) return
                restoring = false
            }
            publishRelocation(detail)
        })
        const publishRelocation = detail => {
            document.body.dataset.readerStage = 'relocated'
            const percent = Number.isFinite(detail.fraction)
                ? `${Math.round(detail.fraction * 100)}%`
                : 'Reading'
            status.textContent = detail.cfi ? `${percent} · CFI ready` : percent
            if (validCfi(detail.cfi)) {
                const message = { type: 'Relocated', cfi: detail.cfi }
                const section = detail.section
                const fraction = Number.isFinite(detail.fraction)
                    ? detail.fraction
                    : Number.isFinite(section?.current) && Number.isFinite(section?.total)
                        && section.total > 0
                        ? (section.current + 1) / section.total
                        : null
                if (Number.isFinite(fraction)) message.fraction = fraction
                send(message)
            }
        }
        let commandQueue = Promise.resolve()
        if (globalThis.booksBridge) globalThis.booksBridge.onmessage = event => {
            let command
            try {
                command = JSON.parse(event.data)
            } catch {
                return
            }
            if (!command || typeof command !== 'object') return
            if (command.type === 'SetFlow'
                && Object.keys(command).length === 2
                && ['paginated', 'scrolled'].includes(command.flow)) {
                view.renderer.setAttribute('flow', command.flow)
                return
            }
            if (Object.keys(command).length !== 1
                || !['Previous', 'Next'].includes(command.type)) return
            commandQueue = commandQueue.then(
                () => navigate(view, command.type),
                () => navigate(view, command.type),
            ).catch(showReaderError)
        }
        document.body.dataset.readerStage = 'waiting-for-layout'
        const layoutObserver = new ResizeObserver(() => {
            if (view.renderer.getBoundingClientRect().height <= 0) return
            layoutObserver.disconnect()
            document.body.dataset.readerStage = 'starting'
            let attempts = 0
            const contentTimer = setInterval(() => {
                if (view.renderer.getContents().length) {
                    clearInterval(contentTimer)
                    try {
                        view.renderer.render()
                    } catch (error) {
                        showReaderError(error)
                    }
                } else if (++attempts >= 200) {
                    clearInterval(contentTimer)
                    showReaderError(new Error('Reader section did not load'))
                }
            }, 25)
            Promise.resolve(restoreLocation
                ? view.init({ lastLocation: restoreLocation })
                : view.renderer.firstSection())
                .then(() => {
                    if (!restoring) return
                    restoring = false
                    if (view.lastLocation) publishRelocation(view.lastLocation)
                })
                .catch(showReaderError)
        })
        layoutObserver.observe(view.renderer)
        status.textContent = ''
    }
} catch (error) {
    showReaderError(error)
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

function showReaderError(error) {
    document.body.dataset.readerStage = 'failed'
    const message = text(error?.message) || 'Reader error'
    status.textContent = `Reader check failed: ${message}`
    send({ type: 'ReaderError', message })
}

function navigate(view, command) {
    const renderer = view.renderer
    const previous = command === 'Previous'
    const atSectionEdge = previous
        ? renderer.page <= 1
        : renderer.page >= renderer.pages - 2
    if (!atSectionEdge) return previous ? view.prev() : view.next()

    const current = renderer.getContents()[0]?.index
    if (!Number.isInteger(current)) return Promise.resolve()
    const step = previous ? -1 : 1
    let index = current + step
    while (index >= 0 && index < view.book.sections.length
        && view.book.sections[index]?.linear === 'no') index += step
    if (index < 0 || index >= view.book.sections.length)
        return Promise.resolve()

    return new Promise((resolve, reject) => {
        let settled = false
        const onRelocate = ({ detail }) => {
            if (detail.section?.current === index) finish(resolve)
        }
        const renderTimer = setInterval(() => renderer.render(), 50)
        const timeout = setTimeout(
            () => finish(reject, new Error('Reader navigation timed out')),
            5000,
        )
        const finish = (complete, value) => {
            if (settled) return
            settled = true
            clearInterval(renderTimer)
            clearTimeout(timeout)
            view.removeEventListener('relocate', onRelocate)
            complete(value)
        }
        view.addEventListener('relocate', onRelocate)
        renderer.goTo({ index, anchor: () => previous ? 1 : 0 })
            .catch(error => finish(reject, error))
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
