import { BarcodeOptions } from './options'

export interface BarcodeNativeModule {
  setOptions(viewId: number, options: BarcodeOptions): void
}
