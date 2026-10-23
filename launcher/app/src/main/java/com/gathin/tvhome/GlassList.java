package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The TV menu's glass list (popup_list, item_input rows, white ticks) for screens other than Home: launcher settings'
 * «الموقع» and «أوقات الإقامة». One choice: OK picks a row and closes the list; Back closes it unchanged.
 */
final class GlassList {

    private GlassList() {
    }

    static void show(Activity activity, View over, List<String> labels, boolean[] checked, IntConsumer pick) {
        float density = activity.getResources().getDisplayMetrics().density;
        ViewGroup root = activity.findViewById(android.R.id.content);
        View panel = activity.getLayoutInflater().inflate(R.layout.popup_list, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = new PopupWindow(panel, (int) (300 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setElevation(0f);
        View first = null;
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            row.findViewById(R.id.icon).setVisibility(View.GONE);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            TextView mark = row.findViewById(R.id.status);
            mark.setText("");
            mark.setCompoundDrawablesWithIntrinsicBounds(checked[i] ? R.drawable.custom_check : 0, 0, 0, 0);
            mark.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            mark.setContentDescription(checked[i] ? "محدد" : null);
            TextView title = row.findViewById(R.id.title);
            title.setText(labels.get(i));
            title.setTypeface(android.graphics.Typeface.DEFAULT);   // item_input's Outfit has no Arabic
            Runnable act = () -> {
                popup.dismiss();
                pick.accept(index);
            };
            row.setOnClickListener(v -> act.run());
            // The OK-up that opened the list must not pick its first row, as in the TV menu.
            final boolean[] pressed = {false};
            row.setOnKeyListener((v, code, event) -> {
                if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) pressed[0] = true;
                    return true;
                }
                if (pressed[0]) act.run();
                pressed[0] = false;
                return true;
            });
            rows.addView(row);
            if (first == null || checked[i]) first = row;
        }
        // At most about ten rows high; the rest scroll as focus moves.
        int max = (int) (440 * density);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        if (panel.getMeasuredHeight() > max) {
            View scroll = panel.findViewById(R.id.scroll);
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            lp.height = max - (int) (12 * density);
            scroll.setLayoutParams(lp);
        }
        popup.showAtLocation(over, Gravity.CENTER, 0, 0);
        if (first != null) first.requestFocus();
    }
}
