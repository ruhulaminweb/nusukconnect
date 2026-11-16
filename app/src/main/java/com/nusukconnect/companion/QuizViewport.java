package com.nusukconnect.companion;

import java.util.*;

/** Keeps a verified quiz identity while our own scrolling moves it out of view. */
public final class QuizViewport {
    public enum Result { VERIFIED, NEED_CONTEXT, CHANGED }
    private final String question;
    private final List<String> choices;
    private final boolean partialAllowed;
    private boolean verified, scrolled;
    private boolean sawQuestion;
    private final Set<Integer> seen=new HashSet<>();

    public QuizViewport(String question,List<String> choices) {
        this(question,choices,false);
    }
    public QuizViewport(String question,List<String> choices,boolean partialAllowed) {
        this.question=question;this.choices=List.copyOf(choices);
        this.partialAllowed=partialAllowed;
    }
    public Result observe(List<String> labels) {
        String current=Rules.question(labels);
        List<String> options=Rules.choices(labels,current);
        if(current!=null && !Rules.normalize(question).equals(Rules.normalize(current)))return Result.CHANGED;
        if(current!=null)sawQuestion=true;
        if(current!=null && options.size()==4 && !partialAllowed) {
            if(!Rules.sameQuestion(question,choices,current,options))return Result.CHANGED;
            if(!verified)scrolled=false;
            verified=true;return Result.VERIFIED;
        }
        if(partialAllowed && current!=null && options.size()==4) {
            for(int i=0;i<choices.size();i++)if(!Rules.normalize(choices.get(i)).equals(Rules.normalize(options.get(i))))return Result.CHANGED;
        }
        LinkedHashSet<Integer> inViewport=new LinkedHashSet<>();
        for(String label:labels) {
            List<String> parts=new ArrayList<>();parts.add(label);parts.addAll(Arrays.asList(label.split("\\n")));
            for(String part:parts) {
                String n=Rules.normalize(part.replaceAll("(?i),?\\s+(not selected|selected|not checked|checked)$",""));
                for(int i=0;i<choices.size();i++)if(n.equals(Rules.normalize(choices.get(i))))inViewport.add(i);
            }
        }
        int previous=-1;
        for(int i:inViewport) {if(i<previous)return Result.CHANGED;previous=i;}
        if(sawQuestion)seen.addAll(inViewport);
        if(sawQuestion && (choices.size()==4 || (partialAllowed&&!choices.isEmpty())) && seen.size()==choices.size()) {verified=true;return Result.VERIFIED;}
        return verified && scrolled?Result.VERIFIED:Result.NEED_CONTEXT;
    }
    public boolean needsQuestion() {return !sawQuestion;}
    public void scrolled() { scrolled=true; }
}
