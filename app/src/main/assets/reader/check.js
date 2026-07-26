const status = document.querySelector('#status')

try {
    const CFI = await import('../epubcfi.js')
    const sample = 'epubcfi(/6/2!/4/2:0)'
    CFI.parse(sample)
    if (CFI.compare(sample, sample) !== 0)
        throw new Error('CFI comparison failed')
    status.textContent = 'foliate-js loaded: EPUB CFI ready'
} catch (error) {
    status.textContent = `Reader check failed: ${error.message}`
}
