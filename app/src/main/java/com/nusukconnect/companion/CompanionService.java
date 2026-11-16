package com.nusukconnect.companion;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.content.Intent;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;

public final class CompanionService extends AccessibilityService {
    static final String SOURCE="com.arshad.nusukcodes", NUSUK="com.moh.nusukapp", CHATGPT="com.openai.chatgpt";
    static CompanionService instance;
    private Store store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running;
    private String stage="IDLE", code="", previousCode="", selectedAnswer="", runDay="";
    private long enteredAt;
    private int target, mode;
    private boolean limitEnabled;
    private String lookupQuestion="";
    private List<String> lookupChoices=List.of();
    private ResearchMatcher.Decision researchedAnswer;
    private QuizViewport answerViewport;
    private boolean viewportAction, lastScrollUsedAction, keysPageSeen, codeTextVerified;
    private QuizScan quizScan=new QuizScan();
    private String stableQuiz="",approvalToken="";
    private long stableQuizAt;
    private boolean confirmBeforeAnswer,answerApproved;
    private boolean approvalDiagnostic;
    private ManualAnswer manualAnswer;
    private String lastReveal="";
    private long lastScrollAt;
    private String aiRequestId="",aiPrompt="";
    private long aiStartedAt,aiCandidateAt;
    private int aiCandidate=-1;
    private boolean aiTemporaryConfirmed;
    private java.util.function.Consumer<ResearchMatcher.Decision> aiDiagnostic;
    private PowerManager.WakeLock screenLock;
    private final Runnable loop=this::tick;

