import com.nusukconnect.companion.QuizViewport;
import java.util.*;

/** Models successive accessibility snapshots as scrolling removes quiz nodes. */
public final class QuizViewportTest {
    private static int checks;
    private static final String QUESTION="Which book did God reveal to Moses?";
    private static final List<String> CHOICES=List.of("The Torah","The Quran","The Injil","The Zabur");
    private static List<String> snapshot(String question,List<String> choices) {
        List<String> labels=new ArrayList<>();labels.add(question);labels.addAll(choices);return labels;
    }
    private static void expect(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        QuizViewport quiz=new QuizViewport(QUESTION,CHOICES);
        expect(quiz.observe(List.of("Check your answer"))==QuizViewport.Result.NEED_CONTEXT,"submit button alone cannot authorize an answer");
        quiz.scrolled();
        expect(quiz.observe(List.of("Check your answer"))==QuizViewport.Result.NEED_CONTEXT,"scrolling before verification cannot invent quiz context");
        expect(quiz.observe(snapshot(QUESTION,CHOICES))==QuizViewport.Result.VERIFIED,"full question and choices verified even without submit button");
        expect(quiz.observe(List.of("Check your answer"))==QuizViewport.Result.NEED_CONTEXT,"disappeared context without our own scroll must be rechecked");
        quiz.scrolled();
        expect(quiz.observe(List.of("Check your answer"))==QuizViewport.Result.VERIFIED,"own scrolling can reveal submit after question moves out of view");
        expect(quiz.observe(List.of(QUESTION,"The Torah","The Quran"))==QuizViewport.Result.VERIFIED,"partial unchanged viewport after scrolling keeps verified context");
        expect(quiz.observe(snapshot("Another question?",CHOICES))==QuizViewport.Result.CHANGED,"changed question blocks submission after scrolling");
        expect(quiz.observe(snapshot(QUESTION,List.of("The Quran","The Torah","The Injil","The Zabur")))==QuizViewport.Result.CHANGED,"reordered choices block submission after scrolling");
        expect(quiz.observe(snapshot(QUESTION,List.of("The Torah","The Quran","The Injil","Other")))==QuizViewport.Result.CHANGED,"replaced choice blocks submission");
        QuizViewport resumed=new QuizViewport(QUESTION,CHOICES);
        expect(resumed.observe(List.of("Check your answer"))==QuizViewport.Result.NEED_CONTEXT,"new run cannot reuse a prior run's scrolling verification");
        QuizViewport incomplete=new QuizViewport(QUESTION,List.of("A","B","C"));
        expect(incomplete.observe(snapshot(QUESTION,CHOICES))==QuizViewport.Result.CHANGED,"an incomplete original snapshot cannot authorize a submission");
        System.out.println("Passed "+checks+" quiz viewport checks.");
    }
}
