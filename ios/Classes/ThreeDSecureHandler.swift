//
//  ThreeDSecureHandler.swift
//  payjp_flutter
//

import Foundation
import PAYJP

protocol ThreeDSecureHandlerDelegate: AnyObject {
    func threeDSecureHandlerDidFinish(status: ThreeDSecureProcessStatus)
}

class ThreeDSecureHandler: NSObject {
    weak var delegate: ThreeDSecureHandlerDelegate?
    private let channel: FlutterMethodChannel
    
    init(channel: FlutterMethodChannel) {
        self.channel = channel
        super.init()
    }
    
    private func notifyCompletion(status: ThreeDSecureProcessStatus) {
        let method: ChannelMethodFromNative
        switch status {
        case .completed:
            method = .onCardFormCompleted
        case .canceled:
            method = .onCardFormCanceled
        @unknown default:
            method = .onCardFormCanceled
        }
        self.channel.invokeMethod(method.rawValue, arguments: nil)
        delegate?.threeDSecureHandlerDidFinish(status: status)
    }
}

extension ThreeDSecureHandler: ThreeDSecureProcessHandlerDelegate {
    func threeDSecureProcessHandlerDidFinish(_ handler: ThreeDSecureProcessHandler, status: ThreeDSecureProcessStatus) {
        notifyCompletion(status: status)
    }
}