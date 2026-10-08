package com.ghostly.apk;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.util.*;

public class TrafficGameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random(42);
    private final ArrayList<Car> cars = new ArrayList<>();
    private float playerX = 0f;
    private float roadScroll = 0f;
    private long lastTime;
    private float distance = 0f;
    private int score = 0;
    private int best = 0;
    private boolean crashed = false;
    private float touchDownX;
    private long touchDownTime;

    private static final float PLAYER_SPEED = 0.34f;
    private static final float WORLD_SCALE = 1.0f;

    public TrafficGameView(Context c) {
        super(c);
        p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setFocusable(true);
        for (int i=0;i<9;i++) {
            boolean same = i < 6;
            cars.add(new Car(same, same ? 0 : 1));
        }
        resetTraffic();
        lastTime = System.nanoTime();
    }

    private void resetTraffic() {
        for (int i=0;i<cars.size();i++) {
            Car c=cars.get(i);
            c.sameDirection = i < 6;
            c.lane = c.sameDirection ? 0 : 1;
            c.x = laneX(c.lane);
            c.z = 0.45f + i*0.72f;
            c.speed = c.sameDirection ? 0.16f + rng.nextFloat()*0.11f : 0.18f + rng.nextFloat()*0.14f;
            c.targetLane=c.lane;
            c.state=0;
            c.timer=0;
            c.color=randomColor();
        }
    }

    private int randomColor() {
        int[] colors={0xFFE7E7EA,0xFF4D5DFF,0xFFFF4E6A,0xFFFFB52E,0xFF51D18A,0xFF9C7BFF,0xFF30333A};
        return colors[rng.nextInt(colors.length)];
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now=System.nanoTime();
        float dt=Math.min(0.04f,(now-lastTime)/1_000_000_000f);
        lastTime=now;
        update(dt);
        drawWorld(c);
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        if (crashed) return;
        distance += PLAYER_SPEED*dt*92f;
        score=(int)distance;
        roadScroll=(roadScroll+PLAYER_SPEED*dt*1.7f)%1f;

        for(Car car:cars) updateCar(car,dt);
        recycleCars();
        checkCollisions();
    }

    private void updateCar(Car car,float dt) {
        car.timer-=dt;
        float desired=car.speed;

        if (car.sameDirection) {
            Car blocker=findSlowerAhead(car);
            if (blocker!=null && car.state==0 && car.timer<=0) {
                int other=1-car.lane;
                if (isLaneSafe(other,car.z,0.55f)) {
                    car.targetLane=other;
                    car.state=1;
                    car.timer=0.9f;
                } else {
                    desired=Math.max(0.10f,blocker.speed-0.01f);
                }
            }

            if(car.state==1) {
                car.x=approach(car.x,laneX(car.targetLane),dt*1.7f);
                if(Math.abs(car.x-laneX(car.targetLane))<0.025f) {
                    car.lane=car.targetLane;
                    car.state=2;
                    car.timer=0.65f;
                }
            } else if(car.state==2) {
                Car passed=findSlowerAhead(car);
                if(passed==null || car.timer<=0) {
                    int home=0;
                    if(isLaneSafe(home,car.z,0.5f)) {
                        car.targetLane=home;
                        car.state=3;
                        car.timer=1.0f;
                    }
                }
            } else if(car.state==3) {
                car.x=approach(car.x,laneX(car.targetLane),dt*1.7f);
                if(Math.abs(car.x-laneX(car.targetLane))<0.025f) {
                    car.lane=car.targetLane;
                    car.state=0;
                    car.timer=1.2f;
                }
            }
        }

        float direction=car.sameDirection ? -1f : 1f;
        car.z += direction*(PLAYER_SPEED-car.speed)*dt*1.7f;
        car.z += direction*car.speed*dt*0.35f;
    }

    private Car findSlowerAhead(Car me) {
        Car best=null;
        float bestZ=99f;
        for(Car o:cars) {
            if(o==me || !o.sameDirection) continue;
            if(o.lane!=me.lane && Math.abs(o.x-laneX(me.lane))>0.08f) continue;
            float dz=o.z-me.z;
            if(dz>0.05f && dz<0.72f && o.speed<me.speed && dz<bestZ) {
                best=o; bestZ=dz;
            }
        }
        return best;
    }

    private boolean isLaneSafe(int lane,float z,float radius) {
        for(Car o:cars) {
            if(o.lane!=lane && Math.abs(o.x-laneX(lane))>0.08f) continue;
            if(Math.abs(o.z-z)<radius) return false;
        }
        return true;
    }

    private void recycleCars() {
        for(Car car:cars) {
            if(car.z < -0.25f || car.z > 4.2f) {
                boolean same=car.sameDirection;
                car.z=same ? 3.0f+rng.nextFloat()*1.2f : -0.3f-rng.nextFloat()*1.2f;
                car.lane=same?0:1;
                car.targetLane=car.lane;
                car.x=laneX(car.lane);
                car.speed=same?0.16f+rng.nextFloat()*0.11f:0.18f+rng.nextFloat()*0.14f;
                car.state=0;
                car.timer=0;
                car.color=randomColor();
            }
        }
    }

    private void checkCollisions() {
        for(Car car:cars) {
            if(Math.abs(car.x-playerX)<0.18f && car.z<0.22f && car.z>-0.10f) {
                crashed=true;
                best=Math.max(best,score);
                return;
            }
        }
    }

    private float laneX(int lane) { return lane==0 ? -0.27f : 0.27f; }
    private float approach(float a,float b,float amount) {
        if(a<b) return Math.min(b,a+amount);
        return Math.max(b,a-amount);
    }

    private void drawWorld(Canvas c) {
        int w=getWidth(), h=getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF08100D); c.drawRect(0,0,w,h,p);

        // sky
        p.setColor(0xFF0B0D18); c.drawRect(0,0,w,h*0.43f,p);
        // distant horizon glow
        p.setColor(0xFF181A2A); c.drawRect(0,h*0.36f,w,h*0.48f,p);

        float horizon=h*0.40f;
        Path road=new Path();
        road.moveTo(w*0.44f,horizon);
        road.lineTo(w*0.56f,horizon);
        road.lineTo(w*0.91f,h);
        road.lineTo(w*0.09f,h);
        road.close();
        p.setColor(0xFF24242A); c.drawPath(road,p);

        // shoulders
        p.setColor(0xFF6D6674);
        drawQuad(c,w*0.435f,horizon,w*0.455f,horizon,w*0.075f,h,w*0.12f,h);
        drawQuad(c,w*0.545f,horizon,w*0.565f,horizon,w*0.88f,h,w*0.925f,h);

        // center line
        p.setColor(0xFFFFE9A6);
        for(int i=0;i<9;i++) {
            float t=((i/9f)+roadScroll)%1f;
            float y=horizon+(float)Math.pow(t,1.55)*h*0.64f;
            float width=2+t*7;
            c.drawRect(w/2-width,y,w/2+width,y+5+t*12,p);
        }

        // lane guide / edge dashes
        p.setColor(0xFFDDDDDD);
        for(int side=-1;side<=1;side+=2) {
            for(int i=0;i<8;i++) {
                float t=((i/8f)+roadScroll*0.9f)%1f;
                float y=horizon+(float)Math.pow(t,1.55)*h*0.64f;
                float x=w/2+side*(w*(0.055f+t*0.405f));
                float s=2+t*4;
                c.drawRect(x-s,y,x+s,y+4+t*7,p);
            }
        }

        // traffic cars, far to near
        ArrayList<Car> sorted=new ArrayList<>(cars);
        Collections.sort(sorted,(a,b)->Float.compare(b.z,a.z));
        for(Car car:sorted) drawCar(c,car);

        drawPlayer(c);
        drawHud(c);

        if(crashed) drawCrash(c);
    }

    private void drawCar(Canvas c,Car car) {
        int w=getWidth(),h=getHeight();
        float z=Math.max(0,Math.min(1,car.z/3.4f));
        float y=h*0.40f+(float)Math.pow(z,1.55)*h*0.62f;
        float laneOffset=car.x;
        float x=w/2+laneOffset*w*(0.18f+z*1.15f);
        float scale=0.30f+z*0.95f;
        float cw=22*scale, ch=42*scale;
        if(!car.sameDirection) ch*=1.03f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(car.color);
        c.drawRoundRect(x-cw,y-ch/2,x+cw,y+ch/2,6*scale,6*scale,p);

        p.setColor(0xFF11131A);
        c.drawRoundRect(x-cw*0.68f,y-ch*0.36f,x+cw*0.68f,y-ch*0.03f,3*scale,3*scale,p);
        p.setColor(0xFF242733);
        c.drawRoundRect(x-cw*0.60f,y+ch*0.04f,x+cw*0.60f,y+ch*0.31f,3*scale,3*scale,p);

        p.setColor(car.sameDirection?0xFFFF3B45:0xFFFFF0D0);
        float light=2.4f*scale;
        c.drawCircle(x-cw*0.57f,y+ch*0.39f,light,p);
        c.drawCircle(x+cw*0.57f,y+ch*0.39f,light,p);

        p.setColor(0xFF08090D);
        c.drawRect(x-cw*1.02f,y-ch*0.28f,x-cw*0.78f,y+ch*0.27f,p);
        c.drawRect(x+cw*0.78f,y-ch*0.28f,x+cw*1.02f,y+ch*0.27f,p);
    }

    private void drawPlayer(Canvas c) {
        int w=getWidth(),h=getHeight();
        float x=w/2+playerX*w*0.42f;
        float y=h*0.83f;
        float cw=42,ch=82;
        p.setColor(0xFFB87CFF);
        c.drawRoundRect(x-cw,y-ch/2,x+cw,y+ch/2,14,14,p);
        p.setColor(0xFF15121E);
        c.drawRoundRect(x-cw*0.67f,y-ch*0.32f,x+cw*0.67f,y-ch*0.02f,8,8,p);
        p.setColor(0xFF272231);
        c.drawRoundRect(x-cw*0.60f,y+ch*0.05f,x+cw*0.60f,y+ch*0.30f,7,7,p);
        p.setColor(0xFFFF3C63);
        c.drawCircle(x-cw*0.57f,y+ch*0.42f,4,p);
        c.drawCircle(x+cw*0.57f,y+ch*0.42f,4,p);
        p.setColor(0xFF09090E);
        c.drawRect(x-cw*1.05f,y-ch*0.27f,x-cw*0.82f,y+ch*0.25f,p);
        c.drawRect(x+cw*0.82f,y-ch*0.27f,x+cw*1.05f,y+ch*0.25f,p);
    }

    private void drawHud(Canvas c) {
        int w=getWidth();
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(16); p.setColor(Color.WHITE);
        c.drawText("GHOSTLY ROAD",24,34,p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(13); p.setColor(0xFFB9B1C5);
        c.drawText("DISTANCE  "+score+" m",24,56,p);
        c.drawText("BEST  "+best+" m",24,75,p);

        p.setColor(0xFFB87CFF);
        c.drawRoundRect(w-138,20,w-20,67,16,16,p);
        p.setColor(Color.WHITE); p.setTextSize(12);
        c.drawText("AI TRAFFIC",w-115,49,p);
    }

    private void drawCrash(Canvas c) {
        int w=getWidth(),h=getHeight();
        p.setColor(0xCC050509);
        c.drawRect(0,0,w,h,p);
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(34); p.setColor(Color.WHITE);
        c.drawText("АВАРИЯ",w/2,h*0.39f,p);
        p.setTextSize(18); p.setColor(0xFFB87CFF);
        c.drawText("Дистанция: "+score+" м",w/2,h*0.45f,p);
        p.setTextSize(14); p.setColor(0xFFB9B1C5);
        c.drawText("Свайпни или коснись экрана, чтобы начать заново",w/2,h*0.51f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawQuad(Canvas c,float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4) {
        Path q=new Path(); q.moveTo(x1,y1); q.lineTo(x2,y2); q.lineTo(x3,y3); q.lineTo(x4,y4); q.close(); c.drawPath(q,p);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN) {
            touchDownX=e.getX(); touchDownTime=System.currentTimeMillis();
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP) {
            float dx=e.getX()-touchDownX;
            if(crashed) {
                crashed=false; distance=0; score=0; playerX=0; resetTraffic(); lastTime=System.nanoTime();
                return true;
            }
            if(Math.abs(dx)>25) playerX=Math.max(-0.48f,Math.min(0.48f,playerX+dx/getWidth()*1.25f));
            else playerX=0;
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE && !crashed) {
            float dx=e.getX()-touchDownX;
            playerX=Math.max(-0.48f,Math.min(0.48f,playerX+dx/getWidth()*0.035f));
            touchDownX=e.getX();
            return true;
        }
        return true;
    }

    private static final class Car {
        boolean sameDirection;
        int lane,targetLane,state;
        float x,z,speed,timer;
        int color;
        Car(boolean same,int lane){this.sameDirection=same;this.lane=lane;this.targetLane=lane;}
    }
}
