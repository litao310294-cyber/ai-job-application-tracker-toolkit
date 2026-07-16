import { Adapter, SendMessage, OnMessage, Message } from 'comctx'

export class ProvideContentAdapter implements Adapter {
  sendMessage: SendMessage = (message) => {
    /**
     * Compatible with Firefox
     * https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts#cloneinto
     */
    const detail =
      // @ts-ignore
      typeof cloneInto === 'function' ? cloneInto(message, document.defaultView) : message

    document.dispatchEvent(new CustomEvent('message', { detail }))
  }
  onMessage: OnMessage = (callback) => {
    const handler = (event: Event) => {
      callback((event as CustomEvent<Partial<Message> | undefined>).detail)
    }
    document.addEventListener('message', handler)
    return () => document.removeEventListener('message', handler)
  }
}
