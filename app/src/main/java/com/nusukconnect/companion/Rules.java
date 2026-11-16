package com.nusukconnect.companion;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Pure matching logic; unknown questions never produce a guessed answer. */
public final class Rules {
    private Rules() {}
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("\\s+", " ").trim();
    }
    public static String code(List<String> labels) {
        // The recording shows NG- followed by eight mixed-case alphanumeric characters.
        Pattern p = Pattern.compile("(?<![A-Za-z0-9])NG-[A-Za-z0-9]{8}(?![A-Za-z0-9])");
        for (String label : labels) {
            Matcher m = p.matcher(label);
            // Ignore the numbered Recently Taken history; it contains previously used codes.
            if (!label.trim().matches("(?s)^\\d+[.)].*" ) && m.find()) return m.group();
        }
        return null;
    }
    public static String question(List<String> labels) {
        String best=null;
        for (String label : labels) {
            String s = label.trim();
            if (s.contains("?") && !normalize(s).contains("use your key") && !normalize(s).contains("do you want")) {
                if(normalize(s).contains("check your answer") || normalize(s).contains("your key can unlock"))s=s.substring(0,s.indexOf('?')+1).trim();
                if(best==null || s.length()<best.length())best=s;
            }
        }
        return best;
    }
    public static List<String> choices(List<String> labels,String question) {
        List<String> result=partialChoices(labels,question);
        return result.size()==4?result:List.of();
    }
    public static List<String> partialChoices(List<String> labels,String question) {
        boolean found=false;LinkedHashMap<String,String> choices=new LinkedHashMap<>();
        for(String label:labels) {
            // Accessibility can expose a wrapped question as one multiline node.
            // Recognize that node before splitting option/state labels into lines.
            if(normalize(label).equals(normalize(question))) { found=true;continue; }
            for(String line:label.split("\\n")) {
            if(Set.of("selected","not selected","checked","not checked").contains(normalize(line)))continue;
            String s=line.trim().replaceAll("(?i),?\\s+(not selected|selected|not checked|checked)$","");
            String n=normalize(s);
            if(n.equals(normalize(question))) { found=true;continue; }
            if(!found || n.isEmpty() || n.equals("selected") || n.equals("not selected"))continue;
            if(n.contains("check your answer") || n.startsWith("you can unlock") || n.startsWith("your key can unlock") || n.startsWith("choose the right answer"))
                return new ArrayList<>(choices.values());
            if(n.equals("play")||n.equals("winners")||n.equals("keys")||n.equals("share")||n.equals("help"))continue;
            if(s.length()>1500 || s.contains("?"))continue;
            choices.putIfAbsent(n,s);
            // Nusuk always supplies four choices; all following explanatory/footer text is outside the answer list.
            if(choices.size()==4)return new ArrayList<>(choices.values());
            }
        }
        return new ArrayList<>(choices.values());
    }
    public static boolean sameQuestion(String original,List<String> originalChoices,String current,List<String> currentChoices) {
        if(!normalize(original).equals(normalize(current)) || originalChoices.size()!=4 || currentChoices.size()!=4)return false;
        for(int i=0;i<4;i++)if(!normalize(originalChoices.get(i)).equals(normalize(currentChoices.get(i))))return false;
        return true;
    }
    public static boolean quizNavigationScreen(String text) {
        String n=normalize(text);
        return n.contains("choose a door") || n.contains("do you want to use your key")
            || n.contains("start the challenge") || n.contains("start challenge") || n.contains("your keys") || n.contains("play again");
    }
    public static boolean codeEntryReady(boolean hasEditable,String text) {
        return hasEditable && codeFormScreen(text);
    }
    public static boolean codeFormScreen(String text) {
        String n=normalize(text);
        return (n.contains("check the code") || n.contains("enter your code") || n.contains("enter the code"))
            && !n.contains("play again") && !n.startsWith("correct!");
    }
    public static boolean keyListScreen(String text) {
        String n=normalize(text);
        return n.contains("your keys") || n.contains("challeng keys") || n.contains("challenge keys");
    }
    public static boolean noKey(String text) {
        String n=normalize(text);
        return Pattern.compile("\\b(?:no (?:available )?keys?|not enough keys?|(?:don't have|do not have)(?: any| a)? keys?)\\b").matcher(n).find();
    }
    public static boolean challengeLabel(String text) {
        String n=normalize(text).replaceAll("[\\s\\-–—=<>→➜›»]+$","").trim();
        return n.equals("start the challenge") || n.equals("start challenge");
    }
    public static Integer keyCount(String label) {
        String n=normalize(label);
        Matcher m=Pattern.compile("^(?:(?:your )?keys?\\s*[:：]?\\s*)?(\\d{1,3})(?:\\s*keys?)?$").matcher(n);
        return m.matches()?Integer.parseInt(m.group(1)):null;
    }
    public static Map<String, String> seeds() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put(normalize("Who was the famous muezzin of the Messenger of Allah ﷺ?"), "Bilal ibn Rabah");
        answers.put(normalize("By what name did Jesus (peace be upon him) give glad tidings of the Prophet (PBUH)?"), "Ahmad");
        answers.put(normalize("Which book did God reveal to Moses (peace be upon him)?"), "The Torah");
        // Sahih al-Bukhari 3757: https://sunnah.com/bukhari/62/103
        answers.put(normalize("Which companion was nicknamed “the Drawn Sword of Allah”?"), "Khalid ibn al-Walid");
        return answers;
    }
    public static String answer(String question, Map<String, String> taught) {
        return taught.get(normalize(question));
    }
    public static List<String> answerOptions(String answer) {
        if (normalize(answer).equals(normalize("Khalid ibn al-Walid"))) {
            return List.of(answer,"Khalid bin al-Walid","Khalid ibn Al Walid","Khalid bin Al Walid",
                "Khalid ibn al-Waleed","Khalid bin al-Waleed");
        }
        return List.of(answer);
    }
    public static boolean resumeQuiz(int mode, boolean quizDue, int accepted, int quizAttempts, int limit) {
        // Redeeming the final permitted code must not prevent its quiz after a service restart.
        return quizAttempts < limit && (mode==2 || (mode==0 && (quizDue || accepted>quizAttempts)));
    }
    public static boolean validLimit(boolean enabled,int limit) {return !enabled || (limit>=1 && limit<=349);}
    public static boolean limitReached(boolean enabled,int attempts,int limit) {return enabled && attempts>=limit;}
    public static boolean resumeQuestion(int mode,boolean openQuestion) {
        return mode!=1 && openQuestion;
    }
    public static String initialStage(int mode,boolean pendingCode,boolean pendingQuiz,boolean openQuestion,boolean reusableCode) {
        if(pendingCode)return "CODE_RESULT";
        if(pendingQuiz)return "QUIZ_RESULT";
        if(mode==0)return openQuestion?"RECOVER_QUIZ":reusableCode?"NAV_KEYS":"SOURCE_GENERATE";
        if(mode==1)return reusableCode?"NAV_KEYS":"SOURCE_GENERATE";
        return openQuestion?"RECOVER_QUIZ":"NAV_QUIZ";
    }
    public static String taskName(int mode) {
        return switch(mode) {
            case 1 -> "Redeem codes only";
            case 2 -> "Answer quizzes with existing keys";
            default -> "Full workflow: codes + quizzes";
        };
    }
    public static String doorPrefix(int door) {
        return new String[]{"The Prophet's","The Quran","Makkah and the","Madinah and the"}[Math.max(0,Math.min(3,door))];
    }
    public static List<Integer> doorOrder(String saved,Random random) {
        List<Integer> order=new ArrayList<>();
        try {for(String value:saved.split(","))order.add(Integer.parseInt(value));}
        catch(NumberFormatException ignored){order.clear();}
        if(order.size()!=4 || !new HashSet<>(order).equals(Set.of(0,1,2,3))) {
            order=new ArrayList<>(List.of(0,1,2,3));Collections.shuffle(order,random);
        }
        return order;
    }
    public static boolean doorLabel(String label,int door) {
        String actual=normalize(label), prefix=normalize(doorPrefix(door));
        return actual.equals(prefix) || actual.startsWith(prefix+" ");
    }
    public static boolean acceptedCode(String text) {
        String n = normalize(text);
        return n.contains("you got a new key") || n.contains("you've got a new key");
    }
    public static String codeResultNavigation(List<String> labels) {
        // A pending result may be resumed on Home. Stop searching once its entry button exists.
        for(String label:labels) if(challengeLabel(label))return label;
        for(String label:labels) if(normalize(label).equals("check the code"))return null;
        for(String target:List.of("Keys","Add code to get new key","Add another code to get a key","Add code to get a new key","Play again"))
            for(String label:labels) if(normalize(label).equals(normalize(target)))return label;
        return null;
    }
    public static String codeResultQuizStage(String text) {
        String n=normalize(text);
        if(n.contains("do you want to use your key"))return "OPEN_DOOR";
        if(n.contains("choose a door"))return "DOOR";
        return null;
    }
    public static String codeResultRecoveryStage(String text,boolean question,int mode,Integer keys,long elapsed) {
        if(acceptedCode(text) || rejectedCode(text))return null;
        String quiz=question?"QUESTION":codeResultQuizStage(text);
        if(quiz!=null) {
            if(mode==1 || (!question && keys!=null && keys==0))return "SOURCE_GENERATE";
            return quiz;
        }
        // Give Nusuk time to return a result. Afterwards abandon this code, never retry it.
        if(elapsed>=15000 && (codeFormScreen(text)||keyListScreen(text)))return "SOURCE_GENERATE";
        return null;
    }
    public static boolean retryDoorSelection(String text,long elapsed) {
        return elapsed>=3000 && "DOOR".equals(codeResultQuizStage(text));
    }
    public static boolean rejectedCode(String text) {
        String n = normalize(text);
        return n.contains("invalid code") || n.contains("code is invalid") || n.contains("code is incorrect")
            || n.contains("code is not correct")
            || Pattern.compile("\\balready (?:(?:has|have) )?(?:been )?(?:used|redeemed)\\b").matcher(n).find()
            || n.contains("code has expired");
    }
    public static boolean blocked(String text) {
        String n = normalize(text);
        return n.contains("for security reasons") || n.contains("can't run on this device") || n.contains("cannot run on this device");
    }
    public static boolean correctAnswer(List<String> labels) {
        for (String label : labels) if (normalize(label).startsWith("correct!")) return true;
        return false;
    }
    public static boolean wrongAnswer(List<String> labels) {
        for(String label:labels) {
            String n=normalize(label);
            if(n.startsWith("wrong answer") || n.startsWith("wrong!") || n.startsWith("incorrect!") || n.equals("incorrect")
                    || n.contains("your answer is incorrect") || n.contains("your answer is wrong"))return true;
        }
        return false;
    }
}
