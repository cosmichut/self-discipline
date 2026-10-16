import 'dart:async';

import 'package:flutter/material.dart';

/// 长按确认按钮：按住时显示进度，持续按住满 [duration] 才触发。
/// 用于"放弃类"操作（取消限额等），提高操作的时间成本。
class HoldToConfirmButton extends StatefulWidget {
  final Duration duration;
  final String label;
  final VoidCallback onConfirmed;

  const HoldToConfirmButton({
    super.key,
    required this.duration,
    required this.label,
    required this.onConfirmed,
  });

  @override
  State<HoldToConfirmButton> createState() => _HoldToConfirmButtonState();
}

class _HoldToConfirmButtonState extends State<HoldToConfirmButton> {
  double _progress = 0;
  Timer? _timer;
  DateTime? _holdStart;

  void _startHold() {
    _holdStart = DateTime.now();
    _timer = Timer.periodic(const Duration(milliseconds: 50), (_) {
      final held = DateTime.now().difference(_holdStart!).inMilliseconds;
      setState(() => _progress =
          (held / widget.duration.inMilliseconds).clamp(0.0, 1.0));
      if (_progress >= 1.0) {
        _cancelHold();
        widget.onConfirmed();
      }
    });
  }

  void _cancelHold() {
    _timer?.cancel();
    _timer = null;
    _holdStart = null;
    if (mounted && _progress > 0) setState(() => _progress = 0);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinearProgressIndicator(value: _progress),
        const SizedBox(height: 8),
        Listener(
          onPointerDown: (_) => _startHold(),
          onPointerUp: (_) => _cancelHold(),
          onPointerCancel: (_) => _cancelHold(),
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 14),
            decoration: BoxDecoration(
              color: Colors.red.shade50,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.red.shade200),
            ),
            alignment: Alignment.center,
            child: Text(
              widget.label,
              style: const TextStyle(color: Colors.red),
            ),
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
