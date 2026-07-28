const status = document.querySelector('#status')
let CFI
let selectable = false
let theme = null
/** value → { annotation, index }, redrawn whenever a section gets a fresh overlayer. */
const annotations = new Map()
let typography = null
/** Fixed-layout zoom: null means fit the whole page, a number is a pinch scale. */
let zoom = null

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

    const bookFile = new URLSearchParams(location.search).get('book')
    if (!bookFile) {
        status.textContent = 'foliate-js loaded: EPUB CFI ready'
    } else if (!/^selected\.[a-z0-9]{2,4}$/.test(bookFile)) {
        throw new Error('Unsupported book file')
    } else {
        status.textContent = 'Opening book…'
        if (bookFile.endsWith('.pdf')) {
            // Import pdf.js first so its worker can be pointed at our polyfilled shim.
            await import('./polyfills.mjs')
            await import('../pdf.js')
            globalThis.pdfjsLib.GlobalWorkerOptions.workerSrc =
                new URL('./pdf-worker.mjs', import.meta.url).toString()
        }
        const { makeBook } = await import('../view.js')
        const { Overlayer } = await import('../overlayer.js')
        const book = await makeBook(`/book/${bookFile}`)
        document.body.dataset.readerStage = 'book-parsed'
        secureBookContent(book)
        const { cfi: lastLocation } = await fetch('/state/last-location.json')
            .then(response => response.json())
        const restoreLocation = validCfi(lastLocation) ? lastLocation : null
        let restoring = Boolean(restoreLocation)

        const view = document.createElement('foliate-view')
        document.body.append(view)
        await view.open(book)
        // foliate slides the columns over 300 ms when this attribute is present.
        view.renderer.setAttribute('animated', '')
        setSelectable(true)
        // Draw saved highlights with foliate's own overlayer.
        view.addEventListener('draw-annotation', ({ detail }) => {
            const { draw, annotation } = detail
            draw(Overlayer.highlight, { color: annotation.color || 'yellow' })
        })
        // foliate-js draws an annotation only if its section is rendered right now, and
        // keeps no list of its own: without this, highlights vanish the moment you leave
        // the section, and an import never shows up outside the open one.
        view.addEventListener('create-overlay', ({ detail }) => {
            for (const { annotation, index } of annotations.values())
                if (index === detail.index) view.addAnnotation(annotation)
        })
        view.addEventListener('show-annotation', ({ detail }) =>
            send({ type: 'AnnotationTapped', cfi: detail.value }))
        send({
            type: 'BookReady',
            // Blank means "no title in the file": the app falls back to the file name.
            // Comics take their title from the path we served them under, which is not
            // one, so that is blanked too.
            title: text(languageMap(book.metadata?.title)).replace(/^\/book\/.*/, ''),
            author: contributor(book.metadata?.author),
            identifier: text(book.metadata?.identifier),
            toc: flattenToc(book.toc),
            // Comics and PDF: their renderer ignores the flow setting, so the app keeps
            // the paginated controls for them instead of the scrolled ones.
            fixedLayout: Boolean(view.isFixedLayout),
        })
        document.body.dataset.readerStage = 'view-open'
        // A tap anywhere on the page toggles the native UI; a horizontal drag
        // turns the page. Links and text selections are left alone.
        view.addEventListener('load', ({ detail }) => {
            const doc = detail?.doc
            if (!doc) return
            let start = null
            let swiped = false
            let pinchStart = null
            const spread = event => Math.hypot(
                event.touches[0].clientX - event.touches[1].clientX,
                event.touches[0].clientY - event.touches[1].clientY)
            doc.addEventListener('touchstart', event => {
                // Two fingers on a PDF or comic zoom it. Reflowable books have the
                // text size setting instead, so their gestures are left alone.
                if (view.isFixedLayout && event.touches.length === 2) {
                    pinchStart = { distance: spread(event), zoom }
                    start = null
                    return
                }
                const touch = event.touches[0]
                start = touch ? { x: touch.clientX, y: touch.clientY } : null
                swiped = false
            }, { passive: true })
            doc.addEventListener('touchmove', event => {
                if (!pinchStart || event.touches.length !== 2) return
                const factor = spread(event) / (pinchStart.distance || 1)
                setZoom(pinchStart.zoom * factor)
            }, { passive: true })
            // Back to a whole page, so a zoomed-in reader is never stuck panning.
            doc.addEventListener('dblclick', () => setZoom(null))
            doc.addEventListener('touchend', event => {
                if (pinchStart) {
                    if (event.touches.length === 0) pinchStart = null
                    return
                }
                const touch = event.changedTouches[0]
                if (!start || !touch) return
                const dx = touch.clientX - start.x
                const dy = touch.clientY - start.y
                start = null
                // Scrolled mode owns vertical dragging, but a section ends where its
                // document ends: one more flick past the edge loads the next one.
                if (view.renderer.getAttribute('flow') === 'scrolled') {
                    if (Math.abs(dy) < 60 || Math.abs(dy) < Math.abs(dx)) return
                    const renderer = view.renderer
                    const atBottom = renderer.viewSize - renderer.end <= 2
                    const atTop = renderer.start <= 2
                    if (dy < 0 && atBottom) send({ type: 'TappedNext' })
                    else if (dy > 0 && atTop) send({ type: 'TappedPrevious' })
                    return
                }
                if (Math.abs(dx) < 60 || Math.abs(dx) < Math.abs(dy) * 1.5) return
                // Zoomed in, a drag pans the page; double tap to fit it and turn again.
                if (zoom !== null) return
                // A page with no text at all (comics, PDF images) has no selection
                // object; that is not an open selection, it is nothing to protect.
                if (doc.defaultView?.getSelection()?.isCollapsed === false) return
                swiped = true
                send({ type: dx > 0 ? 'TappedPrevious' : 'TappedNext' })
            }, { passive: true })
            doc.addEventListener('selectionchange', () => {
                const selection = doc.defaultView?.getSelection()
                if (!selection || selection.isCollapsed) return
                const range = selection.getRangeAt(0)
                const cfi = view.getCFI(detail.index, range)
                if (!validCfi(cfi)) return
                // Roughly where on the page the selection sits, so the native panel
                // can dock on the opposite side and never cover it.
                const rect = range.getBoundingClientRect()
                const height = doc.documentElement.clientHeight || 1
                send({
                    type: 'Selected',
                    cfi,
                    text: text(selection.toString()),
                    lower: (rect.top + rect.height / 2) / height > 0.5,
                })
            })
            doc.addEventListener('click', event => {
                if (swiped) {
                    swiped = false
                    return
                }
                if (event.target?.closest?.('a')) return
                // A page with no text at all (comics, PDF images) has no selection
                // object; that is not an open selection, it is nothing to protect.
                if (doc.defaultView?.getSelection()?.isCollapsed === false) return
                send({ type: 'Tapped' })
            })
        })
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
            status.hidden = true
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
                const printPage = text(detail.pageItem?.label)
                if (printPage) message.printPage = printPage
                const chapter = text(detail.tocItem?.label)
                if (chapter) message.chapter = chapter
                const { current, total } = detail.location ?? {}
                if (Number.isFinite(current) && Number.isFinite(total) && total > 0) {
                    message.page = current
                    message.pages = total
                }
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
            if (command.type === 'GoToFraction'
                && Object.keys(command).length === 2
                && Number.isFinite(command.fraction)) {
                commandQueue = commandQueue
                    .then(() => view.goToFraction(
                        Math.min(1, Math.max(0, command.fraction)),
                    ))
                    .catch(showReaderError)
                return
            }
            if (command.type === 'SetTheme'
                && Object.keys(command).length === 5
                && /^#[0-9a-f]{6}$/i.test(command.foreground ?? '')
                && /^#[0-9a-f]{6}$/i.test(command.background ?? '')
                && /^#[0-9a-f]{6}$/i.test(command.link ?? '')
                && typeof command.keepColors === 'boolean') {
                setTheme(
                    command.foreground,
                    command.background,
                    command.link,
                    command.keepColors,
                )
                return
            }
            if (command.type === 'SetTypography'
                && Object.keys(command).length === 5
                && Number.isFinite(command.fontScale)
                && Number.isFinite(command.lineHeight)
                && Number.isFinite(command.margin)
                && ['book', 'serif', 'sans'].includes(command.font)) {
                setTypography({
                    fontScale: Math.min(220, Math.max(70, command.fontScale)),
                    lineHeight: Math.min(2.4, Math.max(1, command.lineHeight)),
                    margin: Math.min(96, Math.max(0, command.margin)),
                    font: command.font,
                })
                return
            }
            if (command.type === 'Annotate'
                && Object.keys(command).length === 3
                && validCfi(command.cfi)
                && /^[a-z]{3,12}$/.test(command.color ?? '')) {
                const annotation = { value: command.cfi, color: command.color }
                commandQueue = commandQueue
                    .then(() => view.addAnnotation(annotation))
                    .then(result => annotations.set(
                        annotation.value,
                        { annotation, index: result?.index },
                    ))
                    .catch(showReaderError)
                return
            }
            if (command.type === 'Unannotate'
                && Object.keys(command).length === 2
                && validCfi(command.cfi)) {
                commandQueue = commandQueue
                    .then(() => view.deleteAnnotation({ value: command.cfi }))
                    .then(() => annotations.delete(command.cfi))
                    .catch(showReaderError)
                return
            }
            if (command.type === 'ClearSelection'
                && Object.keys(command).length === 1) {
                for (const { doc } of view.renderer.getContents())
                    doc?.defaultView?.getSelection()?.removeAllRanges()
                return
            }
            if (command.type === 'SetSelectable'
                && Object.keys(command).length === 2
                && typeof command.enabled === 'boolean') {
                setSelectable(command.enabled)
                return
            }
            // Bookmarks are stored as bare CFI, the way Foliate stores them, so the
            // chapter each one sits in has to be resolved here, off the book itself.
            if (command.type === 'DescribeCfis'
                && Object.keys(command).length === 2
                && Array.isArray(command.cfis)
                && command.cfis.length <= 500
                && command.cfis.every(validCfi)) {
                const total = view.book?.sections?.length ?? 0
                commandQueue = commandQueue
                    .then(async () => {
                        const described = []
                        for (const cfi of command.cfis) {
                            const chapter = text((await view.getTOCItemOf(cfi))?.label)
                            const { index, anchor } = view.resolveCFI(cfi) ?? {}
                            described.push({
                                cfi,
                                chapter,
                                section: Number.isInteger(index) ? index + 1 : null,
                                sections: total,
                                // Two bookmarks in one chapter look alike without the
                                // words they sit on.
                                excerpt: await excerpt(view, index, anchor),
                            })
                        }
                        send({ type: 'CfisDescribed', described })
                    })
                    .catch(showReaderError)
                return
            }
            if (command.type === 'GoToHref'
                && Object.keys(command).length === 2
                && typeof command.href === 'string' && command.href.length <= 2048) {
                commandQueue = commandQueue
                    .then(() => view.goTo(command.href))
                    .catch(showReaderError)
                return
            }
            if (command.type === 'GoToCfi'
                && Object.keys(command).length === 2
                && validCfi(command.cfi)) {
                commandQueue = commandQueue
                    .then(() => view.goTo(command.cfi))
                    .catch(showReaderError)
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
                        // The fixed-layout renderer (PDF, comics) lays itself out.
                        view.renderer.render?.()
                    } catch (error) {
                        showReaderError(error)
                    }
                } else if (++attempts >= 200) {
                    clearInterval(contentTimer)
                    showReaderError(new Error('Reader section did not load'))
                }
            }, 25)
            // init() handles both cases and works on either renderer: with no saved
            // location it simply opens the first page.
            Promise.resolve(view.init({ lastLocation: restoreLocation }))
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

