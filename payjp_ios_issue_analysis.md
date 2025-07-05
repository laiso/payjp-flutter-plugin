# PayJP Flutter iOS Platform Channel Threading Issue - Research Analysis

## Executive Summary

The PayJP Flutter integration is experiencing an iOS-specific issue where 3D Secure authentication causes black screen displays and platform channel threading errors. This problem occurs only when 3D Secure is enabled and only on iOS devices, while Android devices function normally.

## Problem Description

### Primary Issue
- **Error**: `The 'payjp' channel sent a message from native to Flutter on a non-platform thread`
- **Symptom**: Black screen (no UI display) after card registration with 3D Secure enabled
- **Platform**: iOS only (Android works normally)
- **Trigger**: 3D Secure authentication flow

### Error Details
```
[ERROR:flutter/shell/common/shell.cc(1053)]
The 'payjp' channel sent a message from native to Flutter on a non-platform thread.
Platform channel messages must be sent on the platform thread
```

## Root Cause Analysis

### 1. Platform Channel Threading Violation
The core issue is that PayJP's iOS SDK is sending messages to Flutter from a background thread instead of the main thread. This violates Flutter's platform channel requirements.

### 2. iOS-Specific 3D Secure Implementation
- iOS 3D Secure flows often involve WebView components and URL scheme handling
- These processes may trigger callbacks from background threads
- The PayJP iOS SDK's callback mechanism isn't properly dispatching to the main thread

### 3. UI Hierarchy Corruption
When platform channel messages are sent from non-main threads, it can cause:
- UI rendering failures
- Black screen display
- App crashes in severe cases

## Technical Solutions

### Merchant-Side Implementation (Recommended)

#### 1. Main Thread Dispatch Wrapper
```swift
// In iOS native code (AppDelegate.swift or custom plugin)
import UIKit

extension FlutterMethodChannel {
    func invokeMethodOnMainThread(_ method: String, arguments: Any?, result: @escaping FlutterResult) {
        if Thread.isMainThread {
            self.invokeMethod(method, arguments: arguments, result: result)
        } else {
            DispatchQueue.main.async {
                self.invokeMethod(method, arguments: arguments, result: result)
            }
        }
    }
}
```

#### 2. URL Scheme Handler Enhancement
```swift
// AppDelegate.swift
override func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
    // Ensure PayJP URL scheme handling occurs on main thread
    if url.scheme?.contains("payjp") == true {
        DispatchQueue.main.async {
            // Handle PayJP URL scheme
            self.handlePayJPCallback(url: url)
        }
        return true
    }
    return super.application(app, open: url, options: options)
}

private func handlePayJPCallback(url: URL) {
    // Process PayJP 3D Secure callback safely on main thread
    // Notify Flutter through platform channel
}
```

#### 3. Flutter Dart Implementation
```dart
// Enhanced error handling and thread safety
class PayJPService {
  static const MethodChannel _channel = MethodChannel('payjp');
  
  static Future<PaymentResult> processPayment({
    required String cardToken,
    required int amount,
    bool useThreeDSecure = false,
  }) async {
    try {
      // Ensure we're calling from the main isolate
      assert(Isolate.current.debugName == 'main');
      
      final result = await _channel.invokeMethod('startCardForm', {
        'cardToken': cardToken,
        'amount': amount,
        'useThreeDSecure': useThreeDSecure,
      });
      
      return PaymentResult.fromMap(result);
    } catch (e) {
      // Handle platform channel errors gracefully
      return PaymentResult.error(e.toString());
    }
  }
}
```

### 4. WebView Integration Fix (if applicable)
```swift
// For WebView-based 3D Secure flows
import WebKit

class PayJPWebViewDelegate: NSObject, WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Ensure callbacks to Flutter happen on main thread
        DispatchQueue.main.async {
            self.notifyFlutter(success: true)
        }
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        DispatchQueue.main.async {
            self.notifyFlutter(success: false, error: error.localizedDescription)
        }
    }
    
    private func notifyFlutter(success: Bool, error: String? = nil) {
        // Safe Flutter notification on main thread
    }
}
```

## Investigation Steps

### 1. Debug View Hierarchy Analysis
When the black screen occurs, use Xcode's Debug View Hierarchy to analyze:
- Whether the WebView/card form layers exist
- If 3D Secure authentication UI is present but not visible
- UI stack corruption

### 2. Threading Analysis
```swift
// Add logging to identify threading issues
func debugPlatformChannelCall(_ method: String) {
    print("PayJP Platform Channel Call: \(method)")
    print("Is Main Thread: \(Thread.isMainThread)")
    print("Current Thread: \(Thread.current)")
}
```

