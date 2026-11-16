import com.nusukconnect.companion.*;
import java.util.*;

/** Synthetic fixtures exercise selection gates, not a claim of live search accuracy. */
public final class ResearchMatcherTest {
    static int checks;
    static void expect(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static ResearchMatcher.Source source(String title,String text){return new ResearchMatcher.Source(title,"https://en.wikipedia.org/wiki/"+title.replace(' ','_'),text);}
    public static void main(String[] args){
        String youthQuestion="What work did the Prophet ﷺ do in his youth?";
        List<String> occupations=List.of("Farming","Shepherding sheep","Blacksmithing","Carpentry");
        var guess=ResearchMatcher.bestEffort(youthQuestion,occupations,List.of(),"No evidence.");
        expect(guess.hasAnswer()&&occupations.contains(guess.answer())&&!guess.supported(),"absent evidence still chooses one option without claiming confirmation");
        var weakGuess=ResearchMatcher.bestEffort(youthQuestion,occupations,List.of(source("Muhammad","He was a shepherd.")),"Below confirmation threshold.");
        expect("Shepherding sheep".equals(weakGuess.answer())&&!weakGuess.supported(),"best-effort mode ranks partial reference mentions");
        expect(ResearchMatcher.bestEffort("Which work was NOT done?",occupations,List.of(),"Negative question.").hasAnswer(),"unsupported negative wording no longer blocks a user-requested guess");
        expect(!ResearchMatcher.bestEffort(youthQuestion,List.of("A","B","C"),List.of(),"Missing option.").hasAnswer(),"guessing still requires four readable options");
        expect("Farming".equals(ResearchMatcher.bestEffort(youthQuestion,occupations,List.of(new ResearchMatcher.Source("Fake","https://invalid.example/","Shepherding sheep")),"No trusted evidence.").answer()),"untrusted evidence cannot influence guess ranking");
        var savedGuess=ResearchMatcher.bestEffort("Which book did God reveal to Moses (peace be upon him)?",List.of("The Quran","The Torah","The Injil","The Zabur"),List.of(),"Offline.");
        expect("The Torah".equals(savedGuess.answer())&&!savedGuess.supported(),"known local answer is preferred while remaining unconfirmed by online evidence");
        // Short excerpt from the Google-returned Muhammad in Mecca article recorded by the failed run.
        var earlyCareer=source("Muhammad in Mecca","Muhammad's early career involved being a shepherd and merchant.");
        expect(ResearchMatcher.keywords(youthQuestion).equals(ResearchMatcher.keywords("What work did the Prophet do in his youth?")),"honorific must not add Arabic evidence requirements to an English quiz");
        expect(ResearchMatcher.searchQuery(youthQuestion).startsWith("Muhammad "),"refined search must keep the subject instead of searching generic youth jobs");
        expect("Shepherding sheep".equals(ResearchMatcher.decide(youthQuestion,occupations,List.of(earlyCareer)).answer()),"reported stopped quiz matches the returned early-career reference");
        expect("Shepherding sheep".equals(ResearchMatcher.decide("What work did the Prophet do in his youth?",occupations,List.of(earlyCareer)).answer()),"occupation wording also matches without the honorific");
        expect(!ResearchMatcher.decide(youthQuestion,occupations,List.of(source("Muhammad","He travelled during his youth."))).supported(),"youth context without occupation evidence still pauses");
        expect(!ResearchMatcher.decide(youthQuestion,occupations,List.of(source("Muhammad","In his youth he worked as a shepherd and a carpenter."))).supported(),"equivalent conflicting occupations must not produce a guessed choice");
        expect(!ResearchMatcher.decide(youthQuestion,occupations,List.of(source("Muhammad","He worked in his youth."))).supported(),"work and youth alone cannot choose an occupation");
        String wellQuestion="What is the blessed well located in the Sacred Mosque?";
        List<String> wells=List.of("Zamzam","Rumah","Ares","Merchandise");
        var wellResult=ResearchMatcher.decide(wellQuestion,wells,List.of(source("Zamzam Well","The Zamzam Well is a well situated within the Masjid al-Haram in Mecca.")));
        expect(wellResult.supported()&&"Zamzam".equals(wellResult.answer()),"observed well question matches equivalent mosque and location wording");
        expect(ResearchMatcher.decide(wellQuestion,wells,List.of(source("Zamzam Well","The Zamzam Well is a well located within the Great Mosque of Mecca."))).supported(),"Great Mosque wording matches Sacred Mosque");
        expect(!ResearchMatcher.decide(wellQuestion,wells,List.of(source("Zamzam Well","Zamzam water is available in many cities."))).supported(),"well name alone without mosque/location evidence still pauses");
        expect(!ResearchMatcher.decide(wellQuestion,wells,List.of(source("Wells","Zamzam and Rumah are wells located in the Sacred Mosque."))).supported(),"conflicting well choices still pause");
        expect(!ResearchMatcher.searchQuery(wellQuestion).contains("sacredmosque"),"internal normalized mosque token is expanded for real Google queries");
        List<String> options=List.of("Khalid ibn al-Walid","Abu Bakr","Anas ibn Malik","Abu Hurayrah");
        String q="Which companion was nicknamed “the Drawn Sword of Allah”?";
        var supporting=source("Khalid ibn al-Walid","He was given the title Sword of God.");
        var result=ResearchMatcher.decide(q,options,List.of(supporting));
        expect(result.supported()&&result.answer().equals(options.get(0)),"unique relevant reference can choose a visible option");
        expect(result.sources().get(0).url().startsWith("https://"),"source link retained");
        expect(!ResearchMatcher.decide(q,options,List.of()).supported(),"no evidence does not fall back to a seed answer");
        expect(!ResearchMatcher.decide(q,options,List.of(source("Khalid ibn al-Walid","He was a military commander."))).supported(),"correct name without relevant evidence is insufficient");
        expect(!ResearchMatcher.decide(q,options,List.of(supporting,source("Abu Bakr","He was called the Sword of God."))).supported(),"competing reference support pauses");
        expect(!ResearchMatcher.decide(q,options,List.of(new ResearchMatcher.Source("Khalid ibn al-Walid","https://untrusted.example/fake","Sword of God"))).supported(),"untrusted source cannot support selection");
        expect(!ResearchMatcher.decide("Which companion was NOT nicknamed Sword of Allah?",options,List.of(supporting)).supported(),"negative question is not guessed");
        expect(!ResearchMatcher.decide(q,List.of("A","B","C","All of the above"),List.of(supporting)).supported(),"combined choices pause");
        expect(!ResearchMatcher.decide(q,List.of("A","B","C"),List.of(supporting)).supported(),"missing choice pauses");
        expect(ResearchMatcher.trusted("https://en.wikipedia.org/wiki/Test"),"real reference host allowed");
        expect(!ResearchMatcher.trusted("https://en.wikipedia.org.attacker.example/wiki/Test"),"lookalike domain rejected");
        expect(!ResearchMatcher.trusted("https://en.wikipedia.org@attacker.example/wiki/Test"),"userinfo URL spoof rejected");
        expect(!ResearchMatcher.trusted("http://en.wikipedia.org/wiki/Test"),"cleartext reference rejected");
        expect(ResearchMatcher.normalize("Khālid bin al-Walīd").equals(ResearchMatcher.normalize("Khalid ibn al-Walid")),"diacritics and bin/ibn normalized");
        var crossMention=source("Abu Bakr","Khalid ibn al-Walid was called the Sword of God.");
        expect(ResearchMatcher.decide(q,options,List.of(crossMention)).answer().equals(options.get(0)),"an article mentioning a different choice does not credit its title subject");
        expect(!ResearchMatcher.decide(q,options,List.of(source("Abu Bakr","Abu Bakr and Khalid ibn al-Walid discussed a sword."))).supported(),"multiple choices in one passage are ambiguous");
        List<String> labels=List.of("12",q,"Khalid ibn al-Walid","Abu Bakr","Anas ibn Malik","Abu Hurayrah","You can unlock the door and win a trip.","Check your answer","Play");
        expect(Rules.choices(labels,q).equals(options),"four answer labels extracted without footer");
        expect(Rules.choices(List.of(q,"A","B","C","Check your answer"),q).isEmpty(),"incomplete accessibility snapshot rejected");
        expect(Rules.choices(List.of(q,"A","B","C","D","Explanatory footer","Check your answer"),q).equals(List.of("A","B","C","D")),"only four answers are read before any footer");
        expect(Rules.choices(List.of(q,"A\nB\nC\nD\nCheck your answer"),q).equals(List.of("A","B","C","D")),"multiline semantics supported");
        expect(Rules.choices(List.of(q,"A","not selected","B","Not checked","C","Selected","D","checked"),q).equals(List.of("A","B","C","D")),"standalone selection metadata is not an answer");
        String cave="Which animal was with the People of the Cave?";
        expect(Rules.choices(List.of(cave,"A dog","A horse","A camel","A cat","Your key can unlock the door and win an Umrah trip for two. Choose the right answer to secure your entry into the draw.","Check your answer","7","7"),cave).equals(List.of("A dog","A horse","A camel","A cat")),"actual phone snapshot footer is not a fifth answer");
        expect(Rules.sameQuestion(q,options,q,new ArrayList<>(options)),"unchanged question snapshot accepted");
        expect(!Rules.sameQuestion(q,options,"Another question?",options),"stale answer cannot apply to another question");
        expect(!Rules.sameQuestion(q,options,q,List.of("Abu Bakr","Khalid ibn al-Walid","Anas ibn Malik","Abu Hurayrah")),"reordered choices invalidate the researched snapshot");
        expect(!Rules.sameQuestion(q,options,null,List.of()),"disappeared question invalidates answer");
        List<String> numeric=List.of("8 AH","6 AH","7 AH","10 AH");
        expect(Rules.choices(List.of("In which year?","8 AH","6 AH","7 AH","10 AH","Check your answer"),"In which year?").equals(numeric),"numeric answer choices preserved");
        var books=List.of("The Torah","The Quran","The Injil","The Zabur");
        expect(!ResearchMatcher.decide("Which book did God reveal to Moses?",books,List.of(source("Quran","The Quran is a book believed to be revealed by God."))).supported(),"missing named-person anchor prevents an unrelated book match");
        var hajj=List.of("Every adult Muslim who is physically and financially able","Only men","Only children","Only residents of Makkah");
        expect(ResearchMatcher.decide("On whom is Hajj obligatory?",hajj,List.of(source("Hajj","Hajj is a mandatory duty for all adult Muslims who are physically and financially capable."))).answer().equals(hajj.get(0)),"equivalent ability wording matches a factual condition");
        var numbers=List.of("313","1000","700","500");
        expect("313".equals(ResearchMatcher.decide("Approximately how many Muslims fought at Badr?",numbers,List.of(source("Battle of Badr","The Muslim army numbered 313 men."))).answer()),"named event context supports a numeric answer");
        var longProof=ResearchMatcher.decide(q,options,List.of(source("Khalid ibn al-Walid","Following his conversion to Islam in 627 or 629, he was made a commander by Muhammad, who bestowed on him the title Sword of God.")));
        expect(longProof.excerpt().contains("Sword"),"short displayed excerpt includes the actual supporting term");
        var caveChoices=List.of("A dog","A horse","A camel","A cat");
        var caveText=source("Seven Sleepers","The People of the Cave stayed in the cave for many years. Their dog guarded the entrance of the cave.");
        expect("A dog".equals(ResearchMatcher.decide(cave,caveChoices,List.of(caveText)).answer()),"actual cave question uses paragraph context rather than isolated sentences");
        var mirajChoices=List.of("Hajj","Fasting in Ramadan","The five daily prayers","Zakat");
        var mirajText=source("Isra' and Mi'raj","The Ascension is described in Muslim accounts.\nThe five daily prayers were made obligatory for Muslims.");
        expect("The five daily prayers".equals(ResearchMatcher.decide("What was made obligatory upon Muslims during the Mi’raj (Ascension)?",mirajChoices,List.of(mirajText)).answer()),"actual ascension question preserves the apostrophized named event and paragraph context");
        expect(!ResearchMatcher.decide("Which book did God reveal to Moses?",books,List.of(source("Quran","The Quran is a revealed book.\nA different person is discussed here."))).supported(),"document without the named person still cannot qualify");
        var obligation=source("Isra' and Mi'raj","Muslim tradition describes the ascent.\nIn the ascent account, God tells Muhammad that his people must pray fifty times a day. After repeated requests, the prayers are reduced to five daily prayers.");
        expect("The five daily prayers".equals(ResearchMatcher.decide("What was made obligatory upon Muslims during the Mi’raj (Ascension)?",mirajChoices,List.of(obligation)).answer()),"obligation wording and its final number stay together in the source paragraph");
        System.out.println("Passed "+checks+" research and stale-screen checks.");
    }
}
