package com.gathin.tvhome;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One look for every number in the clean view, the prayer time's: one digit font (HomeScene.digitFace, Noto Naskh
 * Arabic UI) in full white, at the size of the text around it. Bold headlines («109 أيام») and the dimmed small lines keep their own style for the
 * words only.
 */
final class NumberStyle {

    /** Digits, with the colon of a time kept inside the run: "4:36", "109", "25". */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?::\\d+)?");
    private static final int WHITE = 0xFFFFFFFF;

    private final Typeface face;
    /** Arabic-Indic digits (٠١٢٣) instead of 0123, CustomHomeConfig.digits. */
    private boolean arabic;

    NumberStyle(Typeface face) {
        this.face = face;
    }

    void setArabic(boolean arabic) {
        this.arabic = arabic;
    }

    /** The number style over every run of digits, and the digits in the chosen script (same length, spans kept). */
    CharSequence apply(CharSequence text) {
        SpannableString s = new SpannableString(arabic ? toArabic(text) : text);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) s.setSpan(new Span(face), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    static String toArabic(CharSequence text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('\u0660' + (c - '0')) : c);
        }
        return b.toString();
    }

    /** TypefaceSpan(Typeface) is API 28; this works from the launcher's minSdk 26. */
    private static final class Span extends MetricAffectingSpan {
        private final Typeface face;

        Span(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(face);
            p.setFakeBoldText(false);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            updateMeasureState(p);
            p.setColor(WHITE);
        }
    }
}