/**
 * The words a CFI points at, for a bookmark list that reads like the book.
 * ponytail: loads the section document per bookmark; cache by section if a library
 * of hundreds of bookmarks in one book ever makes opening the list slow.
 */
async function excerpt(view, index, anchor) {
    if (!Number.isInteger(index) || typeof anchor !== 'function') return ''
    try {
        const doc = await view.book.sections[index].createDocument()
        const found = anchor(doc)
        const range = found instanceof Range ? found : doc.createRange()
        if (!(found instanceof Range) && found) range.selectNodeContents(found)
        // A bookmark is a point, not a span, so read on from where it sits.
        const after = doc.createRange()
        after.setStart(range.startContainer, range.startOffset)
        after.setEnd(doc.body, doc.body.childNodes.length)
        return text(after.toString()).slice(0, 120)
    } catch {
        return ''
    }
}

/** Pinch zoom for PDF and comics; foliate re-renders PDF pages at the new scale. */
function setZoom(value) {
    zoom = value === null ? null : Math.min(6, Math.max(0.5, value))
    document.querySelector('foliate-view')?.renderer
        ?.setAttribute('zoom', zoom ?? 'fit-page')
}

/** Reading mode swallows selection so a stray touch never interrupts the page. */
function setSelectable(enabled) {
    selectable = enabled
    applyStyles()
}