### 3. 3D Secure Flow Monitoring
```dart
// Add comprehensive logging
void logPaymentFlow(String step, Map<String, dynamic> data) {
  print('PayJP Flow - $step: $data');
  print('Current DateTime: ${DateTime.now()}');
  print('Thread Info: ${Isolate.current.debugName}');
}
```

## PayJP SDK Considerations

### Reported Issues (from Investigation)
1. **Threading Errors**: The PayJP iOS SDK may not consistently use main thread for Flutter callbacks
2. **3D Secure Implementation**: Complex interaction between native iOS UI and Flutter layers
3. **WebView Integration**: Potential conflicts between PayJP's WebView handling and Flutter's rendering

### SDK Version Compatibility
- Ensure using latest PayJP Flutter SDK version
- Check for iOS-specific updates and patches
- Monitor PayJP's GitHub issues for similar reports

## Immediate Mitigation Strategies

### 1. Graceful Error Handling
```dart
class PaymentHandler {
  static Future<void> handlePaymentWithRetry({
    required String cardToken,
    required int amount,
    int maxRetries = 3,
  }) async {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await PayJPService.processPayment(
          cardToken: cardToken,
          amount: amount,
          useThreeDSecure: true,
        );
        return; // Success
      } catch (e) {
        if (attempt == maxRetries - 1) {
          // Final attempt failed, show user-friendly error
          showPaymentErrorDialog();
        } else {
          // Wait before retry
          await Future.delayed(Duration(seconds: 2));
        }
      }
    }
  }
}
```

### 2. Fallback UI
```dart
class PaymentScreen extends StatefulWidget {
  @override
  _PaymentScreenState createState() => _PaymentScreenState();
}

class _PaymentScreenState extends State<PaymentScreen> {
  bool _isPaymentProcessing = false;
  bool _hasError = false;
  
  @override
  Widget build(BuildContext context) {
    if (_hasError) {
      return PaymentErrorWidget(onRetry: _retryPayment);
    }
    
    if (_isPaymentProcessing) {
      return PaymentLoadingWidget();
    }
    
    return PaymentFormWidget();
  }
  
  void _retryPayment() {
    setState(() {
      _hasError = false;
      _isPaymentProcessing = true;
    });
    // Restart payment flow
  }
}
```

## Recommendations

### For Merchants
1. **Implement Thread-Safe Wrappers**: Use the provided Swift code to ensure main thread execution
2. **Add Comprehensive Error Handling**: Implement retry mechanisms and graceful degradation
3. **Monitor Debug View Hierarchy**: Use Xcode tools to analyze UI issues when they occur
4. **Test Extensively**: Focus on iOS device testing with 3D Secure enabled

### For PayJP
1. **Platform Channel Threading Fix**: Update iOS SDK to ensure all Flutter callbacks occur on main thread
2. **3D Secure Implementation Review**: Audit the 3D Secure flow for threading issues
3. **Documentation Update**: Provide clear guidance on thread safety requirements
4. **Sample Implementation**: Provide reference implementation showing proper thread handling

### Implementation Priority
1. **High Priority**: Main thread dispatch wrapper (immediate fix)
2. **Medium Priority**: Enhanced error handling and retry logic
3. **Low Priority**: Debug logging and monitoring improvements

## Testing Protocol

### Device Testing Matrix
- iPhone models: 12, 13, 14, 15 series
- iOS versions: 15.0+, 16.0+, 17.0+
- 3D Secure scenarios: Enabled/Disabled
- Network conditions: WiFi, cellular, poor connectivity

### Test Cases
1. **Basic 3D Secure Flow**: Card registration with authentication
2. **Background/Foreground**: App backgrounding during 3D Secure
3. **Orientation Changes**: Device rotation during payment
4. **Memory Pressure**: Payment under low memory conditions
5. **Network Interruption**: 3D Secure with network issues

## Impact Assessment

### Business Impact
- **User Experience**: Black screen during payment severely impacts UX
- **Conversion Rates**: Payment failures directly affect revenue
- **Platform Disparity**: iOS-only issue creates platform-specific problems

### Technical Impact
- **Support Burden**: Increased customer support for iOS payment issues
- **Development Overhead**: Platform-specific fixes and testing
- **Risk Assessment**: Potential for wider deployment of payment issues

## Conclusion

The PayJP iOS platform channel threading issue can be resolved through merchant-side implementation of thread-safe wrappers and proper error handling. While the root cause lies in the PayJP iOS SDK's threading implementation, merchants can implement effective workarounds without waiting for SDK updates.

The solution involves ensuring all platform channel communications occur on the main thread, implementing robust error handling, and providing fallback mechanisms for payment failures.

**Recommended Immediate Action**: Implement the main thread dispatch wrapper and enhanced error handling to resolve the black screen issue while maintaining payment functionality.