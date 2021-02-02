import React, { Component, ReactElement } from 'react'
import {
  requireNativeComponent,
  findNodeHandle,
  NativeSyntheticEvent,
  Platform,
  StyleSheet,
  View,
} from 'react-native'

import { CameraManager } from './native'
import {
  CameraPermissionStatus,
  RecordAudioPermissionStatus,
  requestPermissions,
} from './permissions'
import {
  AndroidCameraManager,
  FpsRange,
  IOSCameraManager,
  OrientationNumber,
  ParsedCameraProps,
  ParsedRecordingOptions,
  PictureOptions,
  PictureSavedEvent,
  RecordingOptions,
  VideoRecordedEvent,
  Point,
} from './types'
import { RNCameraProps } from './types/internal'

interface State {
  isAuthorized: boolean
  isAuthorizationChecked: boolean
  recordAudioPermissionStatus: RecordAudioPermissionStatus
}

export default class Camera extends Component<RNCameraProps, State> {
  static defaultProps: Omit<
    RNCameraProps,
    'androidCameraPermissionOptions' | 'androidRecordAudioPermissionOptions'
  > = {
    plugins: [],
    zoom: 0,
    useNativeZoom: false,
    maxZoom: 0,
    ratio: '4:3',
    focusDepth: 0,
    type: 'back',
    autoFocus: true,
    flashMode: 'off',
    exposure: -1,
    whiteBalance: 'auto',
    captureAudio: true,
    keepAudioSession: false,
    useCamera2Api: false,
    playSoundOnCapture: false,
    playSoundOnRecord: false,
    pictureSize: 'None',
    videoStabilizationMode: 'off',
    mirrorVideo: false,
    onCameraViewId: () => null,
  }

  constructor(props: RNCameraProps) {
    super(props)
    this.mounted = true
    this.state = {
      isAuthorized: false,
      isAuthorizationChecked: false,
      recordAudioPermissionStatus:
        RecordAudioPermissionStatus.PENDING_AUTHORIZATION,
    }

    this.setReference = this.setReference.bind(this)
    this.onMountError = this.onMountError.bind(this)
    this.onCameraReady = this.onCameraReady.bind(this)
    this.onAudioInterrupted = this.onAudioInterrupted.bind(this)
    this.onAudioConnected = this.onAudioConnected.bind(this)
    this.onTouch = this.onTouch.bind(this)
    this.onPictureSaved = this.onPictureSaved.bind(this)
    this.onSubjectAreaChanged = this.onSubjectAreaChanged.bind(this)
  }

  render(): ReactElement | null {
    const { style, ...nativeProps } = this.convertNativeProps(this.props)

    if (this.state.isAuthorized) {
      return (
        <View style={style}>
          <RNCamera
            {...nativeProps}
            style={StyleSheet.absoluteFill}
            ref={this.setReference}
            onMountError={this.onMountError}
            onCameraReady={this.onCameraReady}
            onAudioInterrupted={this.onAudioInterrupted}
            onAudioConnected={this.onAudioConnected}
            onTouch={this.onTouch}
            onPictureSaved={this.onPictureSaved}
            onSubjectAreaChanged={this.onSubjectAreaChanged}
          />
        </View>
      )
    } else {
      return null
    }
  }

  async componentDidMount(): Promise<void> {
    const {
      hasCameraPermissions,
      recordAudioPermissionStatus,
    } = await this.arePermissionsGranted()
    if (this.mounted === false) {
      return
    }

    this.setState(
      {
        isAuthorized: hasCameraPermissions,
        isAuthorizationChecked: true,
        recordAudioPermissionStatus,
      },
      this.onStatusChanged
    )
  }

  componentWillUnmount(): void {
    this.mounted = false
  }

  async takePictureAsync(options?: PictureOptions): Promise<PictureSavedEvent> {
    if (!options) {
      options = {}
    }
    if (!options.quality) {
      options.quality = 1
    }

    if (options.orientation) {
      if (typeof options.orientation !== 'number') {
        const { orientation } = options
        options.orientation = CameraManager.Orientation[
          orientation
        ] as OrientationNumber
        if (__DEV__) {
          if (typeof options.orientation !== 'number') {
            // eslint-disable-next-line no-console
            console.warn(`Orientation '${orientation}' is invalid.`)
          }
        }
      }
    }

    if (options.pauseAfterCapture === undefined) {
      options.pauseAfterCapture = false
    }

    return await CameraManager.takePicture(options, this.ensureCameraHandle())
  }

  async getSupportedRatiosAsync(): Promise<string[]> {
    if (Platform.OS === 'android') {
      return await CameraManager.getSupportedRatios(this.ensureCameraHandle())
    } else {
      throw new Error('Ratio is not supported on iOS')
    }
  }

