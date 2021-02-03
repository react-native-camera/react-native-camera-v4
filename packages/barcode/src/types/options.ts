import { Rect } from '@react-native-camera/core'

import { BarCodeType } from './barcode'

export interface BarcodeOptions {
  barcodeTypes: BarCodeType[]
  rectOfInterest?: Rect
  disabled?: boolean
}
