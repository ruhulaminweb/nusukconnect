import com.nusukconnect.companion.GoogleResults;

public final class GoogleResultsTest {
    static int checks;
    static void expect(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        String wiki="https://en.wikipedia.org/wiki/Khalid_ibn_al-Walid";
        expect(wiki.equals(GoogleResults.sourceUrl(wiki)),"direct source accepted");
        expect(wiki.equals(GoogleResults.sourceUrl("https://www.google.com/url?q=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FKhalid_ibn_al-Walid&sa=U")),"q wrapper decoded");
        expect(wiki.equals(GoogleResults.sourceUrl("/url?sa=t&url=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FKhalid_ibn_al-Walid")),"relative url wrapper decoded");
        expect(wiki.equals(GoogleResults.sourceUrl("https://www.google.com.bd/url?url=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FKhalid_ibn_al-Walid")),"regional Google wrapper supported");
        expect(wiki.equals(GoogleResults.sourceUrl("https://www.google.com/url?esrc=s&q=&sa=U&url=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FKhalid_ibn_al-Walid")),"empty q does not hide a valid url parameter");
        expect(GoogleResults.sourceUrl("https://www.google.com/search?q=question")==null,"search page is not a source");
        expect(GoogleResults.sourceUrl("https://accounts.google.com/login")==null,"login page is not evidence");
        expect(GoogleResults.sourceUrl("https://www.google.com/sorry/index")==null,"verification page is not evidence");
        expect(GoogleResults.sourceUrl("https://www.google.com/url?q=javascript%3Aalert%281%29")==null,"script redirect rejected");
        expect(GoogleResults.sourceUrl("https://www.google.com.attacker.example/url?q="+wiki)==null,"fake Google host rejected");
        expect(GoogleResults.sourceUrl("https://www.google.com/url?q=https%3A%2F%2Funtrusted.example")==null,"untrusted redirect target rejected");
        expect(GoogleResults.sourceUrl("https://www.google.com/url?q=%ZZ")==null,"malformed percent escape rejected");
        expect(GoogleResults.sourceUrl("http://en.wikipedia.org/wiki/Test")==null,"cleartext source rejected");
        expect(GoogleResults.browserRedirect("https://www.google.com/goto?url=opaque-token"),"opaque Google redirect can be followed by the browser");
        expect(GoogleResults.sourceUrl("https://www.google.com/goto?url=opaque-token")==null,"opaque redirect is not itself accepted as evidence");
        expect(!GoogleResults.browserRedirect("https://www.google.com.attacker.example/goto?url=opaque"),"redirect lookalike host rejected");
        expect(!GoogleResults.browserRedirect("https://www.google.com/sorry/index?continue=search"),"verification is never treated as a source redirect");
        System.out.println("Passed "+checks+" Google result-link checks.");
    }
}
