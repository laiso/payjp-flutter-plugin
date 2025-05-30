import Flutter
import Payjp

class PayjpThreeDSecureHandler: NSObject {
    private let channel: FlutterMethodChannel
    private var pendingResult: FlutterResult?
    
    init(channel: FlutterMethodChannel) {
        self.channel = channel
        super.init()
    }
    
    func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case ChannelMethodToNative.startThreeDSecureWithResourceId.rawValue:
            guard let args = call.arguments as? [String: Any],
                  let resourceId = args["resourceId"] as? String else {
                result(FlutterError(code: "INVALID_ARGUMENT",
                                  message: "resourceId is required",
                                  details: nil))
                return
            }
            
            if pendingResult != nil {
                result(FlutterError(code: "PENDING_OPERATION",
                                  message: "Another 3DS process is already in progress.",
                                  details: nil))
                return
            }
            
            pendingResult = result
            
            if let viewController = UIApplication.shared.keyWindow?.rootViewController {
                PayjpVerifier.shared.startThreeDSecureFlow(resourceId: resourceId) { [weak self] result in
                    guard let self = self else { return }
                    
                    switch result {
                    case .success:
                        self.channel.invokeMethod(ChannelMethodFromNative.onCardFormCompleted.rawValue, arguments: nil)
                        self.pendingResult?(nil)
                    case .canceled:
                        self.channel.invokeMethod(ChannelMethodFromNative.onCardFormCanceled.rawValue, arguments: nil)
                        self.pendingResult?(nil)
                    case .failure(let error):
                        self.pendingResult?(FlutterError(code: "THREE_D_SECURE_FAILED",
                                                       message: error.localizedDescription,
                                                       details: nil))
                    }
                    self.pendingResult = nil
                }
            } else {
                result(FlutterError(code: "NO_VIEW_CONTROLLER",
                                  message: "No root view controller found",
                                  details: nil))
            }
            
        default:
            result(FlutterMethodNotImplemented)
        }
    }
} 