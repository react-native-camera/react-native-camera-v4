import Foundation
import AVFoundation

protocol RNCameraDelegate {
  func onTouch(data: NSDictionary)
}

class RNCamera : UIView, SensorOrientationCheckerDelegate {
  
  // MARK: Immutable Variables
  let sessionQueue = dispatch_queue_serial_t(label: "cameraQueue")
  let session = AVCaptureSession()
  let sensorOrientationChecker = SensorOrientationChecker()

  
  // MARK: Mutable Variables
  var delegate: RNCameraDelegate?
  var videoCaptureDeviceInput: AVCaptureDeviceInput?
  var audioCaptureDeviceInput: AVCaptureDeviceInput?
  var previewLayer: AVCaptureVideoPreviewLayer
  var movieFileOutput: AVCaptureMovieFileOutput?
  var stillImageOutput: AVCaptureStillImageOutput?
  var pictureSize: AVCaptureSession.Preset?
  var isFocusedOnPoint = false
  var isExposedOnPoint = false
  var invertImageData = true
  var isRecordingInterrupted = false
  var keepAudioSession = false
  var captureAudio = false
  
  
  // MARK: Internal Variables
  private var recordRequested = false
  private var sessionInterrupted = false
  private var exposureIsoMin: Float?
  private var exposureIsoMax: Float?
  private var maxZoom: CGFloat = 0
  private var zoom: CGFloat = 0
  private var cameraId: String?
  private var autoFocus: AVCaptureDevice.FocusMode = .continuousAutoFocus
  private var autoExposure = false
  private var presetCamera = AVCaptureDevice.Position.unspecified
  private var flashMode: AVCaptureDevice.FlashMode = AVCaptureDevice.FlashMode.off
  private var exposure: Float = -1
  private var autoFocusPointOfInterest: CGPoint?
  private var whiteBalance: AVCaptureDevice.WhiteBalanceMode?
  private var customWhiteBalance: WhiteBalanceSettings?
  private var nativeZoom = false
  private var pinchGestureRecognizer: UIPinchGestureRecognizer?
  private var focusDepth: Float?
  
  
  // MARK: React Events
  @objc var onCameraReady: RCTDirectEventBlock?
  @objc var onMountError: RCTDirectEventBlock?
  @objc var onTouch: RCTDirectEventBlock?
  @objc var onAudioConnected: RCTDirectEventBlock?
  @objc var onAudioInterrupted: RCTDirectEventBlock?
  @objc var onSubjectAreaChanged: RCTDirectEventBlock?
  
  
  // MARK: Computed Properties
  var isRecording: Bool {
    get { return movieFileOutput?.isRecording ?? false }
  }
  
  
  // MARK: Constructors
  required init?(coder: NSCoder) {
    super.init(coder: coder)
    initialize()
  }
  
  override init(frame: CGRect) {
    super.init(frame: frame)
    initialize()
  }
  
  
  // MARK: View Lifecycle
  override func layoutSubviews() {
    super.layoutSubviews()
    previewLayer.frame = bounds
    backgroundColor = UIColor.black
    layer.insertSublayer(previewLayer, at: 0)
  }
  
  override func insertReactSubview(_ subview: UIView!, at atIndex: Int) {
    insertSubview(subview, at: atIndex + 1)
    super.insertReactSubview(subview, at: atIndex)
  }
  
  override func removeReactSubview(_ subview: UIView!) {
    subview.removeFromSuperview()
    super.removeReactSubview(subview)
  }

