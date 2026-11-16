package com.nusukconnect.companion;

import java.util.List;

/** Observes a user-controlled quiz without selecting or submitting any choice. */
public final class ManualAnswer {
    public enum Result { WAITING, CORRECT, WRONG }
    private boolean sawQuiz,finished;
    public ManualAnswer() {}
    public ManualAnswer(boolean questionAlreadyVerified) {sawQuiz=questionAlreadyVerified;}
    public Result observe(List<String> labels) {
        if(finished)return Result.WAITING;
        if(sawQuiz && Rules.correctAnswer(labels)) {finished=true;return Result.CORRECT;}
        if(sawQuiz && Rules.wrongAnswer(labels)) {finished=true;return Result.WRONG;}
        if(Rules.question(labels)!=null || Rules.normalize(String.join("\n",labels)).contains("check your answer"))sawQuiz=true;
        return Result.WAITING;
    }
}
