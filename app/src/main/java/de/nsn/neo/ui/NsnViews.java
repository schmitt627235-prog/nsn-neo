package de.nsn.neo.ui;

import android.content.Context;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.nsn.neo.R;
import de.nsn.neo.BuildConfig;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.data.PlaybackRecord;

public final class NsnViews {
    private NsnViews() {}

    public static void applyMobileImmersiveBars(Activity activity) {
        if (BuildConfig.IS_TV) return;
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }

    public static TextView heading(Context context, String value, boolean tv) {
        TextView view = text(context, value, tv ? 26 : 22, Color.WHITE);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        // Compact section rhythm on mobile; TV keeps the wider streaming-app spacing.
        view.setPadding(dp(context, 18), dp(context, tv ? 22 : 14), dp(context, 12), dp(context, tv ? 12 : 8));
        return view;
    }

    public static LinearLayout card(Context context, String title, boolean tv) {
        return card(context, title, tv, false, false);
    }

    public static LinearLayout card(Context context, String title, boolean tv, boolean landscape, boolean progress) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL); card.setFocusable(tv); card.setClickable(true);
        // The card owns one fixed visual box. Rows may provide breathing room for focus
        // scaling, but neighboring posters must never paint into this card's bounds.
        card.setClipChildren(true); card.setClipToPadding(true);
        int width = dp(context, landscape ? (tv ? 270 : 210) : (tv ? 180 : 132));
        int posterHeight = dp(context, landscape ? (tv ? 152 : 118) : (tv ? 252 : 186));
        int inset = dp(context, 4); card.setPadding(inset, inset, inset, inset);
        ImageView poster = new ImageView(context);
        poster.setImageResource(R.drawable.nsn_logo); poster.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        poster.setBackgroundColor(Color.rgb(22,22,22));
        card.addView(poster, new LinearLayout.LayoutParams(width, posterHeight));
        if (progress) {
            View track = new View(context); GradientDrawable progressShape = new GradientDrawable();
            progressShape.setColor(context.getColor(R.color.nsn_red)); progressShape.setCornerRadius(dp(context, 2)); track.setBackground(progressShape);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(Math.round(width * .58f), dp(context, 3));
            progressParams.topMargin = dp(context, -3); card.addView(track, progressParams);
        }
        TextView label = text(context, title, tv ? 16 : 14, Color.WHITE);
        label.setMaxLines(2); label.setPadding(0, dp(context, 8), 0, 0);
        card.addView(label, new LinearLayout.LayoutParams(width, dp(context, tv ? 52 : 46)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(width + inset * 2, -2);
        cardParams.setMargins(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 14));
        card.setLayoutParams(cardParams);
        if (tv) card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).translationZ(focused ? dp(context, 12) : 0).setDuration(150).start();
            GradientDrawable bg = new GradientDrawable(); bg.setColor(focused ? Color.rgb(18,18,18) : Color.TRANSPARENT);
            bg.setStroke(dp(context, focused ? 4 : 0), context.getColor(R.color.nsn_red));
            bg.setCornerRadius(dp(context, 10)); v.setBackground(bg);
        });
        return card;
    }

    public static LinearLayout rail(Context context, String[] titles, boolean tv) {
        LinearLayout row = new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL); row.setClipChildren(false); row.setClipToPadding(false);
        for (String title : titles) row.addView(card(context, title, tv, true, true));
        return row;
    }

    public static LinearLayout card(Context context, MediaItem item, boolean tv, View.OnClickListener click) {
        LinearLayout card = card(context, item.title, tv);
        ImageView poster = (ImageView) card.getChildAt(0);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        PosterLoader.load(poster, item.posterUrl);
        card.setTag(item); card.setOnClickListener(click);
        return card;
    }

    public static LinearLayout playbackCard(Context context, PlaybackRecord record, boolean tv, View.OnClickListener click) {
        LinearLayout card=card(context,record.title,tv,true,false); ImageView poster=(ImageView)card.getChildAt(0);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP); PosterLoader.load(poster,record.posterUrl);
        int width=dp(context,tv?270:210); View track=new View(context); GradientDrawable shape=new GradientDrawable();
        shape.setColor(context.getColor(R.color.nsn_red)); shape.setCornerRadius(dp(context,2)); track.setBackground(shape);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.max(dp(context,5),Math.round(width*record.progress())),dp(context,4));
        p.topMargin=dp(context,-4); card.addView(track,1,p);
        if(record.subtitle!=null&&!record.subtitle.isEmpty())((TextView)card.getChildAt(2)).setText(record.title+"\n"+record.subtitle);
        card.setTag(record);card.setOnClickListener(click);return card;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
