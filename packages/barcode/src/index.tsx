import { NativeModules } from 'react-native';

type BarcodeType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Barcode } = NativeModules;

export default Barcode as BarcodeType;
