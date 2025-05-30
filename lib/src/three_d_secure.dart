/*
 * Copyright (c) 2020 PAY, Inc.
 *
 * Use of this source code is governed by a MIT License that can by found in the LICENSE file.
 */
/// Redirect configuration for 3D Secure.
/// Register in [PAY.JP dashboard](https://pay.jp/d/settings).
class PayjpThreeDSecureRedirect {
  final String url;
  final String key;
  PayjpThreeDSecureRedirect({required this.url, required this.key});
}

/// 3Dセキュア処理のステータスを表す列挙型
enum ThreeDSecureProcessStatus {
  /// 3Dセキュア処理が完了した
  completed,
  /// 3Dセキュア処理がキャンセルされた
  canceled,
}

/// 3Dセキュア処理が成功したときに実行されるコールバック
typedef OnThreeDSecureProcessSucceeded = void Function(ThreeDSecureProcessStatus status);

/// 3Dセキュア処理が失敗したときに実行されるコールバック
typedef OnThreeDSecureProcessFailed = void Function({required String message, required int code});
