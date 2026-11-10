package com.example.dedilharte.view;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public final class WindowInsetsHelper {
    private WindowInsetsHelper() {}
    public static void activity(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content.getChildCount() > 0) apply(activity.getWindow(), content.getChildAt(0));
    }
    public static void dialog(Dialog dialog) {
        ViewGroup content = dialog.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) apply(dialog.getWindow(), content.getChildAt(0));
    }
    private static void apply(Window window, View root) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + Math.max(safe.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }
}
