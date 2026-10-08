package com.ghostly.apk;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class AimTrainerActivity extends Activity {
    AimView game;
    TextView stats;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        FrameLayout root=new FrameLayout(this);
        game=new AimView();
        root.addView(game,new FrameLayout.LayoutParams(-1,-1));
        stats=new TextView(this);
        stats.setTextColor(Color.WHITE); stats.setTextSize(16); stats.setPadding(24,24,24,24);
        stats.setShadowLayer(8,0,2,Color.BLACK);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(stats,sp);
        setContentView(root);
        stats.setText("🎯 AIM TRAINER\n30 целей · тапай по мишеням");
    }

    void finishGame(){
        stats.setText(String.format(Locale.US,
            "🏆 Результат\nТочность: %.1f%%\nСредняя реакция: %d ms\nПопаданий: %d/%d\nЛучшая серия: %d",
            game.accuracy(),game.avgReaction(),game.hits,game.total,game.bestStreak));
    }

    class AimView extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Random rnd=new Random();
        float tx=300,ty=500,tr=48;
        long shownAt=0;
        int total=0,hits=0,streak=0,bestStreak=0;
        long reactionSum=0;
        boolean running=true;

        AimView(){super(AimTrainerActivity.this);p.setTextAlign(Paint.Align.CENTER);postDelayed(this::spawn,500);}

        void spawn(){
            if(!running)return;
            int w=Math.max(1,getWidth()),h=Math.max(1,getHeight());
            tr=Math.max(28,Math.min(62,Math.min(w,h)*0.045f));
            tx=tr+rnd.nextFloat()*(Math.max(tr,w-2*tr));
            ty=120+tr+rnd.nextFloat()*Math.max(1,h-220-2*tr);
            shownAt=System.currentTimeMillis();
            invalidate();
        }

        @Override protected void onDraw(Canvas c){
            c.drawColor(Color.rgb(7,6,11));
            p.setColor(0xffb980ff); c.drawCircle(tx,ty,tr,p);
            p.setColor(0xfff7efff); c.drawCircle(tx,ty,tr*0.52f,p);
            p.setColor(0xff7e4cff); c.drawCircle(tx,ty,tr*0.20f,p);
            p.setColor(Color.WHITE);p.setTextSize(20);
            c.drawText("Цель "+Math.min(total+1,30)+"/30",getWidth()/2f,48,p);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(!running || e.getAction()!=MotionEvent.ACTION_DOWN)return true;
            float dx=e.getX()-tx,dy=e.getY()-ty;
            total++;
            if(dx*dx+dy*dy<=tr*tr){
                hits++;streak++;bestStreak=Math.max(bestStreak,streak);
                reactionSum+=System.currentTimeMillis()-shownAt;
            }else streak=0;
            if(total>=30){
                running=false;invalidate();finishGame();return true;
            }
            spawn();return true;
        }

        double accuracy(){return total==0?0:(hits*100.0/total);}
        long avgReaction(){return hits==0?0:reactionSum/hits;}
    }
}
