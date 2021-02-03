import { Point } from '@react-native-camera/core'

import { BarCodeType } from './barcode'

export interface BarcodeReadResult {
  viewId: number
  data: string
  rawData?: string
  type: BarCodeType
  bounds: {
    origin: Point[]
    width: number
    height: number
  }
}
