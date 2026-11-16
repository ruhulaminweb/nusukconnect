package com.nusukconnect.companion;

import android.content.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class Store {
    private final SharedPreferences p;
    Store(Context c) { p = c.getSharedPreferences("companion", Context.MODE_PRIVATE); day(); }
    String get(String k, String d) { return p.getString(k, d); }
    int number(String k, int d) { return p.getInt(k, d); }
    boolean flag(String k) { return p.getBoolean(k, false); }
    void remove(String k) { p.edit().remove(k).apply(); }
    boolean online() { return p.getBoolean("online",true); }
    boolean limitEnabled() { return p.getBoolean("limitEnabled",true); }
    void choices(List<String> options) { put("choices",new JSONArray(options).toString()); }
    List<String> choices() {
        List<String> result=new ArrayList<>();
        try { JSONArray array=new JSONArray(get("choices","[]"));for(int i=0;i<array.length();i++)result.add(array.getString(i)); }
        catch(JSONException ignored) {}
        return result;
    }
    void research(String question,ResearchMatcher.Decision result) {
        StringBuilder text=new StringBuilder(question).append("\n\n");
        text.append(result.reason().startsWith("ChatGPT selected option")?"ChatGPT choice: "+result.answer():result.supported()?"Confirmed option: "+result.answer():result.hasAnswer()?"Unconfirmed guess: "+result.answer()+"\n"+result.reason():"Paused: "+result.reason());
        if(!result.excerpt().isEmpty())text.append("\n\nSupporting excerpt: ").append(result.excerpt());
        text.append("\n\nReferences:\n");
        for(ResearchMatcher.Source source:result.sources())text.append(source.title()).append("\n").append(source.url()).append("\n");
        put("research",text.toString());
    }
    void workflow(int step,String message) {
        p.edit().putInt("workflowSteps",number("workflowSteps",0)|step).commit();
        status(message);
    }
    void generated(String code) {
        p.edit().putInt("workflowSteps",0).putString("cycleCode",code).commit();
        workflow(1,"Workflow: fresh code generated and Copy Code pressed.");
    }
    void correctResult() {
        workflow(64,"Nusuk confirmed the answer is correct.");
        if(number("workflowSteps",0)==127) {
            String code=get("cycleCode","");Set<String> done=new HashSet<>(p.getStringSet("completedCodes",Collections.emptySet()));
            if(!code.isEmpty()&&done.add(code)) {
                p.edit().putStringSet("completedCodes",done).putInt("completedCycles",number("completedCycles",0)+1).commit();
                status("WORKFLOW COMPLETE: code generated, key received, quiz opened, online answer selected, submitted and confirmed correct.");
            }
        }
    }
    String workflowSummary() {
        String[] steps={"Code generated and copied","Key received","Quiz read","Answer researched","Choice selected","Answer submitted","Correct result confirmed"};
        int mask=number("workflowSteps",0);StringBuilder result=new StringBuilder("Verified full cycles: ").append(number("completedCycles",0)).append("\n");
        for(int i=0;i<steps.length;i++)result.append((mask&(1<<i))!=0?"✓ ":"○ ").append(steps[i]).append("\n");
        return result.toString();
    }
    void put(String k, String v) { p.edit().putString(k, v).apply(); }
    void put(String k, int v) { p.edit().putInt(k, v).apply(); }
    void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    void day() {
        String today = LocalDate.now().toString();
        if(!today.equals(get("logDay",get("day",""))))p.edit().putString("log","").apply();
        if(!today.equals(get("logDay","")))put("logDay",today);
        if (!today.equals(get("day", ""))) {
            p.edit().putString("day", today).putInt("attempts",0).putInt("accepted",0)
                .putInt("quizAttempts",0).putInt("correct",0).putInt("wrong",0).putInt("rejected",0).apply();
        }
    }
    void clearLog() {
        p.edit().putString("log","").putString("logDay",LocalDate.now().toString()).apply();
    }
    void increment(String k) { day(); put(k, number(k,0)+1); }
    void acceptCode(boolean quizDue) {
        p.edit().putBoolean("pendingCode",false).putBoolean("quizDue",quizDue)
            .putInt("accepted",number("accepted",0)+1).commit();
    }
    void rejectCode() {
        p.edit().putBoolean("pendingCode",false).putString("code","")
            .putInt("rejected",number("rejected",0)+1).commit();
    }
    void leaveUnknownCodeResult() {
        // The result disappeared during navigation. Keep the attempt and used-code history;
        // neither acceptance nor rejection can be credited without Nusuk's result.
        p.edit().putBoolean("pendingCode",false).putString("code","").putBoolean("quizDue",false)
            .putInt("workflowSteps",number("workflowSteps",0)&~2).commit();
        status("Code result is no longer shown. Leaving it unconfirmed and continuing; the old code will not be resubmitted.");
    }
    void submission(boolean quiz) {
        String pending=quiz?"pendingQuiz":"pendingCode", counter=quiz?"quizAttempts":"attempts";
        // Persist before acting. After process death an uncertain result is checked, never retried.
        SharedPreferences.Editor edit=p.edit().putBoolean(pending,true).putInt(counter,number(counter,0)+1);
        if (!quiz) {
            Set<String> used=new HashSet<>(p.getStringSet("usedCodes",Collections.emptySet()));
            used.add(get("code","")); edit.putStringSet("usedCodes",used);
        }
        edit.commit();
    }
    boolean hasCode(String code) { return p.getStringSet("usedCodes",Collections.emptySet()).contains(code); }
    void manualSubmissionObserved() {
        SharedPreferences.Editor edit=p.edit().putBoolean("pendingQuiz",true).putBoolean("manualAnswer",false)
            .putBoolean("openQuestion",false).putInt("workflowSteps",(number("workflowSteps",0)&~8)|32);
        if(!flag("pendingQuiz"))edit.putInt("quizAttempts",number("quizAttempts",0)+1);
        edit.commit();
        status("Your submitted quiz result was observed. Checking the outcome before continuing.");
    }
    void cancelSubmission(boolean quiz) {
        String pending=quiz?"pendingQuiz":"pendingCode", counter=quiz?"quizAttempts":"attempts";
        p.edit().putBoolean(pending,false).putInt(counter,Math.max(0,number(counter,0)-1)).commit();
    }
    void status(String text) {
        put("status", text);
        String line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + text;
        String history = line + "\n" + get("log", "");
        put("log", history.substring(0, Math.min(history.length(), 12000)));
    }
    Map<String,String> answers() {
        Map<String,String> map = new LinkedHashMap<>(Rules.seeds());
        try {
            JSONObject j = new JSONObject(get("answers", "{}"));
            Iterator<String> keys = j.keys();
            while (keys.hasNext()) { String key=keys.next(); map.put(key, j.getString(key)); }
        } catch (JSONException ignored) {}
        return map;
    }
    void teach(String question, String answer) {
        Map<String,String> map=answers(); map.put(Rules.normalize(question),answer.trim());
        put("answers",new JSONObject(map).toString());
    }
    int quizDoor() {
        int selected=number("door",0);
        if(selected!=4)return Math.max(0,Math.min(3,selected));
        int index=number("randomDoorIndex",0);
        String saved=get("randomDoorOrder","");
        if(index<0||index>=4){index=0;saved="";}
        List<Integer> order=Rules.doorOrder(saved,new Random());
        StringJoiner joined=new StringJoiner(",");for(int door:order)joined.add(""+door);
        p.edit().putString("randomDoorOrder",joined.toString()).putInt("randomDoorIndex",index).commit();
        return order.get(index);
    }
    void doorSelected(int door) {
        p.edit().putBoolean("randomDoorPending",number("door",0)==4).commit();
        status("Selected quiz door: "+Rules.doorPrefix(door));
    }
    void doorOpened() {
        if(flag("randomDoorPending")) {
            p.edit().putBoolean("randomDoorPending",false).putInt("randomDoorIndex",number("randomDoorIndex",0)+1).commit();
        }
    }
}
