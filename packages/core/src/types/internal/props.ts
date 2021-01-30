import { CameraProps } from '../props'

export interface InternalCameraProps {
  plugins: string[]
  onCameraViewId: (viewId: number) => void
}

export type RNCameraProps = CameraProps & InternalCameraProps