  override func willMove(toSuperview newSuperview: UIView?) {
    if (newSuperview != nil) {
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(orientationChanged),
        name: UIApplication.didChangeStatusBarOrientationNotification,
        object: nil
      )
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(sessionWasInterrupted),
        name: .AVCaptureSessionWasInterrupted,
        object: session
      )
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(sessionDidStartRunning),
        name: .AVCaptureSessionDidStartRunning,
        object: session
      )
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(sessionRuntimeError),
        name: .AVCaptureSessionRuntimeError,
        object: session
      )
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(audioDidInterrupted),
        name: AVAudioSession.interruptionNotification,
        object: AVAudioSession.sharedInstance()
      )
      
      startSession()
    } else {
      NotificationCenter.default.removeObserver(
        self,
        name: UIApplication.didChangeStatusBarOrientationNotification,
        object: nil
      )
      NotificationCenter.default.removeObserver(
        self,
        name: .AVCaptureSessionWasInterrupted,
        object: session
      )
      NotificationCenter.default.removeObserver(
        self,
        name: .AVCaptureSessionDidStartRunning,
        object: session
      )
      NotificationCenter.default.removeObserver(
        self,
        name: .AVCaptureSessionRuntimeError,
        object: session
      )
      NotificationCenter.default.removeObserver(
        self,
        name: AVAudioSession.interruptionNotification,
        object: AVAudioSession.sharedInstance()
      )
      
      stopSession()
    }
    
    super.willMove(toSuperview: newSuperview)
  }
  
  
  // MARK: React Props Change Handlers
  @objc func setCameraId(_ cameraId: NSString) {
    let currentCameraId = self.cameraId
    if currentCameraId != nil && currentCameraId == cameraId as String { return }

    self.cameraId = cameraId as String
    updateType()
  }

  @objc func setType(_ type: NSInteger) {
    guard let parsedType = AVCaptureDevice.Position(rawValue: type) else {
      rctLogWarn("Camera type \(type) is not a valid AVCaptureDevice.Position value")
    }

    if (parsedType == presetCamera) {
      return
    }
    
    presetCamera = parsedType
    updateType()
  }
  
  @objc func setMaxZoom(_ maxZoom: NSNumber) {
    self.maxZoom = CGFloat(truncating: maxZoom)
    updateZoom()
  }

  @objc func setZoom(_ zoom: NSNumber) {
    self.zoom = CGFloat(truncating: zoom)
    updateZoom()
  }
  
  @objc func setAutoFocus(_ autoFocus: NSInteger) {
    guard let parsedFocus = AVCaptureDevice.FocusMode(rawValue: autoFocus) else {
      rctLogWarn("Focus mode \(autoFocus) is not a valid AVCaptureDevice.FocusMode value")
    }
    self.autoFocus = parsedFocus
    updateFocusMode()
  }

  @objc func setFlashMode(_ flashMode: NSInteger) {
    guard let parsedFlashMode = AVCaptureDevice.FlashMode(rawValue: flashMode) else {
      rctLogWarn("Flash mode \(flashMode) is not a valid AVCaptureDevice.FlashMode value")
    }
    self.flashMode = parsedFlashMode
    updateFlashMode()
  }
  
  @objc func setExposure(_ exposure: NSNumber) {
    guard let parsedExposure = Float(exactly: exposure) else { return }
    self.exposure = parsedExposure
    updateExposure()
  }
  
  @objc func setAutoFocusPointOfInterest(_ autoFocusPointOfInterest: NSDictionary?) {
    guard let pointOfInterest = autoFocusPointOfInterest else {
      self.autoFocusPointOfInterest = nil
      self.autoExposure = false
      updateAutoFocusPointOfInterest()
      return
    }

    guard let x = pointOfInterest["x"] as? Float,
          let y = pointOfInterest["y"] as? Float
    else {
      rctLogWarn("autoFocusPointOfInterest prop must have the shape { x: Float, y: Float }")
      return
    }
    
    self.autoFocusPointOfInterest = CGPoint(x: CGFloat(x), y: CGFloat(y))
    self.autoExposure = pointOfInterest["autoExposure"] as? Bool ?? false
    updateAutoFocusPointOfInterest()
  }
  
  @objc func setWhiteBalance(_ whiteBalance: NSInteger) {
    guard let parsedWhiteBalance = AVCaptureDevice.WhiteBalanceMode(rawValue: whiteBalance) else {
      rctLogWarn("Invalid whiteBalance prop \(whiteBalance), it is not a valid AVCaptureDevice.WhiteBalanceMode")
      return
    }
    
    if (parsedWhiteBalance == .locked) {
      rctLogWarn("To use a locked specific white balance set the property customWhiteBalance")
      return
    }

    self.whiteBalance = parsedWhiteBalance
    self.customWhiteBalance = nil
    updateWhiteBalance()
  }
  
  @objc func setCustomWhiteBalance(_ customWhiteBalance: NSDictionary?) {
    guard let balance = customWhiteBalance else {
      self.customWhiteBalance = nil
      updateWhiteBalance()
      return
    }
    
    guard let temperature = balance["temperature"] as? Float,
          let tint = balance["tint"] as? Float,
          let redGainOffset = balance["redGainOffset"] as? Float,
          let greenGainOffset = balance["greenGainOffset"] as? Float,
          let blueGainOffset = balance["blueGainOffset"] as? Float
    else {
      rctLogWarn("customWhiteBalance prop must have the shape: { temperature: Float, tint: Float, redGainOffset: Float, greenGainOffest: Float, blueGainOffest: Float}")
      return
    }
    
    self.customWhiteBalance = WhiteBalanceSettings(
      temperature: temperature,
      tint: tint,
      redGainOffset: redGainOffset,
      greenGainOffset: greenGainOffset,
      blueGainOffset: blueGainOffset
    )
    self.whiteBalance = nil
    updateWhiteBalance()
  }
  
  @objc func setNativeZoom(_ nativeZoom: Bool) {
    self.nativeZoom = nativeZoom
    setupOrDisablePinchZoom()
  }
  
  @objc func setFocusDepth(_ focusDepth: NSNumber) {
    self.focusDepth = Float(exactly: focusDepth)
    updateFocusDepth()
  }
  
  // MARK: Camera Lifecycle
  private func initialize() {
    sensorOrientationChecker.delegate = self
    previewLayer = AVCaptureVideoPreviewLayer(session: session)
    previewLayer.videoGravity = AVLayerVideoGravity.resizeAspectFill
    previewLayer.needsDisplayOnBoundsChange = true
    
    let singleTapGestureRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleSingleTap))
    singleTapGestureRecognizer.numberOfTapsRequired = 1
    let doubleTapGestureRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap))
    doubleTapGestureRecognizer.numberOfTapsRequired = 2
    addGestureRecognizer(singleTapGestureRecognizer)
    addGestureRecognizer(doubleTapGestureRecognizer)
  }
  
  private func startSession() {
    let notifyReady = { [self] in
      if let readyHandler = onCameraReady {
        readyHandler(nil)
      }
    }

    #if TARGET_IPHONE_SIMULATOR
      notifyReady()
      return
    #endif
    
    sessionQueue.async { [self] in
      // if session already running, also return and fire ready event
      // this is helpfu when the device type or ID is changed and we must
      // receive another ready event (like Android does)
      if (session.isRunning) {
        notifyReady()
        return
      }
      
      // if camera not set (invalid type and no ID) return.
      if (presetCamera == AVCaptureDevice.Position.unspecified && cameraId == nil) {
        return
      }
      
      // video device was not initialized, also return
      if (videoCaptureDeviceInput == nil) {
        return
      }
      
      let stillImageOutput = AVCaptureStillImageOutput()
      
      if (session.canAddOutput(stillImageOutput)) {
        stillImageOutput.outputSettings = [
          AVVideoCodecKey: AVVideoCodecJPEG,
          AVVideoQualityKey: 1.0
        ]
        session.addOutput(stillImageOutput)
        stillImageOutput.isHighResolutionStillImageOutputEnabled = true
        self.stillImageOutput = stillImageOutput
      }
      
      setupMovieFileCapture()
      
      sessionInterrupted = false
      session.startRunning()
      notifyReady()
    }
  }
  
  private func setupMovieFileCapture() {
    let output = AVCaptureMovieFileOutput()
    
    if (session.canAddOutput(output)) {
      session.addOutput(output)
      movieFileOutput = output
    }
  }
  
  private func stopSession() {
    #if TARGET_IPHONE_SIMULATOR
      return;
    #endif
    
    sessionQueue.async { [self] in
      previewLayer.removeFromSuperlayer()
      session.commitConfiguration()
      session.stopRunning()
      
      session.inputs.forEach { input in
        session.removeInput(input)
      }
      
      session.outputs.forEach { output in
        session.removeOutput(output)
      }
      
      // cleanup audio input if any, and release
      // audio session so other apps can continue playback.
      removeAudioCaptureSessionInput()
      
      // clean these up as well since we've removed
      // all inputs and outputs from session
      videoCaptureDeviceInput = nil
      audioCaptureDeviceInput = nil
      movieFileOutput = nil
    }
  }
  
  private func removeAudioCaptureSessionInput() {
    // Removes audio capture from the session, allowing the session
    // to resume if it was interrupted, and stopping any
    // recording in progress with the appropriate flags.
    
    guard let deviceInput = audioCaptureDeviceInput else { return }
    var audioRemoved = false
    
    if (session.inputs.contains(deviceInput)) {
      if (isRecording) {
        isRecordingInterrupted = true
      }
      
      session.removeInput(deviceInput)
      audioCaptureDeviceInput = nil
      sessionQueue.async { [self] in
        updateFlashMode()
      }
      
      audioRemoved = true
    }
    
    // Deactivate our audio session so other audio can resume
    // playing, if any. E.g., background music.
    // unless told not to
    if (!keepAudioSession) {
      do {
        try AVAudioSession.sharedInstance().setActive(
          false,
          options: AVAudioSession.SetActiveOptions.notifyOthersOnDeactivation
        )
      } catch {
        rctLogWarn("Audio device could not set inactive: \(error.localizedDescription)")
      }
    }
      
    audioCaptureDeviceInput = nil;
    
    guard let handler = onAudioInterrupted else { return }
    
    if (audioRemoved) {
      handler(nil)
    }
  }
  
  private func initializeAudioCaptureSessionInput() {
    // Initializes audio capture device
    // Note: Ensure this is called within a a session configuration block
    
    if audioCaptureDeviceInput == nil { return }
    
    // if we failed to get the audio device, fire our interrupted event
    let notifyFailed = { [self] in
      if let audioInterruptedHandler = onAudioInterrupted {
        audioInterruptedHandler(nil)
      }
    }
    
    guard let device = AVCaptureDevice.default(for: .audio) else {
      rctLogWarn("Could not create audio capture device")
      notifyFailed()
      return
    }
    
    let input: AVCaptureDeviceInput

    do {
      input = try AVCaptureDeviceInput(device: device)
    } catch {
      rctLogWarn("Could not create audio device input: \(error.localizedDescription)")
      notifyFailed()
      return
    }
    
    do {
      try AVAudioSession.sharedInstance().setActive(true)
    } catch {
      rctLogWarn("Could not set audio device as active: \(error.localizedDescription)")
      notifyFailed()
      return
    }
    
    if(!session.canAddInput(input)) {
      rctLogWarn("Cannot add audio input to capture session")
      notifyFailed()
      return
    }
    
    session.addInput(input)
    audioCaptureDeviceInput = input
    
    if let handler = onAudioConnected {
      handler(nil)
    }
  }
  
  private func initializeCaptureSessionInput() {
    let notifyMountError: (_ message: String) -> Void = { [self] message in
      if let handler = onMountError {
        handler(["message": message])
      }
    }
    sessionQueue.async { [self] in
      guard let device = getDevice() else {
        notifyMountError("Invalid camera device")
      }
      
      // if setting a new device is the same we currently have, nothing to do
      if (videoCaptureDeviceInput?.device.uniqueID == device.uniqueID) {
        return
      }
      
      var interfaceOrientation: UIInterfaceOrientation;
      
      DispatchQueue.main.sync {
        interfaceOrientation = UIApplication.shared.statusBarOrientation
      }
      
      let orientation = videoOrientationForInterfaceOrientation(orientation: interfaceOrientation)
      
      session.beginConfiguration()
      
      let captureDeviceInput: AVCaptureDeviceInput
      
      do {
        captureDeviceInput = try AVCaptureDeviceInput(device: device)
      } catch {
        session.commitConfiguration()
        notifyMountError("Could not create input from capture device: \(error.localizedDescription)")
        return
      }
      
      // Do additional cleanup that might be needed on the
      // previous device, if any.
      cleanupFocus(previousDevice: videoCaptureDeviceInput?.device)
      
      // clear this variable before setting it again.
      // Otherwise, if setting fails, we end up with a stale value.
      // and we are no longer able to detect if it changed or not
      videoCaptureDeviceInput = nil
      
      // setup our capture preset based on what was set from RN
      // and our defaults
      // if the preset is not supported (e.g., when switching cameras)
      // canAddInput below will fail
      session.sessionPreset = getDefaultPreset()
      
      exposureIsoMin = 0
      exposureIsoMax = 0
      
      if (session.canAddInput(captureDeviceInput)) {
        session.addInput(captureDeviceInput)
        videoCaptureDeviceInput = captureDeviceInput
        
        // Update all these async after our session has commited
        // since some values might be changed on session commit.
        sessionQueue.async {
          updateZoom()
          updateFocusMode()
          updateFocusDepth()
          updateExposure()
          updateAutoFocusPointOfInterest()
          updateWhiteBalance()
          updateFlashMode()
        }
        
        if let validOrientation = orientation {
          previewLayer.connection?.videoOrientation = validOrientation
        }
      } else {
        rctLogWarn("The selected device (\(device.uniqueID)) doesnt support preset \(session.sessionPreset) or configuration")
        notifyMountError("Camera device does not support selected settings.")
      }
      
      // if we have not yet set our audio capture device,
      // set it. Setting it early will prevent flickering when
      // recording a video
      // Only set it if captureAudio is true so we don't prompt
      // for permission if audio is not needed.
      // TODO: If we can update checkRecordAudioAuthorizationStatus
      // to actually do something in production, we can replace
      // the captureAudio prop by a simple permission check;
      // for example, checking
      // [[AVAudioSession sharedInstance] recordPermission] == AVAudioSessionRecordPermissionGranted
      if (captureAudio) {
        initializeAudioCaptureSessionInput();
      }

      session.commitConfiguration()
    }
  }
  
  
  // MARK: Event handlers
  @objc private func sessionDidStartRunning(_ notification: Notification) {
    // update flash and our interrupted flag on session resume

    if (sessionInterrupted) {
      // resume flash value since it will be resetted / turned off
      sessionQueue.async { [self] in
        updateFlashMode()
      }
    }
    
    sessionInterrupted = false;
  }
  
  @objc private func sessionRuntimeError(_ notification: Notification) {
    // Manually restarting the session since it must
    // have been stopped due to an error.
    sessionQueue.async { [self] in
      sessionInterrupted = false
      session.startRunning()
      if let handler = onCameraReady {
        handler(nil)
      }
    }
  }
  
  @objc private func sessionWasInterrupted(_ notification: Notification) {
    // Mark session interruption
    sessionInterrupted = true;

    // Turn on video interrupted if our session is interrupted
    // for any reason
    if (isRecording) {
      isRecordingInterrupted = true;
    }

    // prevent any video recording start that we might have on the way
    recordRequested = false;

    // get event info and fire RN event if our session was interrupted
    // due to audio being taken away.
    if let userInfo = notification.userInfo {
      let type = userInfo[AVCaptureSessionInterruptionReasonKey] as! AVCaptureSession.InterruptionReason
      if (type == AVCaptureSession.InterruptionReason.audioDeviceInUseByAnotherClient) {
        sessionQueue.async { [self] in
          removeAudioCaptureSessionInput()
        }
      }
    }
  }
  
  @objc private func audioDidInterrupted(_ notification: Notification) {
    // We are using this event to detect audio interruption ended
    // events since we won't receive it on our session
    // after disabling audio.
    
    guard let userInfo = notification.userInfo else { return }
    let type = userInfo[AVAudioSessionInterruptionTypeKey] as! AVAudioSession.InterruptionType
    
    // if our audio interruption ended
    if (type == AVAudioSession.InterruptionType.ended) {
      // and the end event contains a hint that we should resume
      // audio. Then re-connect our audio session if we are
      // capturing audio.
      // Sometimes we are hinted to not resume audio; e.g.,
      // when playing music in background.
      let option = userInfo[AVAudioSessionInterruptionOptionKey] as! AVAudioSession.InterruptionOptions
      if (captureAudio && option == AVAudioSession.InterruptionOptions.shouldResume) {
        sessionQueue.async { [self] in
          if (captureAudio) {
            initializeAudioCaptureSessionInput()
          }
        }
      }
    }
  }
  
  @objc private func orientationChanged(_ notification: Notification) {
    changePreviewOrientation(orientation: UIApplication.shared.statusBarOrientation)
  }
  
  @objc private func autoFocusDelegate(_ notification: Notification) {
    let device = notification.object as! AVCaptureDevice
    lockDevice(device) {
      defocusPointOfInterest()
      deexposePointOfInterest()
    }
  }
  
  @objc private func handleSingleTap(sender: UITapGestureRecognizer) {
    handleTap(sender: sender, isDouble: false)
  }
  @objc private func handleDoubleTap(sender: UITapGestureRecognizer) {
    handleTap(sender: sender, isDouble: true)
  }
  
  @objc private func handlePinchToZoomRecognizer(sender: UIPinchGestureRecognizer) {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    if (sender.state == .changed) {
      maxZoom = getMaxZoomFactor(device: device)
      do {
        try device.lockForConfiguration()
      } catch {
        rctLogWarn("Unable to lock device for configuration: \(error.localizedDescription)")
        return
      }
      
      let desiredZoomFactor = device.videoZoomFactor +
        CGFloat(atan2f(Float(sender.velocity), 5.0))
      
      device.videoZoomFactor = max(1, min(desiredZoomFactor, maxZoom))
      device.unlockForConfiguration()
    }
  }
  
  func orientationSet(orientation: UIInterfaceOrientation) {
    <#code#>
  }
  
  
  // MARK: Private Methods
  private func getDevice() -> AVCaptureDevice? {
    // Helper to get a device from the currently set properties (type and camera id)
    // might return nil if device failed to be retrieved or is invalid

    if let id = cameraId {
      return deviceWithCameraId(id)
    } else {
      return deviceWithMediaType(mediaType: .video, position: presetCamera)
    }
  }
  
  private func getDefaultPreset() -> AVCaptureSession.Preset {
    // helper to return the camera's instance default preset
    // this is for pictures only, and video should set another preset
    // before recording.
    // This default preset returns much smoother photos than High.
    
    if let size = pictureSize {
      return size
    }
    
    return AVCaptureSession.Preset.high
  }
  
  private func getMaxZoomFactor(device: AVCaptureDevice) -> CGFloat {
    if (maxZoom > 1) {
      return min(maxZoom, device.activeFormat.videoMaxZoomFactor)
    } else {
      return device.activeFormat.videoMaxZoomFactor;
    }
  }
  
  private func lockDevice(_ device: AVCaptureDevice, applySettings: () -> Void) {
    do {
      try device.lockForConfiguration()
    } catch {
      rctLogError(error.localizedDescription)
      return
    }
    
    applySettings()
    device.unlockForConfiguration()
  }
  
  private func cleanupFocus(previousDevice: AVCaptureDevice?) {
    // Function to cleanup focus listeners and variables on device
    // change. This is required since "defocusing" might not be
    // possible on the new device, and our device reference will be
    // different
    
    isFocusedOnPoint = false
    isExposedOnPoint = false
    
    // cleanup listeners if we had any
    if let prev = previousDevice {
      // remove event listener
      NotificationCenter.default.removeObserver(
        self,
        name: .AVCaptureDeviceSubjectAreaDidChange,
        object: prev
      )
      
      // cleanup device flags
      lockDevice(prev) {
        prev.isSubjectAreaChangeMonitoringEnabled = false
      }
    }
  }
  
  private func setupOrDisablePinchZoom() {
    if (nativeZoom) {
      let recognizer = UIPinchGestureRecognizer(
        target: self,
        action: #selector(handlePinchToZoomRecognizer)
      )
      pinchGestureRecognizer = recognizer
      addGestureRecognizer(recognizer)
    } else if let recognizer = pinchGestureRecognizer {
      removeGestureRecognizer(recognizer)
      pinchGestureRecognizer = nil
    }
  }
  
  private func updateFlashMode() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    if (!device.hasFlash || !device.isFlashModeSupported(flashMode)) {
      rctLogWarn("Device doesn't support flash mode")
      return
    }
    
    lockDevice(device) {
      device.flashMode = flashMode
    }
  }
  
  private func updateZoom() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    lockDevice(device) {
      let maxZoom = getMaxZoomFactor(device: device)
      device.videoZoomFactor = (maxZoom - 1 ) * zoom + 1
    }
  }
  
  private func updateFocusMode() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    if (device.isFocusModeSupported(autoFocus)) {
      lockDevice(device) {
        device.focusMode = autoFocus
      }
    }
  }
  
  private func updateFocusDepth() {
    guard let device = videoCaptureDeviceInput?.device,
          let focusDepth = self.focusDepth else { return }
    
    if (device.focusMode != .locked) {
      rctLogWarn("Focus depth is only supported for locked focus mode")
      return
    }
    
    if (!device.isLockingFocusWithCustomLensPositionSupported) {
      rctLogWarn("Device does not support focusDepth")
      return
    }
    
    lockDevice(device) {
      device.setFocusModeLocked(lensPosition: focusDepth, completionHandler: nil)
    }
  }

  
  private func updateAutoFocusPointOfInterest() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    lockDevice(device) {
      if let pointOfInterest = autoFocusPointOfInterest {
        if (device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.continuousAutoFocus)) {
          device.focusPointOfInterest = pointOfInterest
          device.focusMode = .continuousAutoFocus
          
          if (!isFocusedOnPoint) {
            isFocusedOnPoint = true
            
            NotificationCenter.default.addObserver(
              self,
              selector: #selector(autoFocusDelegate),
              name: .AVCaptureDeviceSubjectAreaDidChange,
              object: device
            )
            device.isSubjectAreaChangeMonitoringEnabled = true
          }
        } else {
          rctLogWarn("AutoFocusPointOfInterest not supported")
        }
        
        if (autoExposure) {
          if (device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.continuousAutoExposure)) {
            isExposedOnPoint = true
          } else {
            rctLogWarn("AutoExposurePointOfInterest not supported")
          }
        } else {
          deexposePointOfInterest()
        }
      } else {
        defocusPointOfInterest()
        deexposePointOfInterest()
      }
    }
  }
  
  private func deexposePointOfInterest() {
    let deviceOrNil = videoCaptureDeviceInput?.device
    
    if (isExposedOnPoint) {
      isExposedOnPoint = false
      
      if let device = deviceOrNil {
        let exposurePoint = CGPoint(x: 0.5, y: 0.5)
        device.exposurePointOfInterest = exposurePoint
        device.exposureMode = .continuousAutoExposure
      }
    }
  }
  
  private func defocusPointOfInterest() {
    let deviceOrNil = videoCaptureDeviceInput?.device
    
    if isFocusedOnPoint {
      isFocusedOnPoint = false
      
      if let device = deviceOrNil {
        device.isSubjectAreaChangeMonitoringEnabled = false
        NotificationCenter.default.removeObserver(
          self,
          name: .AVCaptureDeviceSubjectAreaDidChange,
          object: device
        )
        
        let prevPoint = device.focusPointOfInterest
        let autofocusPoint = CGPoint(x: 0.5, y: 0.5)
        device.focusPointOfInterest = autofocusPoint
        device.focusMode = .continuousAutoFocus
        
        if let handler = onSubjectAreaChanged {
          handler(["prevPointOfInterest": ["x": prevPoint.x, "y": prevPoint.y]])
        }
      }
    }
    
    if (isExposedOnPoint) {
      isExposedOnPoint = false
      
      if let device = deviceOrNil {
        device.exposurePointOfInterest = CGPoint(x: 0.5, y: 0.5)
        device.exposureMode = .continuousAutoExposure
      }
    }
  }
  
  /// Set the AVCaptureDevice's ISO values based on RNCamera's 'exposure' value,
  /// which is a float between 0 and 1 if defined by the user or -1 to indicate that no
  /// selection is active. 'exposure' gets mapped to a valid ISO value between the
  /// device's min/max-range of ISO-values.
  ///
  /// The exposure gets reset every time the user manually sets the autofocus-point in
  /// 'updateAutoFocusPointOfInterest' automatically. Currently no explicit event is fired.
  /// This leads to two 'exposure'-states: one here and one in the component, which is
  /// fine. 'exposure' here gets only synced if 'exposure' on the js-side changes. You
  /// can manually keep the state in sync by setting 'exposure' in your React-state
  /// everytime the js-updateAutoFocusPointOfInterest-function gets called.
  private func updateExposure() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    lockDevice(device) {
      if (exposure < 0 || exposure > 1) {
        device.exposureMode = AVCaptureDevice.ExposureMode.continuousAutoExposure
        return
      }
      
      let isoMin: Float
      let isoMax: Float
      
      if let knownMin = exposureIsoMin {
        isoMin = knownMin
      } else {
        isoMin = device.activeFormat.minISO
        exposureIsoMin = isoMin
      }
      
      if let knownMax = exposureIsoMax {
        isoMax = knownMax
      } else {
        isoMax = device.activeFormat.maxISO
        exposureIsoMax = isoMax
      }
      
      // Get a valid ISO-value in range from min to max. After we mapped the exposure
      // (a val between 0 - 1), the result gets corrected by the offset from 0, which
      // is the min-ISO-value.
      let appliedExposure = (isoMax - isoMin) * exposure + isoMin
      
      // Make sure we're in AVCaptureExposureModeCustom, else the ISO + duration time won't apply.
      // Also make sure the device can set exposure
      if (device.isExposureModeSupported(.custom)) {
        if (device.exposureMode != .custom) {
          device.exposureMode = .custom
        }
        
        device.setExposureModeCustom(
          duration: AVCaptureDevice.currentExposureDuration,
          iso: appliedExposure,
          completionHandler: nil
        )
      } else {
        rctLogWarn("Device does not support AVCaptureDevice.ExposureMode.custom")
      }
    }
  }
  
  private func updateType() {
    initializeCaptureSessionInput()
    startSession()
  }
  
  private func updateWhiteBalance() {
    if let customWhiteBalance = self.customWhiteBalance {
      applyCustomWhiteBalance(customWhiteBalance)
    } else {
      applyWhiteBalance()
    }
  }
  
  private func applyWhiteBalance() {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    lockDevice(device) {
      if let whiteBalance = self.whiteBalance {
        device.whiteBalanceMode = whiteBalance
      } else {
        device.whiteBalanceMode = .continuousAutoWhiteBalance
      }
    }
  }
  
  private func applyCustomWhiteBalance(_ whiteBalance: WhiteBalanceSettings) {
    guard let device = videoCaptureDeviceInput?.device else { return }
    
    lockDevice(device) {
      if (!device.isWhiteBalanceModeSupported(.locked)) {
        rctLogWarn("Locked white balance mode is not supported, falling back to continous auto white balance mode")
        device.whiteBalanceMode = .continuousAutoWhiteBalance
        return
      }
      
      let temperatureAndTint = AVCaptureDevice.WhiteBalanceTemperatureAndTintValues(
        temperature: whiteBalance.temperature,
        tint: whiteBalance.tint
      )
      var deviceRgbGains = device.deviceWhiteBalanceGains(for: temperatureAndTint)
      
      let redGain = deviceRgbGains.redGain + whiteBalance.redGainOffset
      let greenGain = deviceRgbGains.greenGain + whiteBalance.greenGainOffset
      let blueGain = deviceRgbGains.blueGain + whiteBalance.blueGainOffset
      
      deviceRgbGains.redGain = max(1, min(device.maxWhiteBalanceGain, redGain))
      deviceRgbGains.greenGain = max(1, min(device.maxWhiteBalanceGain, greenGain))
      deviceRgbGains.blueGain = max(1, min(device.maxWhiteBalanceGain, blueGain))
      
      device.setWhiteBalanceModeLocked(with: deviceRgbGains, completionHandler: nil)
    }
  }
  
  private func changePreviewOrientation(orientation: UIInterfaceOrientation) {
    let videoOrientationOrNil = videoOrientationForInterfaceOrientation(orientation: orientation)
    DispatchQueue.main.async { [self] in
      guard let connection = previewLayer.connection,
            let videoOrientation = videoOrientationOrNil
            else { return }
      
      if (connection.isVideoOrientationSupported) {
        connection.videoOrientation = videoOrientation
      }
    }
  }
  
  private func handleTap(sender: UITapGestureRecognizer, isDouble: Bool) {
    guard let handler = onTouch else { return }
    if (sender.state == .recognized) {
      let location = sender.location(in: self)
      
      handler([
        "isDoubleTap": isDouble,
        "touchOrigin": ["x": location.x, "y": location.y]
      ])
    }
  }
}
