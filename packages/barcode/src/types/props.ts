import { BarcodeOptions } from './options'
import { BarcodeReadResult } from './read'

export interface BarcodePluginProps extends BarcodeOptions {
  onBarCodeRead: (result: BarcodeReadResult, resume: () => void) => void
}
