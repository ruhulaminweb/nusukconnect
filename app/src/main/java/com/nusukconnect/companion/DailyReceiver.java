package com.nusukconnect.companion;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.Calendar;

public final class DailyReceiver extends BroadcastReceiver {
    static void schedule(Context c) {
        Store s=new Store(c);
        AlarmManager alarm=c.getSystemService(AlarmManager.class);
        PendingIntent pi=PendingIntent.getBroadcast(c, 1, new Intent(c,DailyReceiver.class).setAction("DAILY"), PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        alarm.cancel(pi);
        if (!s.flag("daily")) return;
        Calendar next=Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY,s.number("hour",9)); next.set(Calendar.MINUTE,s.number("minute",0));
        next.set(Calendar.SECOND,0); next.set(Calendar.MILLISECOND,0);
        if (next.getTimeInMillis()<=System.currentTimeMillis()) next.add(Calendar.DATE,1);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),pi);
        s.put("nextRun",next.getTimeInMillis()+"");
    }
    @Override public void onReceive(Context c,Intent intent) {
        if ("STOP".equals(intent.getAction())) {
            if (CompanionService.instance!=null) CompanionService.instance.pause("Stopped from notification.");
            return;
        }
        Store s=new Store(c);
        if (!s.flag("daily")) return;
        schedule(c);
        KeyguardManager keys=c.getSystemService(KeyguardManager.class);
        PowerManager power=c.getSystemService(PowerManager.class);
        if (!keys.isKeyguardLocked() && power.isInteractive() && CompanionService.instance!=null) {
            CompanionService.instance.begin();
        } else {
            s.status("Daily run is ready. Unlock the phone and tap Submit.");
            notify(c,"Daily run ready","Unlock your phone and tap Submit.",false);
        }
    }
    static void notify(Context c,String title,String message,boolean running) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("runs","NusukConnect activity",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(c,"runs").setSmallIcon(com.nusukconnect.companion.R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(open).setOngoing(running).setOnlyAlertOnce(running);
        if (running) {
            PendingIntent stop=PendingIntent.getBroadcast(c,2,new Intent(c,DailyReceiver.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null,"Stop",stop).build());
        }
        try { nm.notify(7,b.build()); } catch (SecurityException ignored) {}
    }
}
