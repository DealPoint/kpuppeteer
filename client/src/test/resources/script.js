console.log('script start')

const waitForRender = callback =>
  window.requestAnimationFrame(() =>
    window.requestAnimationFrame(callback))

window.onload = () => {
  console.log('DOM ready')
  const ts = document.getElementById('CurrentTimestamp')
  ts.innerText = `${new Date()}`
  console.log('text updated')
  waitForRender(() => console.log('__renderComplete__'))
}