  async getCameraIdsAsync(): Promise<string[]> {
    // iOS does not need a camera instance
    if (Platform.OS === 'ios') {
      return await (CameraManager as IOSCameraManager).getIOSCameraIds()
    }

    return await CameraManager.getCameraIds(this.ensureCameraHandle())
  }

  getSupportedPreviewFpsRange = async (): Promise<FpsRange[]> => {
    if (Platform.OS === 'android') {
      return await (CameraManager as AndroidCameraManager).getSupportedPreviewFpsRange(
        this.ensureCameraHandle()
      )
    } else {
      throw new Error(
        'getSupportedPreviewFpsRange is only supported on Android'
      )
    }
  }

  async getAvailablePictureSizes(ratio?: string): Promise<string[]> {
    const ratioToGet = ratio ?? this.props.ratio

    if (!ratioToGet) {
      throw new Error(
        'To get availabe picture sizes you must define a ratio either passing it directly to getAvailablePictureSizes or as the ratio prop of the camera component'
      )
    }
    return await CameraManager.getAvailablePictureSizes(
      ratioToGet,
      this.ensureCameraHandle()
    )
  }

  getStatus(): CameraPermissionStatus {
    const { isAuthorized, isAuthorizationChecked } = this.state
    if (isAuthorizationChecked === false) {
      return CameraPermissionStatus.PENDING_AUTHORIZATION
    }
    return isAuthorized
      ? CameraPermissionStatus.READY
      : CameraPermissionStatus.NOT_AUTHORIZED
  }

  async recordAsync(options?: RecordingOptions): Promise<VideoRecordedEvent> {
    const parsedOptions = (options
      ? { ...options }
      : {}) as ParsedRecordingOptions

    if (typeof options?.quality === 'string') {
      parsedOptions.quality = CameraManager.VideoQuality[options.quality]
    }

    if (options?.orientation) {
      parsedOptions.orientation = CameraManager.Orientation[options.orientation]
      if (__DEV__) {
        if (typeof parsedOptions.orientation !== 'number') {
          // eslint-disable-next-line no-console
          console.warn(`Orientation '${options.orientation}' is invalid.`)
        }
      }
    }

    if (__DEV__) {
      if (options?.videoBitrate && typeof options.videoBitrate !== 'number') {
        // eslint-disable-next-line no-console
        console.warn('Video Bitrate should be a positive integer')
      }
    }

    const { recordAudioPermissionStatus } = this.state
    const { captureAudio } = this.props

    if (
      !captureAudio ||
      recordAudioPermissionStatus !== RecordAudioPermissionStatus.AUTHORIZED
    ) {
      parsedOptions.mute = true
    }

    if (__DEV__) {
      if (
        (!parsedOptions.mute || captureAudio) &&
        recordAudioPermissionStatus !== RecordAudioPermissionStatus.AUTHORIZED
      ) {
        // eslint-disable-next-line no-console
        console.warn(
          'Recording with audio not possible. Permissions are missing.'
        )
      }
    }

    return await CameraManager.record(parsedOptions, this.ensureCameraHandle())
  }

  stopRecording(): void {
    CameraManager.stopRecording(this.ensureCameraHandle())
  }

  pauseRecording(): void {
    CameraManager.pauseRecording(this.ensureCameraHandle())
  }

  resumeRecording(): void {
    CameraManager.resumeRecording(this.ensureCameraHandle())
  }

  pausePreview(): void {
    CameraManager.pausePreview(this.ensureCameraHandle())
  }

  isRecording(): boolean {
    return CameraManager.isRecording(this.ensureCameraHandle())
  }

  resumePreview(): void {
    CameraManager.resumePreview(this.ensureCameraHandle())
  }

  async arePermissionsGranted(): Promise<{
    hasCameraPermissions: boolean
    recordAudioPermissionStatus: RecordAudioPermissionStatus
  }> {
    const {
      hasCameraPermissions,
      hasRecordAudioPermissions,
    } = await requestPermissions(
      Boolean(this.props.captureAudio),
      this.props.androidCameraPermissionOptions,
      this.props.androidRecordAudioPermissionOptions
    )

    const recordAudioPermissionStatus = hasRecordAudioPermissions
      ? RecordAudioPermissionStatus.AUTHORIZED
      : RecordAudioPermissionStatus.NOT_AUTHORIZED
    return { hasCameraPermissions, recordAudioPermissionStatus }
  }

