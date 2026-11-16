package com.nusukconnect.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int GREEN=Color.rgb(16,77,59), INK=Color.rgb(27,46,37), MUTED=Color.rgb(103,111,101), PAPER=Color.rgb(247,245,236);
    private static final int HOME=0,TASK=1,DASHBOARD=2,SETTINGS=3,ABOUT=4;
    private static final String[] TABS={"Home","Task","Dashboard","Settings","About"};
    private Store store;
    private LinearLayout page;
    private ViewFlipper pages;
    private final Button[] tabButtons=new Button[5];
    private int selectedTab;
    private TextView status,counts,connection,homeTask,log,scheduleText,references,workflow,taskDescription,doorLabel,limitLabel;
    private TextView dashboardState,dashboardDate,codeRateText,quizRateText;
    private final TextView[] metrics=new TextView[6];
    private ProgressBar codeRate,quizRate;
    private EditText limit;
    private Spinner mode,door,confirmAnswer,limitCheck;
    private Switch daily,online;
    private AlertDialog answerConfirmation;
    private String shownApproval="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshLoop=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1500);}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new Store(this);
        getWindow().setStatusBarColor(GREEN);getWindow().setNavigationBarColor(PAPER);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(PAPER);
        pages=new ViewFlipper(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<TABS.length;i++) {
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setFocusableInTouchMode(true);scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(24),dp(22),dp(28));
            scroll.addView(content);pages.addView(scroll);
        }
        LinearLayout navigation=new LinearLayout(this);navigation.setPadding(dp(4),dp(5),dp(4),dp(5));navigation.setBackgroundColor(Color.WHITE);
        for(int i=0;i<TABS.length;i++) {
            final int index=i;Button tab=new Button(this);tab.setText(TABS[i]);tab.setAllCaps(false);tab.setTextSize(11);tab.setMinWidth(0);tab.setMinimumWidth(0);tab.setPadding(0,0,0,0);
            tab.setOnClickListener(v->selectTab(index));tabButtons[i]=tab;
            navigation.addView(tab,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        root.addView(navigation);setContentView(root);
        buildHome();buildTask();buildDashboard();buildSettings();buildAbout();
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){refresh();}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        daily.setOnCheckedChangeListener((view,checked)->{if(save())refresh();});
        selectTab(state==null?HOME:state.getInt("tab",HOME));refresh();
    }
    private void usePage(int index) {page=(LinearLayout)((ScrollView)pages.getChildAt(index)).getChildAt(0);}
    private void pageTitle(String title,String subtitle) {
        TextView label=text(title,28,INK);label.setTypeface(null,Typeface.BOLD);page.addView(label);
        space(page,6);page.addView(text(subtitle,14,MUTED));space(page,20);
    }
    private void buildHome() {
        usePage(HOME);
        TextView brand=text("NusukConnec",30,GREEN);brand.setTypeface(null,Typeface.BOLD);page.addView(brand);
        space(page,4);page.addView(text("By ruhulamin34@gmail.com",14,MUTED));
        space(page,14);TextView title=text("Your task : done by Apps",22,INK);title.setTypeface(null,Typeface.BOLD);page.addView(title);
        space(page,6);page.addView(text("Generate Code, Add Keys, Find Quiz Answer through ChatGPT and Submit",15,MUTED));space(page,20);
        LinearLayout stateCard=card();connection=text("Checking connection…",13,GREEN);connection.setTypeface(null,Typeface.BOLD);stateCard.addView(connection);
        space(stateCard,8);status=text("Ready",18,INK);stateCard.addView(status);space(stateCard,12);counts=text("",14,MUTED);stateCard.addView(counts);space(page,16);
        LinearLayout controls=card();heading(controls,"Ready when you are");homeTask=text("",14,MUTED);controls.addView(homeTask);
        button(controls,"Start task",GREEN,Color.WHITE,this::startTask);button(controls,"Stop",Color.rgb(237,230,211),INK,this::stopTask);
        button(controls,"Choose task",Color.rgb(237,230,211),INK,()->selectTab(TASK));
        space(page,16);page.addView(text("Keep the phone unlocked. Review your task and settings before starting. The About tab explains automation, AI guesses and privacy.",13,MUTED));
    }
    private void buildTask() {
        usePage(TASK);pageTitle("Task","Choose the work and follow its progress.");
        LinearLayout task=card();heading(task,"What to do");
        mode=spinner(new String[]{"Full workflow: codes + quizzes","Redeem codes only","Answer quizzes with existing keys"});mode.setSelection(store.number("mode",0));task.addView(mode);
        space(task,8);doorLabel=text("Quiz door",14,INK);task.addView(doorLabel);
        door=spinner(new String[]{"The Prophet's","The Quran","Makkah and the…","Madinah and the…","Random — rotate all four"});door.setSelection(store.number("door",0));task.addView(door);
        space(task,8);taskDescription=text("",13,MUTED);task.addView(taskDescription);
        button(task,"Submit",GREEN,Color.WHITE,this::startTask);button(task,"Stop",Color.rgb(237,230,211),INK,this::stopTask);
        task.addView(text("Submit applies your task and settings, then resumes waiting work or starts a fresh cycle. Stop pauses the run.",12,MUTED));space(page,16);
        LinearLayout progress=card();heading(progress,"Workflow");workflow=text("",14,GREEN);progress.addView(workflow);space(page,16);
        LinearLayout answer=card();heading(answer,"Latest answer");references=text("",14,MUTED);references.setTextIsSelectable(true);answer.addView(references);
        button(answer,"Answer and confirmation settings",Color.rgb(237,230,211),INK,()->selectTab(SETTINGS));
    }
    private void buildDashboard() {
        usePage(DASHBOARD);pageTitle("Dashboard","A simple view of the activity recorded on this phone.");
        dashboardDate=text("",13,MUTED);page.addView(dashboardDate);space(page,12);
        String[] titles={"Codes accepted","Code attempts","Correct answers","Wrong answers","Quiz submissions","Verified cycles"};
        for(int row=0;row<3;row++) {
            LinearLayout pair=new LinearLayout(this);
            for(int col=0;col<2;col++) {
                int index=row*2+col;LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(14),dp(14),dp(14),dp(14));tile.setBackground(background(Color.WHITE,14));
                tile.addView(text(titles[index],12,MUTED));metrics[index]=text("0",28,GREEN);metrics[index].setTypeface(null,Typeface.BOLD);tile.addView(metrics[index]);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);if(col==0)params.rightMargin=dp(10);pair.addView(tile,params);
            }
            page.addView(pair,new LinearLayout.LayoutParams(-1,-2));space(page,10);
        }
        page.addView(text("Verified cycles is the saved total across runs; the other counts are for today.",12,MUTED));space(page,16);
        LinearLayout rates=card();heading(rates,"Today at a glance");
        codeRateText=text("",13,INK);rates.addView(codeRateText);codeRate=progressBar(rates);space(rates,14);
        quizRateText=text("",13,INK);rates.addView(quizRateText);quizRate=progressBar(rates);space(rates,14);
        dashboardState=text("",13,MUTED);rates.addView(dashboardState);space(page,16);
        LinearLayout activity=card();heading(activity,"Activity log");log=text("",12,MUTED);log.setTextIsSelectable(true);log.setVisibility(View.GONE);
        Button expand=new Button(this);expand.setText("Show activity log");expand.setAllCaps(false);activity.addView(expand);
        expand.setOnClickListener(v->{boolean visible=log.getVisibility()==View.VISIBLE;log.setVisibility(visible?View.GONE:View.VISIBLE);expand.setText(visible?"Show activity log":"Hide activity log");});
        button(activity,"Clear log",Color.rgb(237,230,211),INK,()->{store.clearLog();refresh();toast("Log cleared. Counts are unchanged.");});
        activity.addView(text("Clearing the log keeps daily counts and pending work. A fresh log starts on the next local date.",12,MUTED));space(activity,8);activity.addView(log);
    }
    private void buildSettings() {
        usePage(SETTINGS);pageTitle("Settings","Configure limits, answer review and the daily routine.");
        LinearLayout limits=card();heading(limits,"Daily limit");limits.addView(text("Daily code limit check",14,INK));
        limitCheck=spinner(new String[]{"Yes","No"});limitCheck.setSelection(store.limitEnabled()?0:1);limits.addView(limitCheck);
        limitLabel=text("Daily limit · 1 to 349 attempts",14,INK);limits.addView(limitLabel);
        limit=input("Start with 1 for a trial run");limit.setInputType(InputType.TYPE_CLASS_NUMBER);limit.setText(""+store.number("limit",1));limits.addView(limit);
        limits.addView(text("No hides the number and disables the companion's daily cap. Today's counts are still recorded.",12,MUTED));
        limitCheck.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){updateLimitVisibility();}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });updateLimitVisibility();space(page,16);
        LinearLayout review=card();heading(review,"Answer confirmation");review.addView(text("User confirmation before submitting an answer",14,INK));
        confirmAnswer=spinner(new String[]{"No","Yes"});confirmAnswer.setSelection(store.flag("confirmAnswer")?1:0);review.addView(confirmAnswer);
        review.addView(text("No submits automatically. Yes asks before each submission. In that dialog, No returns to Nusuk so you can change the suggestion and submit yourself. Automation continues after your result.",13,MUTED));space(page,16);
        LinearLayout ai=card();heading(ai,"ChatGPT");online=new Switch(this);online.setText("Ask ChatGPT in Temporary Chat");online.setTextColor(INK);online.setChecked(store.online());ai.addView(online);
        ai.addView(text("Sends the question and four choices only after verifying Temporary Chat. No Google or reference-site lookup is used. Sign into ChatGPT yourself.",13,MUTED));space(ai,8);
        ai.addView(text("If ChatGPT is unavailable or does not return a usable answer within about a minute, the app tries a local guess and continues after either quiz result. AI answers can be wrong.",13,MUTED));space(page,16);
        LinearLayout schedule=card();heading(schedule,"Daily routine");daily=new Switch(this);daily.setText("Enable daily routine");daily.setTextColor(INK);daily.setChecked(store.flag("daily"));schedule.addView(daily);
        scheduleText=text("",14,MUTED);schedule.addView(scheduleText);
        button(schedule,"Choose daily time",Color.rgb(237,230,211),INK,()->new TimePickerDialog(this,(view,h,m)->{store.put("hour",h);store.put("minute",m);if(save())refresh();},store.number("hour",9),store.number("minute",0),true).show());
        schedule.addView(text("Android may delay the time. Start with the phone unlocked and in use; otherwise a notification asks you to start. Realme battery controls can interrupt the routine.",13,MUTED));space(page,16);
        LinearLayout connectionCard=card();heading(connectionCard,"Connection");
        button(connectionCard,"Enable Accessibility",Color.rgb(237,230,211),INK,this::showAccessibilityDisclosure);
        button(connectionCard,"Allow notifications",Color.rgb(237,230,211),INK,()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
            else toast("Notifications are already allowed, or managed in Android settings.");
        });space(page,16);
        LinearLayout recovery=card();heading(recovery,"Submission recovery");recovery.addView(text("A pending submission is checked rather than repeated. Clear its pending check only after inspecting Nusuk yourself; no success will be credited.",13,MUTED));
        button(recovery,"Clear a dismissed pending result",Color.rgb(237,230,211),INK,()->new AlertDialog.Builder(this).setTitle("Clear unverified result?")
            .setMessage("Only after checking Nusuk yourself. No success is recorded. The waiting question and manual-review state will also be cleared.")
            .setNegativeButton("Cancel",null).setPositiveButton("Clear",(dialog,which)->{
                if(CompanionService.instance!=null&&CompanionService.instance.isRunning()){toast("Stop the run first.");return;}
                store.put("pendingCode",false);store.put("pendingQuiz",false);store.put("openQuestion",false);store.put("manualAnswer",false);
                store.status("Pending result cleared by user; outcome unverified.");refresh();
            }).show());space(page,16);
        button(page,"Save settings",GREEN,Color.WHITE,()->{if(save()){refresh();toast("Settings saved.");}});
        page.addView(text("You can also apply these selections by starting a task. Changes during a run apply on its next start.",12,MUTED));
    }
    private void buildAbout() {
        usePage(ABOUT);pageTitle("About","NusukConnec · Version "+versionName());
        LinearLayout author=card();heading(author,"NusukConnec");author.addView(text("By ruhulamin34@gmail.com",15,INK));space(author,8);
        author.addView(text("Your task : done by Apps",18,GREEN));space(author,6);author.addView(text("Generate Code, Add Keys, Find Quiz Answer through ChatGPT and Submit",14,MUTED));space(page,16);
        LinearLayout disclaimer=card();heading(disclaimer,"Disclaimer");
        note(disclaimer,"Independent companion","NusukConnec is an independent automation helper. It is not an official Nusuk, Nusuk Codes or OpenAI product, and is not affiliated with or endorsed by their owners.");
        note(disclaimer,"AI answers and guesses","ChatGPT replies and fallback guesses can be wrong. Correct answers, rewards, keys and uninterrupted operation are not guaranteed. Enable answer confirmation if you want to review a suggestion before submission.");
        note(disclaimer,"Automation and your account","A run can spend quiz keys and submit answers on your behalf. Use accounts you are authorized to control and follow the target apps' rules. Review your task and settings before starting. Use Stop to pause the run.");
        note(disclaimer,"Privacy","Only quiz questions and choices are sent to ChatGPT through verified Temporary Chat. Generated codes and passwords are not sent. Local settings and run logs remain on this phone. ChatGPT controls how Temporary Chat data is handled; this is not a promise of zero server retention.");
        note(disclaimer,"Device limitations","Accessibility, an unlocked phone and working target apps are required. This app cannot unlock the phone or bypass Nusuk's security restrictions. Android power management, app updates and network conditions can interrupt a run.");
        space(page,16);page.addView(text("The disclaimer is information about how the app works. It does not change your selected task or confirmation preference.",12,MUTED));
    }
    private void note(LinearLayout parent,String title,String body) {TextView label=text(title,15,INK);label.setTypeface(null,Typeface.BOLD);parent.addView(label);space(parent,5);parent.addView(text(body,13,MUTED));space(parent,15);}
    private ProgressBar progressBar(LinearLayout parent) {ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(12)));return bar;}
    private String versionName() {try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException ignored){return "";}}
    private void selectTab(int index) {
        index=Math.max(0,Math.min(TABS.length-1,index));
        if(index!=selectedTab&&getCurrentFocus()!=null){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);keyboard.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);}
        selectedTab=index;pages.setDisplayedChild(index);pages.getChildAt(index).requestFocus();
        for(int i=0;i<tabButtons.length;i++){boolean active=i==index;tabButtons[i].setTextColor(active?Color.WHITE:MUTED);tabButtons[i].setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);tabButtons[i].setBackground(background(active?GREEN:Color.TRANSPARENT,10));tabButtons[i].setSelected(active);tabButtons[i].setContentDescription(TABS[i]+" tab"+(active?", selected":""));}
    }
    private void startTask() {if(!save())return;if(CompanionService.instance==null){showAccessibilityDisclosure();return;}if(CompanionService.instance.isRunning())CompanionService.instance.pause("Applying the selected task.");CompanionService.instance.begin();refresh();}
    private void stopTask() {if(CompanionService.instance!=null)CompanionService.instance.pause("Stopped by you.");else store.status("Stopped.");refresh();}
    private boolean save() {
        boolean checked=limitCheck.getSelectedItemPosition()==0;int value=store.number("limit",1);
        if(checked)try{value=Integer.parseInt(limit.getText().toString().trim());}catch(NumberFormatException error){value=0;}
        if(!Rules.validLimit(checked,value)){selectTab(SETTINGS);limit.setError("Choose 1–349");limit.requestFocus();return false;}
        if(CompanionService.instance!=null&&CompanionService.instance.isRunning())toast("Settings apply on the next run.");
        store.put("limit",value);store.put("limitEnabled",checked);store.put("mode",mode.getSelectedItemPosition());store.put("door",door.getSelectedItemPosition());
        store.put("daily",daily.isChecked());store.put("online",online.isChecked());store.put("confirmAnswer",confirmAnswer.getSelectedItemPosition()==1);DailyReceiver.schedule(this);return true;
    }
    private void updateLimitVisibility() {boolean checked=limitCheck.getSelectedItemPosition()==0;limitLabel.setVisibility(checked?View.VISIBLE:View.GONE);limit.setVisibility(checked?View.VISIBLE:View.GONE);if(!checked)limit.setError(null);}
    private void refresh() {
        if(store==null||status==null||mode==null||limitCheck==null||dashboardState==null)return;
        store.day();CompanionService service=CompanionService.instance;boolean connected=service!=null;
        connection.setText(connected?"● ACCESSIBILITY CONNECTED":"○ SETUP NEEDED · ENABLE ACCESSIBILITY");status.setText(store.get("status","Ready for a trial run"));
        int accepted=store.number("accepted",0),attempts=store.number("attempts",0),correct=store.number("correct",0),submitted=store.number("quizAttempts",0);
        counts.setText("TODAY · "+store.get("day","")+"\n"+accepted+" codes accepted / "+attempts+" attempted\n"+correct+" answers correct / "+submitted+" submitted");
        int selectedMode=mode.getSelectedItemPosition();homeTask.setText("Selected task: "+Rules.taskName(selectedMode));boolean codesOnly=selectedMode==1;
        doorLabel.setVisibility(codesOnly?View.GONE:View.VISIBLE);door.setVisibility(codesOnly?View.GONE:View.VISIBLE);
        taskDescription.setText(switch(selectedMode){case 1->"Generate a code, redeem it in Nusuk, then repeat "+(limitCheck.getSelectedItemPosition()==0?"up to the daily limit.":"until stopped.");case 2->"Use existing keys, ask ChatGPT, submit an answer or guess, then repeat after either result.";default->"Generate a code, add a key, ask ChatGPT and submit an answer or guess, then repeat.";});
        if(!codesOnly&&door.getSelectedItemPosition()==4)taskDescription.append(" Random visits all four doors in a shuffled rotation.");
        int steps=store.number("workflowSteps",0);workflow.setText("Task: "+Rules.taskName(selectedMode)+"\n"+(codesOnly?((steps&1)!=0?"✓ ":"○ ")+"Code generated and copied\n"+((steps&2)!=0?"✓ ":"○ ")+"Key received":store.workflowSummary()));
        scheduleText.setText(String.format(Locale.getDefault(),"Daily time: %02d:%02d · phone's local time",store.number("hour",9),store.number("minute",0)));
        references.setText(store.get("research","No ChatGPT answers yet."));log.setText(store.get("log","No runs yet."));
        dashboardDate.setText("TODAY · "+store.get("day",""));int[] values={accepted,attempts,correct,store.number("wrong",0),submitted,store.number("completedCycles",0)};
        for(int i=0;i<values.length;i++)metrics[i].setText(String.valueOf(values[i]));
        int codePercent=attempts<=0?0:Math.max(0,Math.min(100,Math.round(100f*accepted/attempts))),quizPercent=submitted<=0?0:Math.max(0,Math.min(100,Math.round(100f*correct/submitted)));
        codeRate.setProgress(codePercent);quizRate.setProgress(quizPercent);codeRateText.setText("Codes accepted: "+accepted+" / "+attempts+" ("+codePercent+"%)");quizRateText.setText("Correct per quiz submission: "+correct+" / "+submitted+" ("+quizPercent+"%)");
        String run=service!=null&&service.isRunning()?"Running · "+service.stage().replace('_',' ').toLowerCase(Locale.ROOT):"Not running";
        String cap=store.limitEnabled()?"Code attempts remaining today: "+Math.max(0,store.number("limit",1)-attempts):"Daily limit check: No";
        dashboardState.setText(run+"\n"+cap+(store.flag("pendingCode")?"\nWaiting for code result":"")+(store.flag("pendingQuiz")?"\nWaiting for quiz result":""));
        showPendingAnswerConfirmation();
    }
    private void showPendingAnswerConfirmation() {
        CompanionService service=CompanionService.instance;String token=service==null?"":service.pendingApprovalToken();
        if(token.isEmpty()){if(answerConfirmation!=null&&answerConfirmation.isShowing())answerConfirmation.dismiss();shownApproval="";return;}
        if(token.equals(shownApproval)&&answerConfirmation!=null&&answerConfirmation.isShowing())return;
        if(answerConfirmation!=null)answerConfirmation.dismiss();shownApproval=token;
        TextView message=text(service.pendingApprovalText(),16,INK);message.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(message);
        answerConfirmation=new AlertDialog.Builder(this).setTitle("Confirm answer submission").setView(scroll).setCancelable(false)
            .setPositiveButton("Yes, submit",(dialog,which)->{service.approveAnswer(token,true);finish();})
            .setNegativeButton("No, I'll submit",(dialog,which)->{service.approveAnswer(token,false);finish();}).create();answerConfirmation.show();
    }
    private void showAccessibilityDisclosure() {
        new AlertDialog.Builder(this).setTitle("Allow NusukConnec to operate Nusuk, Codes and ChatGPT?")
            .setMessage("The Accessibility service operates Nusuk, Nusuk Codes and ChatGPT: it enters generated codes, spends quiz keys and submits answers on your behalf. AI mode verifies Temporary Chat before sending the question and four choices, then reads the reply. No prompt is sent if Temporary Chat is not verified. Sign into ChatGPT yourself. Generated codes and passwords are not sent. Stop from the app or its notification, or disable Accessibility in Android settings.\n\nEnable NusukConnec yourself on the next screen. If Android shows Restricted settings, follow Android's app-info instructions.")
            .setNegativeButton("Cancel",null).setPositiveButton("Open settings",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private GradientDrawable background(int color,int radius) {GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private LinearLayout card() {LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable shape=background(Color.WHITE,18);shape.setStroke(dp(1),Color.rgb(229,224,207));box.setBackground(shape);page.addView(box);return box;}
    private void heading(LinearLayout parent,String title) {TextView label=text(title,19,INK);label.setTypeface(null,Typeface.BOLD);parent.addView(label);space(parent,12);}
    private TextView text(String value,int size,int color) {TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);label.setLineSpacing(dp(3),1);return label;}
    private EditText input(String hint) {EditText edit=new EditText(this);edit.setHint(hint);edit.setTextColor(INK);edit.setTextSize(16);edit.setSingleLine(false);edit.setPadding(dp(8),dp(8),dp(8),dp(8));return edit;}
    private Spinner spinner(String[] values) {Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));spinner.setMinimumHeight(dp(48));return spinner;}
    private void button(LinearLayout parent,String title,int background,int foreground,Runnable action) {Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextColor(foreground);button.setTextSize(15);button.setBackground(background(background,12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(49));params.topMargin=dp(9);parent.addView(button,params);button.setOnClickListener(view->action.run());}
    private void space(LinearLayout parent,int height) {View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(int number) {return (int)(number*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle state) {state.putInt("tab",selectedTab);super.onSaveInstanceState(state);}
    @Override public void onBackPressed() {if(selectedTab!=HOME)selectTab(HOME);else super.onBackPressed();}
    @Override protected void onResume() {super.onResume();handler.post(refreshLoop);}
    @Override protected void onPause() {handler.removeCallbacks(refreshLoop);super.onPause();}
    @Override protected void onDestroy() {if(answerConfirmation!=null)answerConfirmation.dismiss();super.onDestroy();}
}
