package com.nusukconnect.companion;

import java.util.*;
import java.util.regex.*;

/** Retains ordered quiz labels while a long page is scrolled through successive viewports. */
public final class QuizScan {
    private String question;
    private final LinkedHashMap<String,String> labels=new LinkedHashMap<>();
    public void observe(List<String> viewport) {
        String current=Rules.question(viewport);
        if(current!=null && !Rules.normalize(current).equals(Rules.normalize(question))) {labels.clear();question=current;}
        if(question==null)return;
        boolean after=current==null;
        for(String label:viewport) {
            String n=Rules.normalize(label);
            if(n.equals(Rules.normalize(question))) {after=true;labels.putIfAbsent(n,question);continue;}
            if(n.startsWith(Rules.normalize(question)+" ") && label.contains("?")) {
                after=true;labels.putIfAbsent(Rules.normalize(question),question);
                label=label.substring(label.indexOf('?')+1).trim();n=Rules.normalize(label);
            }
            if(!after)continue;
            Matcher footer=Pattern.compile("(?i)check your answer|your key can unlock|you can unlock|choose the right answer").matcher(label);
            if(footer.find()) {
                String before=label.substring(0,footer.start()).trim();
                if(!before.isEmpty())labels.putIfAbsent(Rules.normalize(before),before);
                break;
            }
            if(n.isEmpty())continue;
            labels.putIfAbsent(n,label);
        }
    }
    public String question() {return question;}
    public List<String> choices() {
        List<String> choices=partialChoices();return choices.size()==4?choices:List.of();
    }
    public List<String> partialChoices() {
        if(question==null)return List.of();
        List<String> merged=new ArrayList<>();merged.add(question);merged.addAll(labels.values());
        return Rules.partialChoices(merged,question);
    }
}
