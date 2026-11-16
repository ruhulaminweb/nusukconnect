import com.nusukconnect.companion.*;
import java.util.*;

public final class QuizScanTest {
    private static int checks;
    private static void expect(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        String question="This is a long question with explanatory context\nthat wraps over multiple lines. Which book was revealed to Moses?";
        QuizScan scan=new QuizScan();
        scan.observe(List.of("2",question,"The Torah","The Quran","Check your answer","2"));
        expect(scan.question().equals(question),"long multiline question retained in full");
        expect(scan.choices().isEmpty(),"incomplete first viewport waits for more choices");
        expect(scan.partialChoices().equals(List.of("The Torah","The Quran")),"bounded fallback retains actual readable choices without inventing missing options");
        scan.observe(List.of("The Quran","The Injil","The Zabur","Check your answer","2"));
        List<String> choices=List.of("The Torah","The Quran","The Injil","The Zabur");
        expect(scan.choices().equals(choices),"choices read across downward scroll with overlap and footer ignored");
        scan.observe(List.of("The Torah, selected","The Quran","The Injil","The Zabur","Check your answer"));
        expect(scan.choices().equals(choices),"selection metadata does not change accumulated options");
        scan.observe(List.of("Another question?","A","B","C","D","Check your answer"));
        expect(scan.question().equals("Another question?")&&scan.choices().equals(List.of("A","B","C","D")),"new question clears stale collected choices");
        QuizScan combined=new QuizScan();combined.observe(List.of("Which book?\nA\nB\nC\nD\nCheck your answer"));
        expect(combined.choices().equals(List.of("A","B","C","D")),"single combined accessibility node can expose question and choices");
        QuizViewport viewport=new QuizViewport(question,choices);
        expect(viewport.observe(List.of(question,"The Torah","The Quran"))==QuizViewport.Result.NEED_CONTEXT,"partial question context begins verification");
        expect(!viewport.needsQuestion(),"partial verification scrolls down for missing choices instead of bouncing up");
        viewport.scrolled();
        expect(viewport.observe(List.of("The Quran","The Injil","The Zabur","Check your answer"))==QuizViewport.Result.VERIFIED,"quiz can be verified across successive viewports");
        expect(viewport.observe(List.of("Another question?","The Torah"))==QuizViewport.Result.CHANGED,"changed question cannot use cached long-page context");
        QuizViewport reorder=new QuizViewport(question,choices);
        reorder.observe(List.of(question,"The Torah"));reorder.scrolled();
        expect(reorder.observe(List.of("The Zabur","The Injil"))==QuizViewport.Result.CHANGED,"partial reordered choices still invalidate a researched answer");
        QuizViewport fallback=new QuizViewport(question,List.of("The Torah","The Quran"),true);
        expect(fallback.observe(List.of(question,"The Torah","The Quran"))==QuizViewport.Result.VERIFIED,"explicit long-page fallback can verify a known readable choice");
        fallback.scrolled();
        expect(fallback.observe(List.of("Check your answer"))==QuizViewport.Result.VERIFIED,"fallback can reach submit after its known choice scrolls out of view");
        expect(fallback.observe(List.of("Changed question?","The Torah"))==QuizViewport.Result.CHANGED,"fallback still cannot submit a stale choice on another question");
        expect(fallback.observe(List.of(question,"The Quran","The Torah","The Injil","The Zabur"))==QuizViewport.Result.CHANGED,"revealed reordered full choices invalidate fallback");
        String longChoice="An answer with extended wording "+"and more detail ".repeat(20);
        expect(Rules.choices(List.of("Which answer?",longChoice,"B","C","D"),"Which answer?").get(0).equals(longChoice.trim()),"long answer labels are not dropped at 180 characters");
        System.out.println("Passed "+checks+" long quiz scanning checks.");
    }
}
