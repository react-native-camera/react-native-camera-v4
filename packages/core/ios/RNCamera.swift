import Foundation
import AVFoundation

protocol RNCameraDelegate {
  func onTouch(data: NSDictionary)
}

class RNCamera : UIView, SensorOrientationCheckerDelegate {
  
  let sessionQueue = dispatch_queue_serial_t(label: "cameraQueue")
  let session = AVCaptureSession()
  let sensorOrientationChecker = SensorOrientationChecker()

  var delegate: RNCameraDelegate?
  var videoCaptureDeviceInput: AVCaptureDeviceInput?
  var audioCaptureDeviceInput: AVCaptureDeviceInput?
  var previewLayer: AVCaptureVideoPreviewLayer
  var movieFileOutput: AVCaptureMovieFileOutput?
  var stillImageOutput: AVCaptureStillImageOutput?
  var autoFocus: Int = -1
  var exposure: Float = -1
  var presetCamera = AVCaptureDevice.Position.unspecified
  var cameraId: String?
  var flashMode: AVCaptureDevice.FlashMode = AVCaptureDevice.FlashMode.off
  var isFocusedOnPoint = false
  var isExposedOnPoint = false
  var invertImageData = true
  var isRecordingInterrupted = false
  var keepAudioSession = false
  var captureAudio = false
  
  private var recordRequested = false
  private var sessionInterrupted = false
  
  @objc var onCameraReady: RCTDirectEventBlock?
  @objc var onTouch: RCTDirectEventBlock?
  @objc var onAudioConnected: RCTDirectEventBlock?
  @objc var onAudioInterrupted: RCTDirectEventBlock?
  
  var isRecording: Bool {
    get { return movieFileOutput?.isRecording ?? false }
  }
  
  required init?(coder: NSCoder) {
    super.init(coder: coder)
    initialize()
  }
  
  override init(frame: CGRect) {
    super.init(frame: frame)
    initialize()
  }
  
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
      notifyReady()
      return
      
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
  
  // update flash and our interrupted flag on session resume
  @objc private func sessionDidStartRunning(_ notification: Notification) {
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
  
  // Removes audio capture from the session, allowing the session
  // to resume if it was interrupted, and stopping any
  // recording in progress with the appropriate flags.
  private func removeAudioCaptureSessionInput() {
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
  
  // We are using this event to detect audio interruption ended
  // events since we won't receive it on our session
  // after disabling audio.
  @objc private func audioDidInterrupted(_ notification: Notification) {
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
  
  // Initializes audio capture device
  // Note: Ensure this is called within a a session configuration block
  private func initializeAudioCaptureSessionInput() {
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
  
  @objc private func orientationChanged(_ notification: Notification) {
    changePreviewOrientation(orientation: UIApplication.shared.statusBarOrientation)
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
  
  @objc private func handleSingleTap(sender: UITapGestureRecognizer) {
    handleTap(sender: sender, isDouble: false)
  }
  @objc private func handleDoubleTap(sender: UITapGestureRecognizer) {
    handleTap(sender: sender, isDouble: true)
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
  
  func orientationSet(orientation: UIInterfaceOrientation) {
    <#code#>
  }
}