function setTheme(foreground, background, link, keepColors) {
    theme = { foreground, background, link, keepColors }
    document.body.style.background = background
    applyStyles()
}

/** Font size, line height, margins and family, all optional overrides. */
function setTypography(settings) {
    typography = settings
    const view = document.querySelector('foliate-view')
    if (view?.renderer) {
        view.renderer.setAttribute('margin', `${settings.margin}px`)
    }
    applyStyles()
}

const FONT_STACKS = {
    serif: 'Georgia, "Noto Serif", serif',
    sans: '"Noto Sans", system-ui, sans-serif',
}

const isDark = hex => {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16))
    return (r * 0.299 + g * 0.587 + b * 0.114) < 128
}

const invert = hex => '#' + [1, 3, 5]
    .map(i => (255 - parseInt(hex.slice(i, i + 2), 16)).toString(16).padStart(2, '0'))
    .join('')

/**
 * Grayscale keeps the book's own distinctions as different greys. The filter goes on
 * `body`, not `html`: filtering the root inverted the page margins too and left a light
 * frame around the text. `html` keeps the theme colour, the body paints nothing of its
 * own, and images are inverted back so they stay themselves.
 */
function greyFilterCss(theme) {
    const dark = isDark(theme.background)
    return `html { background: ${theme.background} !important }`
        + ` body { background: transparent !important;`
        + ` filter: grayscale(1)${dark ? ' invert(1)' : ''} }`
        + (dark
            ? ' img, picture, video, canvas { filter: invert(1) grayscale(0) !important }'
            : '')
}

