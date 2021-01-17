import { NativeModules } from 'react-native';

type CoreType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Core } = NativeModules;

export default Core as CoreType;
