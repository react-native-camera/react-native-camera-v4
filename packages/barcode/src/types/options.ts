import { Rect } from '@react-native-camera/core'

import { BarCodeTypes } from './barcode'

export interface BarcodeOptions {
  barcodeTypes: BarCodeTypes[]
  rectOfInterest?: Rect
}