    @Override protected void onServiceConnected() {
        instance=this; store=new Store(this);
        store.status("Accessibility connected. Ready to start.");
    }
    boolean isRunning() { return running; }
    String stage() { return stage; }
    void begin() {
        if (running) return;
        if (store==null) store=new Store(this);
        store.day(); target=store.number("limit",1); limitEnabled=store.limitEnabled();mode=store.number("mode",0);
        PowerManager power=getSystemService(PowerManager.class);
        if (getSystemService(KeyguardManager.class).isKeyguardLocked() || !power.isInteractive()) { pause("Unlock the phone before starting."); return; }
        if (!Rules.validLimit(limitEnabled,target)) { pause("Choose a daily limit from 1 to 349."); return; }
        runDay=store.get("day","");
        confirmBeforeAnswer=store.flag("confirmAnswer");answerApproved=false;approvalToken="";
        answerViewport=null;lastReveal="";lastScrollAt=0;
        enteredAt=SystemClock.elapsedRealtime();
        running=true;
        // No wake-up flag is used: this only prevents timeout after the user has unlocked the phone.
        screenLock=power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,"NusukConnec:activeRun");
        screenLock.setReferenceCounted(false);screenLock.acquire(6*60*60*1000L);
        store.status("Selected task: "+Rules.taskName(mode));
        boolean reusableCode=!Rules.limitReached(limitEnabled,store.number("attempts",0),target) && !store.get("code","").isEmpty()
            && !store.hasCode(store.get("code",""));
        String first=Rules.initialStage(mode,store.flag("pendingCode"),store.flag("pendingQuiz"),store.flag("openQuestion"),reusableCode);
        if(mode!=1 && !store.flag("pendingCode") && !store.flag("pendingQuiz") && store.flag("manualAnswer") && store.flag("openQuestion")) {
            first="MANUAL_RETURN";manualAnswer=new ManualAnswer(true);
        }
        if(first.equals("SOURCE_GENERATE") && mode==0 && store.flag("quizDue") && !Rules.limitReached(limitEnabled,store.number("quizAttempts",0),target))first="NAV_QUIZ";
        if(first.equals("SOURCE_GENERATE")) {
            if(mode==0){
                store.put("openQuestion",false);store.put("quizDue",false);store.put("code","");
                store.status("Full workflow starts with fresh code generation.");
            }
            nextCode();
        } else {
            if(mode==2 && !store.flag("pendingCode") && !store.flag("pendingQuiz") && Rules.limitReached(limitEnabled,store.number("quizAttempts",0),target)) {
                pause("Today's quiz limit has been reached.");return;
            }
            code=store.get("code","");setStage(first);launch(NUSUK);
        }
        handler.removeCallbacks(loop); handler.postDelayed(loop,750);
    }
    void pause(String why) {
        running=false; handler.removeCallbacks(loop);
        approvalToken="";approvalDiagnostic=false;aiDiagnostic=null;
        cancelLookup();
        releaseScreen();
        if (store==null) store=new Store(this);
        store.status(why);
        DailyReceiver.notify(this,"NusukConnec paused",why,false);
    }
    private void setStage(String next) {
        if (stage.equals(next)) return;
        if(next.equals("NAV_KEYS"))keysPageSeen=false;
        if(next.equals("SET_CODE"))codeTextVerified=false;
        if(next.equals("QUESTION")) {quizScan=new QuizScan();stableQuiz="";}
        stage=next; enteredAt=SystemClock.elapsedRealtime();
        store.status(next.replace('_',' ').toLowerCase(Locale.ROOT));
        DailyReceiver.notify(this,"NusukConnec running",store.number("accepted",0)+" codes accepted · "+store.number("correct",0)+" correct answers. Tap Stop to pause.",true);
    }
    private void nextCode() {
        if (Rules.limitReached(limitEnabled,store.number("attempts",0),target)) { pause("Daily code limit reached. Accepted "+store.number("accepted",0)+" of "+store.number("attempts",0)+" attempts."); return; }
        code=""; previousCode=""; setStage("SOURCE_GENERATE"); launch(SOURCE);
    }
    private void launch(String packageName) {
        Intent i=getPackageManager().getLaunchIntentForPackage(packageName);
        if (i==null) { pause("Required app is not installed or has no launcher: "+packageName); return; }
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException ex) { pause("Android could not open "+packageName+". Open it manually, then resume."); }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* One paced loop avoids duplicate submissions. */ }
    @Override public void onInterrupt() { pause("Android interrupted accessibility. Resume when ready."); }
    @Override public void onDestroy() {
        handler.removeCallbacks(loop);running=false;cancelLookup();releaseScreen();if(instance==this)instance=null;
        super.onDestroy();
    }
    private void tick() {
        if (!running) return;
        try {
            if (getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()) { pause("Phone locked or screen turned off. Unlock it and resume."); return; }
            store.day();
            if (!runDay.equals(store.get("day",""))) { pause("A new day started. Start a new run."); return; }
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if (root!=null) {
                try {
                    String pkg=String.valueOf(root.getPackageName());
                    if (SOURCE.equals(pkg)||NUSUK.equals(pkg)||CHATGPT.equals(pkg)) {
                        List<AccessibilityNodeInfo> nodes=new ArrayList<>(); collect(root,nodes,0);
                        try { step(pkg,nodes); } finally { for (AccessibilityNodeInfo n:nodes) if (n!=root) n.recycle(); }
                    }
                } finally { root.recycle(); }
            }
            if(running && (stage.equals("AI_RETURN") || stage.equals("CONFIRM_RETURN") || stage.equals("MANUAL_RETURN")) && SystemClock.elapsedRealtime()-enteredAt>5000) {
                setStage(stage.equals("MANUAL_RETURN")?"MANUAL_ANSWER":stage.equals("CONFIRM_RETURN")?"SUBMIT_ANSWER":"ANSWER_READY");launch(NUSUK);
            } else if(running && stage.startsWith("AI_") && !stage.equals("AI_RETURN") && SystemClock.elapsedRealtime()-aiStartedAt>60000) {
                finishAi(null,"ChatGPT did not return a readable choice within one minute.");
            } else if (running && !stage.startsWith("AI_") && !stage.equals("CONFIRM_ANSWER") && !stage.equals("MANUAL_ANSWER") && SystemClock.elapsedRealtime()-enteredAt>(stage.equals("RESEARCH")?75000:45000)) {
                if(stage.equals("RESEARCH")) {
                    cancelLookup();
                    prepareResearchResult(ResearchMatcher.bestEffort(lookupQuestion,lookupChoices,List.of(),"Research time budget reached."));
                } else pause("No recognized screen for 45 seconds at "+stage+". Open the expected screen and resume. Pending submissions are never retried automatically.");
            }
        } catch (RuntimeException ex) { pause("Screen changed unexpectedly. Resume from the current screen. ("+ex.getClass().getSimpleName()+")"); }
        if (running) handler.postDelayed(loop,stage.equals("RESEARCH")?1500:stage.endsWith("RESULT")?1000:750);
    }
    private void step(String pkg,List<AccessibilityNodeInfo> nodes) {
        viewportAction=false;
        if(CHATGPT.equals(pkg)) { if(stage.startsWith("AI_"))stepAi(nodes);return; }
        if(stage.equals("AI_RETURN") && NUSUK.equals(pkg))setStage("ANSWER_READY");
        if(stage.equals("CONFIRM_RETURN") && NUSUK.equals(pkg))setStage("SUBMIT_ANSWER");
        if(stage.equals("MANUAL_RETURN") && NUSUK.equals(pkg)) {
            setStage("MANUAL_ANSWER");store.status("Your turn: change the suggested choice if needed and tap Check your answer. The loop will continue after the result.");
        }
        if(stage.startsWith("AI_"))return;
        List<String> labels=new ArrayList<>();
        boolean includeOffscreen=Set.of("NAV_KEYS","SET_CODE","SUBMIT_CODE","CODE_RESULT","NAV_QUIZ","DOOR","OPEN_DOOR","RECOVER_QUIZ","QUESTION","RESEARCH","ANSWER_READY","SUBMIT_ANSWER").contains(stage);
        for (AccessibilityNodeInfo n:nodes) {
            if (n.isPassword() || (!includeOffscreen&&!n.isVisibleToUser())) continue;
            if (n.getText()!=null) labels.add(n.getText().toString());
            if (n.getContentDescription()!=null) labels.add(n.getContentDescription().toString());
        }
        String screen=String.join("\n",labels), normal=Rules.normalize(screen);
        String visibleQuestion=Rules.question(labels);
        boolean quizQuestion=visibleQuestion!=null && Rules.choices(labels,visibleQuestion).size()==4;
        if (Rules.blocked(screen)) { pause("Nusuk reports a device-security restriction. The app cannot proceed until Nusuk opens normally."); return; }
        store.put("lastScreen",stage+"\n"+screen.substring(0,Math.min(9000,screen.length())));
        if (normal.contains("too many") || normal.contains("daily limit") || normal.contains("try again later")) {
            pause("Nusuk reported a limit or temporary restriction. Check the app before continuing."); return;
        }
        if (stage.startsWith("SOURCE") && !SOURCE.equals(pkg)) return;
        if (!stage.startsWith("SOURCE") && !NUSUK.equals(pkg)) return;
        if(Set.of("CODE_RESULT","NAV_QUIZ","DOOR","OPEN_DOOR","RECOVER_QUIZ").contains(stage) && Rules.noKey(screen)) {
            if(stage.equals("CODE_RESULT")) {store.leaveUnknownCodeResult();code="";}
            setStage("NO_KEY");store.status("No key available. Closing the popup before generating a code.");return;
        }
        if(Set.of("NAV_KEYS","SET_CODE","SUBMIT_CODE").contains(stage) && store.flag("pendingCode") && Rules.rejectedCode(screen)) {
            store.rejectCode();store.status("Nusuk rejected the code. Generating a fresh code in NusukCodes.");nextCode();return;
        }
        // Nusuk can restore its previous result after briefly exposing the code form.
        // Preserve the generated code and navigate back; an old result is never credited.
        if((stage.equals("SET_CODE")||stage.equals("SUBMIT_CODE"))
                && !Rules.codeFormScreen(screen) && Rules.quizNavigationScreen(screen)) {
            store.status("Code form changed. Returning to Keys with the same generated code.");
            setStage("NAV_KEYS");return;
        }
        switch(stage) {
            case "SOURCE_GENERATE":
                previousCode=Rules.code(labels);
                if (click(nodes,"Get Fresh Code")) setStage("SOURCE_READ");
                break;
            case "SOURCE_READ": {
                String found=Rules.code(labels);
                if (found!=null && !found.equals(previousCode)) {
                    if (store.hasCode(found)) { pause("The code generator returned a code already submitted by NusukConnec. No duplicate was sent."); return; }
                    if(!click(nodes,"Copy Code"))break;
                    code=found; store.put("code",code); setStage("NAV_KEYS"); launch(NUSUK);
                    store.generated(code);
                }
                break;
            }
            case "NAV_KEYS":
                if(Rules.keyListScreen(screen))keysPageSeen=true;
                if (Rules.codeEntryReady(editable(nodes,false)!=null,screen)) setStage("SET_CODE");
                else if (click(nodes,"Add code to get new key") || click(nodes,"Add another code to get a key")
                        || click(nodes,"Add code to get a new key")) setStage("NAV_KEYS");
                else if (keysPageSeen || Rules.codeFormScreen(screen)) scrollContent(nodes,true);
                else if (click(nodes,"Keys")) keysPageSeen=true;
                else if (click(nodes,"Play again")) setStage("NAV_KEYS");
                else if (click(nodes,"Start the challenge") || click(nodes,"Start challenge")) setStage("NAV_KEYS");
                else scrollContent(nodes,true);
                break;
            case "SET_CODE": {
                AccessibilityNodeInfo edit=editable(nodes,false);
                if(edit==null) { scrollContent(nodes,true);break; }
                if(!onScreen(edit,nodes)) { reveal(nodes,edit,"code input");break; }
                if (Rules.codeEntryReady(edit!=null,screen)) {
                    Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code);
                    if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) setStage("SUBMIT_CODE");
                }
                break;
            }
            case "SUBMIT_CODE": {
                AccessibilityNodeInfo edit=editable(nodes,false);
                if(edit==null && !codeTextVerified) { scrollContent(nodes,false);break; }
                if(edit!=null) {
                    if(!code.equals(String.valueOf(edit.getText()))) { pause("Code text could not be verified; nothing submitted."); return; }
                    codeTextVerified=true;
                }
                if(!prepareClick(nodes,"Check the code")) { scrollContent(nodes,true);break; }
                // Mark uncertainty before the click, so interruption never causes a duplicate submission.
                store.submission(false);
                if (click(nodes,"Check the code")) setStage("CODE_RESULT");
                else store.cancelSubmission(false);
                break;
            }
            case "CODE_RESULT":
                if (Rules.acceptedCode(screen)) {
                    store.acceptCode(mode==0);
                    store.workflow(2,"Workflow: Nusuk confirmed the code added a new key.");
                    if (mode==1) nextCode(); else setStage("NAV_QUIZ");
                } else if (Rules.rejectedCode(screen)) {
                    store.rejectCode();
                    store.status("Code rejected by Nusuk. Generating a fresh code in Nusuk Codes."); nextCode();
                }
                else {
                    String recovery=Rules.codeResultRecoveryStage(screen,quizQuestion,mode,visibleKeyCount(nodes),SystemClock.elapsedRealtime()-enteredAt);
                    if(recovery!=null) {
                        store.leaveUnknownCodeResult();code="";
                        store.put("openQuestion",recovery.equals("QUESTION"));
                        if(recovery.equals("SOURCE_GENERATE"))nextCode();else setStage(recovery);
                        break;
                    }
                    String navigation=Rules.codeResultNavigation(labels);
                    if(navigation!=null)click(nodes,navigation);
                    else if(!Rules.codeFormScreen(screen) && Rules.keyListScreen(screen))scrollContent(nodes,true);
                    // Home is not a code-result page. Never blindly scroll it while waiting.
                    // Keep pendingCode until an explicit success or rejection is observed.
                }
                break;
            case "NAV_QUIZ":
                if (normal.contains("choose a door")) setStage("DOOR");
                else if (normal.contains("do you want to use your key")) setStage("OPEN_DOOR");
                else if (normal.contains("check your answer") || quizQuestion) { store.put("openQuestion",true); setStage("QUESTION"); }
                else if (click(nodes,"Play again") || click(nodes,"Play")) setStage("NAV_QUIZ");
                else if (click(nodes,"Start the challenge") || click(nodes,"Start challenge")) setStage("NAV_QUIZ");
                else scrollContent(nodes,true);
                break;
            case "NO_KEY":
                if(Rules.noKey(screen)) {
                    if(!click(nodes,"Close") && !tapLabel(nodes,"Close") && SystemClock.elapsedRealtime()-enteredAt>3000)performGlobalAction(GLOBAL_ACTION_BACK);
                } else {
                    store.put("openQuestion",false);store.put("quizDue",false);nextCode();
                }
                break;
            case "DOOR": {
                if (normal.contains("do you want to use your key")) { setStage("OPEN_DOOR"); break; }
                if (normal.contains("check your answer") || quizQuestion) { store.put("openQuestion",true); setStage("QUESTION"); break; }
                Integer available=visibleKeyCount(nodes);
                if(available!=null && available==0) {store.put("openQuestion",false);store.put("quizDue",false);nextCode();break;}
                if (Rules.limitReached(limitEnabled,store.number("quizAttempts",0),target)) { pause("Today's quiz limit reached."); return; }
                int category=store.quizDoor();
                boolean opened=click(nodes,Rules.doorPrefix(category));
                if (!opened && normal.contains("choose a door")) {
                    // Category cards can expose their full, multiline title rather than its visible first line.
                    for (String label:labels) {
                        if (Rules.doorLabel(label,category) && click(nodes,label)) { opened=true; break; }
                    }
                }
                if (opened) {store.doorSelected(category);setStage("OPEN_DOOR");}
                else if(normal.contains("choose a door"))scrollContent(nodes,true);
                break;
            }
            case "OPEN_DOOR":
                if (normal.contains("check your answer") || quizQuestion) { store.put("openQuestion",true); setStage("QUESTION"); }
                else if (click(nodes,"Open the door")) { store.put("openQuestion",true); setStage("QUESTION"); }
                else if(Rules.retryDoorSelection(screen,SystemClock.elapsedRealtime()-enteredAt))setStage("DOOR");
                else if (Rules.noKey(screen)) {
                    store.put("openQuestion",false);store.put("quizDue",false);
                    if(mode==0){store.status("No key is available. Continuing with code generation.");nextCode();}
                    else pause("No key is available. Redeem a code first.");
                }
                else if(normal.contains("do you want to use your key"))scrollContent(nodes,true);
                break;
            case "RECOVER_QUIZ":
                if(normal.contains("check your answer") || quizQuestion)setStage("QUESTION");
                else if(Rules.quizNavigationScreen(screen)) {
                    store.put("openQuestion",false);store.status("The old question is no longer open. Reopening the quiz navigation.");setStage("NAV_QUIZ");
                }
                break;
            case "QUESTION": {
                if (!normal.contains("check your answer") && Rules.question(labels)==null && quizScan.question()==null) {
                    if(Rules.quizNavigationScreen(screen))setStage("RECOVER_QUIZ");
                    else scrollContent(nodes,true);
                    break;
                }
                store.doorOpened();
                quizScan.observe(quizContentLabels(nodes));
                String question=quizScan.question();
                if (question==null) { scrollContent(nodes,false);break; }
                store.put("question",question);
                List<String> options=quizScan.choices();
                store.choices(options);
                if(options.size()!=4) {
                    List<String> partial=quizScan.partialChoices();
                    if(SystemClock.elapsedRealtime()-enteredAt>15000 && !partial.isEmpty()) {
                        lookupQuestion=question;lookupChoices=List.copyOf(partial);answerViewport=new QuizViewport(question,partial,true);
                        prepareResearchResult(new ResearchMatcher.Decision(partial.get(0),"Long-page fallback: trying the first readable choice.",List.of(),"",false));break;
                    }
                    scrollContent(nodes,true);break;
                }
                String signature=Rules.normalize(question)+options.stream().map(Rules::normalize).collect(java.util.stream.Collectors.joining("|"));
                if(SystemClock.elapsedRealtime()-enteredAt>15000) {
                    lookupQuestion=question;lookupChoices=List.copyOf(options);answerViewport=new QuizViewport(question,options);
                    prepareResearchResult(new ResearchMatcher.Decision(options.get(0),"Long-page fallback: trying the first readable choice.",List.of(),"",false));break;
                }
                if(!signature.equals(stableQuiz)) {stableQuiz=signature;stableQuizAt=SystemClock.elapsedRealtime();break;}
                if(SystemClock.elapsedRealtime()-stableQuizAt<700)break;
                if(store.online()) {
                    if(options.size()!=4) { scrollContent(nodes,true);break; }
                    store.workflow(4,"Workflow: quiz question and four choices read.");
                    startResearch(question,options);break;
                }
                if(options.size()!=4) { scrollContent(nodes,true);break; }
                String answer=Rules.answer(question,store.answers()), taughtChoice=null;
                if(answer!=null)for(String option:options)for(String alias:Rules.answerOptions(answer))
                    if(Rules.normalize(option).equals(Rules.normalize(alias)))taughtChoice=option;
                researchedAnswer=taughtChoice==null?ResearchMatcher.bestEffort(question,options,List.of(),"Online research is off and no saved choice matches.")
                    :new ResearchMatcher.Decision(taughtChoice,"Using the saved answer.",List.of(),"",false);
                lookupQuestion=question;lookupChoices=List.copyOf(options);answerViewport=new QuizViewport(question,options);
                store.research(question,researchedAnswer);setStage("ANSWER_READY");
                break;
            }
            case "RESEARCH": {
                String q=Rules.question(labels);
                if(q!=null && !Rules.sameQuestion(lookupQuestion,lookupChoices,q,Rules.choices(labels,q)))
                    pause("The question changed during research. No answer was selected. Resume to research the current question.");
                break;
            }
            case "ANSWER_READY": {
                if(!verifyAnswerViewport(nodes,labels))break;
                if(researchedAnswer==null || !researchedAnswer.hasAnswer() || !lookupChoices.contains(researchedAnswer.answer())) {
                    pause("No supported online answer is ready.");return;
                }
                if(click(nodes,researchedAnswer.answer())) {
                    selectedAnswer=researchedAnswer.answer();answerApproved=false;setStage("SUBMIT_ANSWER");
                    store.workflow(16,"Workflow: selected "+(researchedAnswer.reason().startsWith("ChatGPT selected option")?"ChatGPT's choice: ":"an unconfirmed guess: ")+selectedAnswer);
                }
                else scrollContent(nodes,true);
                break;
            }
            case "SUBMIT_ANSWER":
                if(!verifyAnswerViewport(nodes,labels))break;
                if(confirmBeforeAnswer && !answerApproved) {requestAnswerConfirmation();break;}
                if(!prepareClick(nodes,"Check your answer")) { scrollContent(nodes,true);break; }
                store.submission(true);
                if (click(nodes,"Check your answer")) {
                    store.put("openQuestion",false); setStage("QUIZ_RESULT");
                    store.workflow(32,"Workflow: answer submitted; waiting for Nusuk's result.");
                } else store.cancelSubmission(true);
                break;
            case "QUIZ_RESULT":
                if (Rules.correctAnswer(labels)) {
                    completeQuiz(true);
                } else if (Rules.wrongAnswer(labels)) {
                    completeQuiz(false);
                }
                break;
            case "MANUAL_ANSWER":
                if(manualAnswer==null)manualAnswer=new ManualAnswer();
                ManualAnswer.Result manualResult=manualAnswer.observe(labels);
                if(manualResult!=ManualAnswer.Result.WAITING) {
                    store.manualSubmissionObserved();completeQuiz(manualResult==ManualAnswer.Result.CORRECT);
                }
                break;
        }
    }
    private void cancelLookup() {
        researchedAnswer=null;aiRequestId="";aiPrompt="";aiTemporaryConfirmed=false;aiCandidate=-1;
    }
    private void startResearch(String question,List<String> options) {
        cancelLookup();lookupQuestion=question;lookupChoices=List.copyOf(options);
        answerViewport=new QuizViewport(question,options);
        aiRequestId=UUID.randomUUID().toString().replace("-","");aiPrompt=ChatGptQuiz.prompt(question,options,aiRequestId);
        aiStartedAt=SystemClock.elapsedRealtime();
        setStage("AI_TEMPORARY");store.status("Opening ChatGPT Temporary Chat with the question and all four choices.");
        store.put("research","Waiting for ChatGPT Temporary Chat: "+question);
        Intent intent=getPackageManager().getLaunchIntentForPackage(CHATGPT);
        if(intent==null) { finishAi(null,"ChatGPT is not installed.");return; }
        try {startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException failure) { finishAi(null,"Android could not open ChatGPT."); }
    }
    private boolean temporaryComposer(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo edit) {
        if(edit==null)return false;
        Rect composer=new Rect();edit.getBoundsInScreen(composer);
        for(AccessibilityNodeInfo node:nodes)if(labelMatches(node.getContentDescription(),"Temporary chat") && onScreen(node,nodes)) {
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            if(composer.contains(bounds.centerX(),bounds.centerY()))return true;
        }
        // The initial Temporary Chat banner is an additional explicit signal before composing.
        for(AccessibilityNodeInfo node:nodes)if(node.getText()!=null && onScreen(node,nodes)) {
            if(ChatGptQuiz.temporaryBanner(node.getText().toString()))return true;
        }
        return false;
    }
    private void stepAi(List<AccessibilityNodeInfo> nodes) {
        if(stage.equals("AI_RETURN")) {performGlobalAction(GLOBAL_ACTION_BACK);return;}
        AccessibilityNodeInfo edit=editable(nodes,false);
        if(stage.equals("AI_TEMPORARY")) {
            if(temporaryComposer(nodes,edit)) {aiTemporaryConfirmed=true;setStage("AI_COMPOSE");return;}
            click(nodes,"Temporary chat");return;
        }
        if(stage.equals("AI_COMPOSE")) {
            if(!aiTemporaryConfirmed || !temporaryComposer(nodes,edit)) {setStage("AI_TEMPORARY");return;}
            if(edit==null || !onScreen(edit,nodes))return;
            String draft=String.valueOf(edit.getText());
            if(!draft.isBlank() && !draft.equals("null") && !draft.equals(aiPrompt)) {finishAi(null,"ChatGPT has an unsent draft; it was left untouched.");return;}
            Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,aiPrompt);
            if(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))setStage("AI_SEND");
            return;
        }
        if(stage.equals("AI_SEND")) {
            if(edit==null || !aiPrompt.equals(String.valueOf(edit.getText())) || !aiTemporaryConfirmed)return;
            if(click(nodes,"Send Message") || click(nodes,"Send prompt") || click(nodes,"Send")) {
                setStage("AI_WAIT");store.status("ChatGPT is answering the question and four choices in Temporary Chat.");
            }
            return;
        }
        if(stage.equals("AI_WAIT")) {
            List<String> replies=new ArrayList<>();boolean generating=false;
            for(AccessibilityNodeInfo node:nodes) {
                if(node.isEditable() || node.isPassword())continue;
                if(node.getText()!=null && node.getText().toString().contains("NUSUK_ANSWER_"+aiRequestId))replies.add(node.getText().toString());
                if(labelMatches(node.getContentDescription(),"Stop generating") || labelMatches(node.getContentDescription(),"Stop response"))generating=true;
            }
            int candidate=ChatGptQuiz.answerIndex(replies,aiRequestId);
            if(candidate<0 || generating) {aiCandidate=-1;return;}
            if(aiCandidate!=candidate) {aiCandidate=candidate;aiCandidateAt=SystemClock.elapsedRealtime();return;}
            if(SystemClock.elapsedRealtime()-aiCandidateAt>=1000)finishAi(lookupChoices.get(candidate),"ChatGPT selected option "+(candidate+1)+" in Temporary Chat.");
        }
    }
    private void finishAi(String answer,String why) {
        ResearchMatcher.Decision result=answer==null?ResearchMatcher.bestEffort(lookupQuestion,lookupChoices,List.of(),why)
            :new ResearchMatcher.Decision(answer,why,List.of(),"",false);
        if(aiDiagnostic!=null) {
            java.util.function.Consumer<ResearchMatcher.Decision> callback=aiDiagnostic;aiDiagnostic=null;
            pause("ChatGPT Temporary Chat diagnostic finished. No quiz answer was submitted.");callback.accept(result);return;
        }
        prepareResearchResult(result);
        AccessibilityNodeInfo root=getRootInActiveWindow();
        boolean inChatGpt=false;
        if(root!=null) {inChatGpt=CHATGPT.contentEquals(root.getPackageName()==null?"":root.getPackageName());root.recycle();}
        if(inChatGpt) {setStage("AI_RETURN");performGlobalAction(GLOBAL_ACTION_BACK);}
        else if(running)launch(NUSUK);
    }
    void testChatGptLookup(String question,List<String> choices,java.util.function.Consumer<ResearchMatcher.Decision> callback) {
        if(running)throw new IllegalStateException("Stop the active run before testing ChatGPT.");
        if(store==null)store=new Store(this);
        store.day();runDay=store.get("day","");running=true;aiDiagnostic=callback;
        startResearch(question,choices);handler.removeCallbacks(loop);if(running)handler.postDelayed(loop,750);
    }
    private void releaseScreen() {if(screenLock!=null){if(screenLock.isHeld())screenLock.release();screenLock=null;}}
    private void prepareResearchResult(ResearchMatcher.Decision result) {
        store.research(lookupQuestion,result);
        if(!result.hasAnswer()) { pauseResearch("Cannot read four choices: "+result.reason());return; }
        researchedAnswer=result;setStage("ANSWER_READY");
        boolean fromChatGpt=result.reason().startsWith("ChatGPT selected option");
        if(fromChatGpt)store.workflow(8,"Workflow: ChatGPT returned choice "+result.answer()+" in Temporary Chat.");
        store.status((fromChatGpt?"ChatGPT choice: ":"Unconfirmed guess: ")+result.answer()+". Checking the screen before selecting.");
    }
    private void pauseResearch(String why) {
        pause(why);
        // Show the reason rather than leaving the unanswered Nusuk screen looking idle.
        try { startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)); }
        catch(RuntimeException ignored) { /* The pause notification still contains the reason. */ }
    }
    private void afterQuiz() {
        answerViewport=null;manualAnswer=null;store.put("manualAnswer",false);
        if (mode==2) {
            if (Rules.limitReached(limitEnabled,store.number("quizAttempts",0),target)) pause("Today's quiz limit reached. Correct answers: "+store.number("correct",0));
            else setStage("NAV_QUIZ");
        } else nextCode();
    }
    private void completeQuiz(boolean correct) {
        if(correct)store.correctResult();
        store.put("pendingQuiz",false);store.put("quizDue",false);
        if(correct)store.increment("correct");
        else {store.increment("wrong");store.status("Nusuk marked the answer wrong. Continuing to the next cycle as requested.");}
        afterQuiz();
    }
    String pendingApprovalToken() {return running&&stage.equals("CONFIRM_ANSWER")?approvalToken:"";}
    String pendingApprovalText() {
        StringBuilder text=new StringBuilder(lookupQuestion).append("\n\n");
        for(int i=0;i<lookupChoices.size();i++)text.append(i+1).append(") ").append(lookupChoices.get(i)).append('\n');
        if(lookupChoices.size()<4)text.append("\nOnly ").append(lookupChoices.size()).append(" choice(s) could be read from this long page. This is a fallback guess.\n");
        return text.append("\nSelected answer: ").append(selectedAnswer).append("\n\nSubmit this answer in Nusuk?").toString();
    }
    void approveAnswer(String token,boolean approved) {
        if(!running || !stage.equals("CONFIRM_ANSWER") || !approvalToken.equals(token))return;
        approvalToken="";
        if(approvalDiagnostic) {
            approvalDiagnostic=false;store.put("aiDiagnostic","Confirmation test: "+(approved?"YES":"NO")+" received. No Nusuk answer submitted.");
            pause("Answer confirmation diagnostic finished. Nothing submitted in Nusuk.");return;
        }
        if(!approved) {
            store.put("manualAnswer",true);store.put("openQuestion",true);manualAnswer=new ManualAnswer(true);setStage("MANUAL_RETURN");
            store.status("Your turn to review and submit in Nusuk. Automation will continue after your result.");return;
        }
        answerApproved=true;answerViewport=new QuizViewport(lookupQuestion,lookupChoices,lookupChoices.size()<4);setStage("CONFIRM_RETURN");
        store.status("Answer approved. Rechecking the quiz before submission.");
    }
    private void requestAnswerConfirmation() {
        approvalToken=UUID.randomUUID().toString();setStage("CONFIRM_ANSWER");
        store.status("Waiting for your Yes/No confirmation before submitting: "+selectedAnswer);
        try {startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
        catch(RuntimeException ignored) {DailyReceiver.notify(this,"Answer confirmation needed","Open NusukConnec to approve or decline submission.",true);}
    }
    void testAnswerConfirmation() {
        if(running)throw new IllegalStateException("Stop the active run before testing confirmation.");
        if(store==null)store=new Store(this);
        lookupQuestion="Which answer should this confirmation test submit?";lookupChoices=List.of("Test option A","Test option B","Test option C","Test option D");
        selectedAnswer="Test option B";running=true;approvalDiagnostic=true;
        store.put("aiDiagnostic","Waiting for confirmation test response. No Nusuk answer will be submitted.");requestAnswerConfirmation();
    }
    private static Integer visibleKeyCount(List<AccessibilityNodeInfo> nodes) {
        Rect window=new Rect();nodes.get(0).getBoundsInScreen(window);Integer count=null;
        for(AccessibilityNodeInfo node:nodes)if(node.isVisibleToUser()) {
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            if(bounds.centerX()<=window.centerX() || bounds.centerY()>window.top+window.height()*.22f)continue;
            Integer value=Rules.keyCount(node.getText()==null?"":node.getText().toString());
            if(value==null)value=Rules.keyCount(node.getContentDescription()==null?"":node.getContentDescription().toString());
            if(value!=null) {if(count!=null&&!count.equals(value))return null;count=value;}
        }
        return count;
    }
    private static List<String> quizContentLabels(List<AccessibilityNodeInfo> nodes) {
        Rect window=new Rect();nodes.get(0).getBoundsInScreen(window);List<String> result=new ArrayList<>();
        for(AccessibilityNodeInfo node:nodes) {
            if(node.isPassword())continue;
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            boolean keyHeader=bounds.centerX()>window.centerX() && bounds.centerY()<window.top+window.height()*.22f;
            for(CharSequence label:new CharSequence[]{node.getText(),node.getContentDescription()})
                if(label!=null && !(keyHeader&&Rules.keyCount(label.toString())!=null))result.add(label.toString());
        }
        return result;
    }
    private static void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out,int depth) {
        if (depth>80 || out.size()>=1400) {n.recycle();return;}
        out.add(n);
        for (int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo child=n.getChild(i); if (child!=null) collect(child,out,depth+1); }
    }
    private static AccessibilityNodeInfo editable(List<AccessibilityNodeInfo> nodes,boolean visibleOnly) {
        AccessibilityNodeInfo found=null;
        for (AccessibilityNodeInfo n:nodes) if (n.isEditable()&&(!visibleOnly||onScreen(n,nodes))&&n.isEnabled()&&!n.isPassword()) {
            if (found!=null) return null; found=n;
        }
        return found;
    }
    private static boolean labelMatches(CharSequence text,String expected) {
        if (text==null) return false;
        if(Rules.challengeLabel(expected) && Rules.challengeLabel(text.toString()))return true;
        if (Rules.normalize(text.toString()).equals(Rules.normalize(expected))) return true;
        String withoutState=text.toString().replaceAll("(?i),?\\s+(not selected|selected|not checked|checked)$","");
        if(Rules.normalize(withoutState).equals(Rules.normalize(expected)))return true;
        for (String line:text.toString().split("\n")) if (Rules.normalize(line).equals(Rules.normalize(expected))) return true;
        return false;
    }
    private boolean tapLabel(List<AccessibilityNodeInfo> nodes,String label) {
        if(viewportAction)return false;
        for(AccessibilityNodeInfo node:nodes)if(node.isEnabled() && onScreen(node,nodes)
                && (labelMatches(node.getText(),label)||labelMatches(node.getContentDescription(),label))) {
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);Path tap=new Path();tap.moveTo(bounds.exactCenterX(),bounds.exactCenterY());
            viewportAction=true;
            return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(tap,0,80)).build(),null,null);
        }
        return false;
    }
    private boolean verifyAnswerViewport(List<AccessibilityNodeInfo> nodes,List<String> labels) {
        String screen=Rules.normalize(String.join("\n",labels));
        if(screen.contains("choose a door") || screen.contains("play again") || screen.contains("do you want to use your key")
                || screen.contains("start the challenge") || screen.contains("start challenge")) {
            pause("The quiz screen changed. Nothing submitted.");return false;
        }
        if(answerViewport==null) { pause("No verified question is ready. Resume to read the quiz again.");return false; }
        QuizViewport.Result result=answerViewport.observe(labels);
        if(result==QuizViewport.Result.CHANGED) {
            pause("The question or answer choices changed. Nothing submitted. Resume to research again.");return false;
        }
        if(result==QuizViewport.Result.NEED_CONTEXT) { scrollContent(nodes,!answerViewport.needsQuestion());return false; }
        return true;
    }
    private static boolean onScreen(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> nodes) {
        if(!node.isVisibleToUser() || nodes.isEmpty())return false;
        Rect bounds=new Rect(),window=new Rect();node.getBoundsInScreen(bounds);nodes.get(0).getBoundsInScreen(window);
        return !bounds.isEmpty() && window.contains(bounds.centerX(),bounds.centerY());
    }
    private void reveal(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo node,String label) {
        if(viewportAction)return;
        // Some Flutter nodes accept SHOW_ON_SCREEN without moving. Try it once, then swipe.
        if(!lastReveal.equals(label) && node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId())) {
            lastReveal=label;viewportAction=true;
            if(answerViewport!=null)answerViewport.scrolled();
            return;
        }
        lastReveal=label;
        Rect bounds=new Rect(),window=new Rect();node.getBoundsInScreen(bounds);nodes.get(0).getBoundsInScreen(window);
        scrollContent(nodes,bounds.isEmpty() || bounds.centerY()>=window.centerY());
    }
    private void scrollContent(List<AccessibilityNodeInfo> nodes,boolean forward) {
        if(viewportAction || nodes.isEmpty() || SystemClock.elapsedRealtime()-lastScrollAt<1100)return;
        viewportAction=true;lastScrollAt=SystemClock.elapsedRealtime();
        if(answerViewport!=null)answerViewport.scrolled();
        // Alternate a reported successful action with a real swipe if the target is still hidden.
        // Flutter may advertise scrolling yet leave the viewport unchanged.
        boolean tryAction=!lastScrollUsedAction;lastScrollUsedAction=false;
        for(int i=nodes.size()-1;tryAction && i>=0;i--) {
            AccessibilityNodeInfo node=nodes.get(i);
            if(onScreen(node,nodes) && node.isEnabled() && node.isScrollable()
                    && node.performAction(forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                lastScrollUsedAction=true;return;
            }
        }
        // Some Flutter scroll regions expose no scroll action. Swipe within the content area.
        Rect bounds=new Rect();nodes.get(0).getBoundsInScreen(bounds);
        if(bounds.width()<=0 || bounds.height()<=0)return;
        Path path=new Path();path.moveTo(bounds.exactCenterX(),bounds.top+bounds.height()*(forward?.72f:.35f));
        path.lineTo(bounds.exactCenterX(),bounds.top+bounds.height()*(forward?.35f:.72f));
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,350)).build(),null,null);
    }
    private AccessibilityNodeInfo clickTarget(List<AccessibilityNodeInfo> nodes,String label) {
        if(viewportAction)return null;
        AccessibilityNodeInfo hidden=null;
        for (AccessibilityNodeInfo n:nodes) {
            if (!n.isEnabled() || n.isPassword()) continue;
            if (!labelMatches(n.getText(),label)&&!labelMatches(n.getContentDescription(),label)) continue;
            if(!onScreen(n,nodes)) {if(hidden==null)hidden=n;continue;}
            AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(n);
            try {
                for (int depth=0;current!=null && depth<6;depth++) {
                    if (current.isClickable() && current.isEnabled() && onScreen(current,nodes)) {
                        AccessibilityNodeInfo target=AccessibilityNodeInfo.obtain(current);return target;
                    }
                    AccessibilityNodeInfo parent=current.getParent(); current.recycle(); current=parent;
                }
            } finally { if (current!=null) current.recycle(); }
        }
        if(hidden!=null)reveal(nodes,hidden,label);
        return null;
    }
    private boolean prepareClick(List<AccessibilityNodeInfo> nodes,String label) {
        AccessibilityNodeInfo target=clickTarget(nodes,label);
        if(target==null)return false;
        target.recycle();return true;
    }
    private boolean click(List<AccessibilityNodeInfo> nodes,String label) {
        AccessibilityNodeInfo target=clickTarget(nodes,label);
        if(target==null)return false;
        try { lastReveal="";return target.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
        finally {target.recycle();}
    }
}
