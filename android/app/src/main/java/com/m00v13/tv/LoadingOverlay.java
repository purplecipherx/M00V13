package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public final class LoadingOverlay {
    private final FrameLayout root;

    private LoadingOverlay(Activity activity,String message) {
        root=new FrameLayout(activity); root.setBackgroundColor(Color.BLACK); root.setClickable(true); root.setFocusable(true); root.setElevation(1000f);
        ImageView image=new ImageView(activity); image.setImageResource(R.drawable.loading_cow); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setAdjustViewBounds(false); image.setBackgroundColor(Color.BLACK);
        root.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout shade=new FrameLayout(activity); shade.setBackgroundColor(Color.argb(45,0,0,0)); root.addView(shade,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        TextView status=TvUi.text(activity,message==null||message.isEmpty()?"Searching…":message,18,true); status.setTextColor(TvUi.BLUE); FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,TvUi.dp(activity,46),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM); sp.bottomMargin=TvUi.dp(activity,52); root.addView(status,sp);
        TextView credit=TvUi.text(activity,"Coded by PurplecipherX",13,false); credit.setTextColor(Color.argb(220,245,242,248)); FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,TvUi.dp(activity,36),Gravity.END|Gravity.BOTTOM); cp.rightMargin=TvUi.dp(activity,22);cp.bottomMargin=TvUi.dp(activity,14);root.addView(credit,cp);
    }

    public static LoadingOverlay show(Activity activity){return show(activity,"Searching…");}
    public static LoadingOverlay show(Activity activity,String message){LoadingOverlay overlay=new LoadingOverlay(activity,message);ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();decor.addView(overlay.root,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));overlay.root.requestFocus();return overlay;}
    public void hide(){if(root.getParent() instanceof ViewGroup)((ViewGroup)root.getParent()).removeView(root);}
}
