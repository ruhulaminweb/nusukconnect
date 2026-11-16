import com.nusukconnect.companion.ChatGptQuiz;
import java.util.*;

public final class ChatGptQuizTest {
    private static int checks;
    private static void expect(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        String request="a1b2c3d4", other="e5f6g7h8";
        List<String> choices=List.of("Farming","Shepherding sheep","Blacksmithing","Carpentry");
        String prompt=ChatGptQuiz.prompt("What work did the Prophet ﷺ do in his youth?",choices,request);
        expect(ChatGptQuiz.temporaryBanner("This chat won’t appear in history. Learn more"),"phone's explicit Temporary Chat banner is recognized");
        expect(!ChatGptQuiz.temporaryBanner("Temporary chat"),"the toggle icon alone cannot prove Temporary Chat is enabled");
        expect(!ChatGptQuiz.temporaryBanner("Ask ChatGPT"),"normal chat composer never authorizes sending");
        expect(prompt.contains("1) Farming")&&prompt.contains("2) Shepherding sheep")&&prompt.contains("3) Blacksmithing")&&prompt.contains("4) Carpentry"),"prompt includes all four choices in original order");
        expect(prompt.contains("Do not browse or search the web"),"prompt requests ChatGPT reasoning without website search");
        expect(ChatGptQuiz.answerIndex(List.of(prompt),request)==-1,"the submitted prompt itself cannot be mistaken for an answer");
        expect(ChatGptQuiz.answerIndex(List.of("NUSUK_ANSWER_"+request+": 2"),request)==1,"reply maps to the exact second quiz option");
        expect(ChatGptQuiz.answerIndex(List.of("NUSUK_ANSWER_"+other+": 2"),request)==-1,"a prior quiz reply is rejected");
        expect(ChatGptQuiz.answerIndex(List.of("The answer is 2"),request)==-1,"unbound text cannot select an option from another conversation");
        expect(ChatGptQuiz.answerIndex(List.of("NUSUK_ANSWER_"+request+": 5"),request)==-1,"out of range reply is rejected");
        expect(ChatGptQuiz.answerIndex(List.of("NUSUK_ANSWER_"+request+": 2","NUSUK_ANSWER_"+request+": 3"),request)==-1,"conflicting partial responses are not submitted");
        expect(ChatGptQuiz.answerIndex(List.of("NUSUK_ANSWER_"+request+": 2","NUSUK_ANSWER_"+request+": 2"),request)==1,"duplicate text and accessibility descriptions agree");
        expect(ChatGptQuiz.answerIndex(List.of("  NUSUK_ANSWER_"+request+" : 4\n"),request)==3,"whitespace is accepted without changing option numbering");
        boolean rejected=false;try {ChatGptQuiz.prompt("Question?",List.of("A","B","C"),request);}catch(IllegalArgumentException expected){rejected=true;}
        expect(rejected,"missing choices cannot be sent as a complete quiz");
        System.out.println("Passed "+checks+" ChatGPT prompt/reply checks.");
    }
}