function applyStyles() {
    const css = [
        '* { scrollbar-width: none !important }'
        + ' ::-webkit-scrollbar { width: 0 !important; height: 0 !important }',
        selectable ? '' : '*, *::before, *::after { -webkit-user-select: none !important;'
            + ' user-select: none !important }',
        theme ? `a, a:link, a:visited { color: ${theme.link} !important }` : '',
        // Two ways to theme the page: flatten every colour to the theme's ink, or keep
        // the book's own colours and let a filter turn them into greys of the theme.
        !theme ? ''
            : theme.keepColors ? greyFilterCss(theme)
            : `html, body { background: ${theme.background} !important;`
                + ` color: ${theme.foreground} !important }`
                + ` p, div, span, li, td, h1, h2, h3, h4, h5, h6, blockquote`
                + ` { color: ${theme.foreground} !important }`,
    ].join('\n')
    document.querySelector('foliate-view')?.renderer?.setStyles?.(css)
}

/** Flat TOC: label, href and depth, bounded so a hostile book cannot flood the bridge. */
function flattenToc(items, depth = 0, out = []) {
    for (const item of items ?? []) {
        if (out.length >= 500) break
        const href = typeof item?.href === 'string' ? item.href.slice(0, 2048) : ''
        if (href) out.push({ label: text(item.label) || 'Untitled', href, depth })
        if (item?.subitems) flattenToc(item.subitems, depth + 1, out)
    }
    return out
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