  async refreshAuthorizationStatus(): Promise<void> {
    const {
      hasCameraPermissions,
      recordAudioPermissionStatus,
    } = await this.arePermissionsGranted()
    if (this.mounted === false) {
      return
    }

    this.setState({
      isAuthorized: hasCameraPermissions,
      isAuthorizationChecked: true,
      recordAudioPermissionStatus,
    })
  }

  private cameraRef: number | undefined = undefined
  private cameraHandle: number | undefined = undefined
  private mounted: boolean

  private convertNativeProps({
    type,
    flashMode,
    autoFocus,
    whiteBalance,
    videoStabilizationMode,
    ...props
  }: RNCameraProps) {
    const parsedProps = ({ ...props } as unknown) as ParsedCameraProps

    if (type) {
      parsedProps.type = CameraManager.Type[type]
    }
    if (flashMode) {
      parsedProps.flashMode = CameraManager.FlashMode[flashMode]
    }
    if (autoFocus != null) {
      parsedProps.autoFocus = autoFocus
        ? CameraManager.AutoFocus.on
        : CameraManager.AutoFocus.off
    }
    if (whiteBalance) {
      if (typeof whiteBalance === 'string') {
        parsedProps.whiteBalance = CameraManager.WhiteBalance[whiteBalance]
      } else {
        parsedProps.whiteBalance = whiteBalance
      }
    }
    if (videoStabilizationMode && CameraManager.VideoStabilizationModes) {
      parsedProps.videoStabilizationMode =
        CameraManager.VideoStabilizationModes[videoStabilizationMode]
    }

    parsedProps.touchDetectorEnabled = Boolean(props.onTap || props.onDoubleTap)

    if (Platform.OS === 'ios') {
      delete parsedProps.ratio
    }

    return parsedProps
  }

  private setReference(ref: number) {
    if (ref) {
      this.cameraRef = ref
      this.cameraHandle = findNodeHandle(ref) ?? undefined
      if (this.cameraHandle) {
        this.props.onCameraViewId(this.cameraHandle)
      }
    } else {
      this.cameraRef = undefined
      this.cameraHandle = undefined
    }
  }

  private onMountError({
    nativeEvent,
  }: NativeSyntheticEvent<{ error: string }>) {
    this.props?.onMountError?.(nativeEvent.error)
  }

  private onCameraReady() {
    this.props?.onCameraReady?.()
  }

  private onAudioInterrupted() {
    this.props?.onAudioInterrupted?.()
  }

  private onAudioConnected() {
    this.props?.onAudioConnected?.()
  }

  private onTouch({
    nativeEvent,
  }: NativeSyntheticEvent<{ isDoubleTap: boolean; touchOrigin: Point }>) {
    if (this.props.onTap && !nativeEvent.isDoubleTap) {
      this.props.onTap(nativeEvent.touchOrigin)
    }
    if (this.props.onDoubleTap && nativeEvent.isDoubleTap) {
      this.props.onDoubleTap(nativeEvent.touchOrigin)
    }
  }

  private onSubjectAreaChanged({
    nativeEvent,
  }: NativeSyntheticEvent<{ prevPointOfInterest: Point }>) {
    this.props?.onSubjectAreaChanged?.({
      prevPoint: nativeEvent.prevPointOfInterest,
    })
  }

  private onPictureSaved({
    nativeEvent,
  }: NativeSyntheticEvent<PictureSavedEvent>) {
    this.props?.onPictureSaved?.(nativeEvent)
  }

  private onStatusChanged() {
    this.props?.onStatusChange?.({
      cameraStatus: this.getStatus(),
      recordAudioPermissionStatus: this.state.recordAudioPermissionStatus,
    })
  }

  private ensureCameraHandle(): number {
    if (!this.cameraHandle) {
      throw new Error(
        'Camera handle not found, cannot associate to native view instance'
      )
    }

    return this.cameraHandle
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const RNCamera = (requireNativeComponent as any)('RNCamera', Camera, {
  nativeOnly: {
    accessibilityComponentType: true,
    accessibilityLabel: true,
    accessibilityLiveRegion: true,
    barCodeScannerEnabled: true,
    touchDetectorEnabled: true,
    googleVisionBarcodeDetectorEnabled: true,
    faceDetectorEnabled: true,
    textRecognizerEnabled: true,
    importantForAccessibility: true,
    onBarCodeRead: true,
    onGoogleVisionBarcodesDetected: true,
    onCameraReady: true,
    onAudioInterrupted: true,
    onAudioConnected: true,
    onPictureSaved: true,
    onFaceDetected: true,
    onTouch: true,
    onLayout: true,
    onMountError: true,
    onSubjectAreaChanged: true,
    renderToHardwareTextureAndroid: true,
    testID: true,
  },
})
