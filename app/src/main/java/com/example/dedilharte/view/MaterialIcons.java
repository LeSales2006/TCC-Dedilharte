package com.example.dedilharte.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import com.example.dedilharte.R;

/** Local Material Symbols Outlined: 24dp bounds, with 20dp glyphs on contrast badges. */
public final class MaterialIcons {
    public static final int DARK = Color.rgb(27, 79, 76);
    private MaterialIcons() {}

    public static Drawable drawable(Context context, int resource, int tint) {
        Drawable icon = AppCompatResources.getDrawable(context, resource).mutate();
        icon.setTint(tint);
        int size = dp(context, 24);
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    public static Drawable onSurface(Context context, int resource, int surface) {
        boolean brand = surface == DARK || surface == Color.rgb(50, 150, 145)
                || surface == Color.rgb(84, 214, 221);
        int tint = brand || ColorUtils.calculateLuminance(surface) < .18 ? Color.WHITE : DARK;
        Drawable icon = drawable(context, resource, tint);
        if (ColorUtils.calculateContrast(tint, surface) >= 3) return icon;
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(DARK); badge.setCornerRadius(dp(context, 6));
        icon.setTint(Color.WHITE);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{badge, icon});
        int inset = dp(context, 2);
        layers.setLayerInset(1, inset, inset, inset, inset);
        layers.setLayerSize(1, dp(context, 20), dp(context, 20));
        layers.setLayerGravity(1, android.view.Gravity.CENTER);
        layers.setBounds(0, 0, dp(context, 24), dp(context, 24));
        return layers;
    }

    public static void leading(TextView view, int resource, int surface) {
        view.setCompoundDrawablesRelative(onSurface(view.getContext(), resource, surface), null, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 8));
    }

    public static void iconOnly(TextView view, int resource, int surface, String description) {
        view.setTextSize(0); view.setIncludeFontPadding(false);
        view.setCompoundDrawablePadding(0);
        view.setCompoundDrawablesRelative(null, null, null, null);
        SpannableString image = new SpannableString(" ");
        image.setSpan(new ImageSpan(onSurface(view.getContext(), resource, surface), ImageSpan.ALIGN_BOTTOM),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(image);
        view.setContentDescription(description);
        view.setFocusable(true);
    }

    public static Drawable dialogWarning(Context context) {
        TypedValue background = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        return onSurface(context, R.drawable.ic_ms_warning, background.data);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
