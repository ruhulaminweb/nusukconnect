import com.nusukconnect.companion.ManualAnswer;
import java.util.*;

public final class ManualAnswerTest {
    private static int checks;
    private static void expect(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        ManualAnswer handoff=new ManualAnswer();
        expect(handoff.observe(List.of("Correct!","Play again"))==ManualAnswer.Result.WAITING,"unrelated old result before a quiz was seen is ignored");
        expect(handoff.observe(List.of("Which book?","The Torah","The Quran","Check your answer"))==ManualAnswer.Result.WAITING,"user-controlled question waits without choosing or submitting");
        expect(handoff.observe(List.of("Which book?","The Quran, selected","Check your answer"))==ManualAnswer.Result.WAITING,"changing the suggested choice does not resume automation early");
        for(int i=0;i<120;i++)handoff.observe(List.of("Check your answer"));
        expect(handoff.observe(List.of("Check your answer"))==ManualAnswer.Result.WAITING,"manual review has no automatic confirmation timeout");
        expect(handoff.observe(List.of("Correct!","Play again"))==ManualAnswer.Result.CORRECT,"observed manual correct result releases the loop");
        expect(handoff.observe(List.of("Correct!","Play again"))==ManualAnswer.Result.WAITING,"same manual result cannot be consumed twice");
        ManualAnswer wrong=new ManualAnswer(true);
        expect(wrong.observe(List.of("Wrong answer","Play again"))==ManualAnswer.Result.WRONG,"manual wrong result also releases the loop");
        ManualAnswer fast=new ManualAnswer(true);
        expect(fast.observe(List.of("Correct!"))==ManualAnswer.Result.CORRECT,"fast manual submission is accepted when the quiz was already verified before confirmation");
        ManualAnswer scrolled=new ManualAnswer();
        scrolled.observe(List.of("Check your answer"));
        expect(scrolled.observe(List.of("Incorrect!"))==ManualAnswer.Result.WRONG,"scrolled question can be armed from its submit control");
        expect(new ManualAnswer(true).observe(List.of("Checking your answer…"))==ManualAnswer.Result.WAITING,"loading is not treated as a submitted result");
        System.out.println("Passed "+checks+" manual handoff checks.");
    }
}
