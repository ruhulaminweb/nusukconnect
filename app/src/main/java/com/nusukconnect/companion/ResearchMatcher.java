package com.nusukconnect.companion;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative text evidence matching, not an AI model or a guarantee of correctness. */
public final class ResearchMatcher {
    public record Source(String title,String url,String text) {}
    public record Decision(String answer,String reason,List<Source> sources,String excerpt,boolean confirmed) {
        public Decision(String answer,String reason,List<Source> sources,String excerpt) { this(answer,reason,sources,excerpt,answer!=null); }
        public boolean supported() { return answer!=null && confirmed; }
        public boolean hasAnswer() { return answer!=null; }
    }
    private static final Set<String> STOP=Set.of(("a an the which what who where when how why did does do was were is are has have had "
        +"of to in on at by and or for from with as that this these those be been being it its his her he she they their "
        +"named called nicknamed known famous drawn companion companions messenger prophet muhammad peace upon him pbuh "
        +"god allah according islam islamic give given approximately approximate roughly about many whom made during").split(" "));
    private ResearchMatcher() {}
    public static String normalize(String input) {
        String s=Normalizer.normalize(input==null?"":input,Normalizer.Form.NFKD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
        // NFKD expands ﷺ into four Arabic words. It is an honorific, not quiz evidence.
        s=s.replaceAll("صلى\\s+الله\\s+عليه\\s+و\\s*سلم"," ")
            .replaceAll("جل\\s+جلاله"," ");
        return s.replaceAll("\\bbin\\b","ibn").replaceAll("\\ballah\\b","god")
            .replaceAll("\\b(musa)\\b","moses").replaceAll("\\b(isa)\\b","jesus")
            .replaceAll("\\b(makkah)\\b","mecca").replaceAll("\\b(madinah)\\b","medina")
            .replaceAll("\\b(muazzin|muadhdhin|muadhin|muezzins)\\b","muezzin")
            .replaceAll("\\b(revealed|reveals|revelation)\\b","reveal")
            .replaceAll("\\bbooks\\b","book").replaceAll("\\s+"," ");
    }
    private static String semantic(String s) {
        return normalize(s).replaceAll("\\bmuslims\\b","muslim").replaceAll("\\b(fought|fighting)\\b","fight")
            .replaceAll("\\b(mandatory|compulsory|required|must)\\b","obligatory").replaceAll("\\bevery\\b","all")
            .replaceAll("\\bphysically\\b","physical").replaceAll("\\bfinancially\\b","financial")
            .replaceAll("\\b(capable|capability|ability)\\b","able")
            .replaceAll("\\b(shepherd(?:ing|ed)?(?: the)? sheep|tend(?:ing|ed)?(?: the)? sheep|herd(?:ing|ed)?(?: the)? sheep|shepherds)\\b","shepherd")
            .replaceAll("\\b(farming|farmers)\\b","farmer")
            .replaceAll("\\b(blacksmithing|blacksmiths)\\b","blacksmith")
            .replaceAll("\\b(carpentry|carpenters)\\b","carpenter");
    }
    private static String topic(String text) {
        return semantic(text).replaceAll("\\bmi raj\\b","miraj").replaceAll("\\bascension\\b","miraj")
            .replaceAll("\\b(masjid al haram|sacred mosque|great mosque(?: of mecca)?)\\b","sacredmosque")
            .replaceAll("\\b(located|situated)\\b","locate")
            .replaceAll("\\bearly career\\b","youth work")
            .replaceAll("\\b(early life|young(?: man)?|childhood)\\b","youth")
            .replaceAll("\\b(worked|working|job|occupation|career)\\b","work")
            .replaceAll("\\b(migrated|migrate|migration|emigrated|emigrate|emigration)\\b","migrate");
    }
    public static List<String> keywords(String question) {
        LinkedHashSet<String> words=new LinkedHashSet<>();
        for (String w:topic(question).split(" ")) if (!STOP.contains(w) && !w.isEmpty()) words.add(w);
        return new ArrayList<>(words);
    }
    public static String searchQuery(String question) {
        String query=String.join(" ",keywords(question)).replace("sacredmosque","Masjid al Haram");
        // The subject is a stopword for scoring, but must not disappear from a refined search.
        if(question.contains("ﷺ") || normalize(question).contains("muhammad"))query="Muhammad "+query;
        return query;
    }
    public static boolean trusted(String url) {
        try {
            URI u=URI.create(url);String host=u.getHost();if(host!=null)host=host.toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(u.getScheme()) || host==null || u.getUserInfo()!=null) return false;
            for (String domain:List.of("wikipedia.org","sunnah.com","quran.com","corpus.quran.com","britannica.com"))
                if (host.equals(domain)||host.endsWith("."+domain)) return true;
        } catch (IllegalArgumentException ignored) {}
        return false;
    }
    private static String optionName(String option) { return semantic(option).replaceFirst("^the ",""); }
    private static boolean phrase(String text,String value) { return (" "+text+" ").contains(" "+value+" "); }
    private static boolean mentions(String text,String option) {
        for (String alias:Rules.answerOptions(option)) {
            if (phrase(text,optionName(alias))) return true;
            String words="\\b(the|a|an|who|which|is|are|was|were|and|of|to)\\b";
            String compactText=text.replaceAll(words," ").replaceAll("\\s+"," ").trim();
            String compactOption=optionName(alias).replaceAll(words," ").replaceAll("\\s+"," ").trim();
            if(!compactOption.isEmpty()&&phrase(compactText,compactOption))return true;
        }
        return false;
    }
    private static String shortExcerpt(String text,List<String> keywords) {
        String[] words=text.trim().split("\\s+");int anchor=0;
        for(int i=0;i<words.length;i++)if(keywords.contains(semantic(words[i]))){anchor=i;break;}
        int start=Math.max(0,anchor-8),end=Math.min(words.length,start+20);start=Math.max(0,end-20);
        return (start>0?"… ":"")+String.join(" ",Arrays.copyOfRange(words,start,end))+(end<words.length?" …":"");
    }
    private static Decision uncertain(String reason,List<Source> docs) {
        List<Source> references=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Source doc:docs)if(trusted(doc.url())&&seen.add(doc.url())){references.add(doc);if(references.size()==6)break;}
        return new Decision(null,reason,references,"");
    }
    /** User-requested fallback: always choose among four readable choices, without claiming confirmation. */
    public static Decision bestEffort(String question,List<String> options,List<Source> docs,String why) {
        if(options.size()!=4 || new HashSet<>(options).size()!=4)return uncertain("Four distinct choices are required.",docs);
        int[] scores=new int[4];
        for(Source doc:docs) {
            if(!trusted(doc.url()))continue;
            String text=topic(doc.title()+" "+doc.text());int coverage=0;
            for(String key:keywords(question))if(phrase(text,key))coverage++;
            for(int i=0;i<4;i++)if(mentions(text,options.get(i)))scores[i]=Math.max(scores[i],1+coverage);
        }
        int best=0;
        for(int i=1;i<4;i++)if(scores[i]>scores[best])best=i;
        String saved=Rules.answer(question,Rules.seeds());
        if(saved!=null)for(int i=0;i<4;i++)for(String alias:Rules.answerOptions(saved))
            if(Rules.normalize(alias).equals(Rules.normalize(options.get(i))))best=i;
        String reason=(why==null?"Evidence was inconclusive.":why)+" Trying an unconfirmed choice as requested.";
        if(Arrays.stream(scores).max().orElse(0)==0 && saved==null)reason+=" No evidence ranked the choices; using the first option.";
        return new Decision(options.get(best),reason,uncertain("",docs).sources(),"",false);
    }
    private static List<String> passages(String text) {
        List<String> result=new ArrayList<>();
        for(String paragraph:text.split("\\n+")) {
            paragraph=paragraph.trim();if(paragraph.isEmpty())continue;
            if(paragraph.length()<=1800){result.add(paragraph);continue;}
            String[] sentences=paragraph.split("(?<=[.!?])\\s+");
            for(int i=0;i<sentences.length;i++){
                StringBuilder window=new StringBuilder(sentences[i]);
                for(int j=i+1;j<Math.min(i+3,sentences.length)&&window.length()+sentences[j].length()<1800;j++)window.append(' ').append(sentences[j]);
                if(window.length()<=1800)result.add(window.toString());
            }
        }
        return result;
    }
    public static Decision decide(String question,List<String> options,List<Source> docs) {
        if (options.size()!=4 || new HashSet<>(options).size()!=4) return new Decision(null,"Four distinct choices are required.",List.of(),"");
        for(String option:options)if(normalize(option).matches(".*\\b(all of the above|none of the above|both of the above)\\b.*"))
            return new Decision(null,"Combined-answer choices need manual review in the free text matcher.",List.of(),"");
        String q=normalize(question);
        if (Pattern.compile("\\b(not|except|incorrect|false|least)\\b").matcher(q).find())
            return new Decision(null,"The free text matcher cannot reliably resolve this negative/comparative question.",List.of(),"");
        List<String> keys=keywords(question);
        if (keys.isEmpty()) return new Decision(null,"The question has no specific searchable terms.",List.of(),"");
        int required=Math.max(1,(int)Math.ceil(keys.size()*.65));
        Set<String> anchors=new HashSet<>();
        Matcher names=Pattern.compile("\\b\\p{Lu}[\\p{L}’'-]*").matcher(question);
        String questionContext=topic(question);
        while(names.find()) { String name=topic(names.group());if(!STOP.contains(name)&&phrase(questionContext,name))anchors.add(name); }
        int[] scores=new int[4];
        @SuppressWarnings("unchecked") List<Source>[] evidence=new List[4];
        String[] quotes=new String[4];
        for (int i=0;i<4;i++) { evidence[i]=new ArrayList<>();quotes[i]=""; }
        for (Source doc:docs) {
            if (!trusted(doc.url())) continue;
            String documentContext=topic(doc.title()+" "+doc.text());boolean anchored=true;
            for(String anchor:anchors)if(!phrase(documentContext,anchor))anchored=false;
            if(!anchored)continue;
            // Keep pronouns and their subject together within bounded source paragraphs, never across the entire article.
            for (String passage:passages(doc.text())) {
                if (passage.length()<12) continue;
                String text=topic(passage),context=topic(doc.title())+" "+text;
                int coverage=0;
                for (String key:keys) if (phrase(context,key)) coverage++;
                if (coverage<required) continue;
                for (int i=0;i<4;i++) {
                    boolean explicit=mentions(text,options.get(i));
                    boolean subject=mentions(semantic(doc.title()),options.get(i));
                    if (!explicit && !subject) continue;
                    // A title is not sufficient when this particular passage explicitly discusses another choice.
                    boolean other=false;
                    for (int j=0;j<4;j++) if (j!=i && mentions(text,options.get(j))) other=true;
                    if (other) continue;
                    int score=coverage;
                    if (score>scores[i]) { scores[i]=score; quotes[i]=shortExcerpt(passage,keys); evidence[i].clear(); }
                    if (score==scores[i] && evidence[i].stream().noneMatch(s->s.url().equals(doc.url()))) evidence[i].add(doc);
                }
            }
        }
        int best=-1,runner=0;
        for (int i=0;i<4;i++) if (best<0||scores[i]>scores[best]) best=i;
        for (int i=0;i<4;i++) if (i!=best) runner=Math.max(runner,scores[i]);
        if (best<0||scores[best]<required) return uncertain("No reference passage clearly supports one of the visible choices.",docs);
        if (runner>=required) return uncertain("Reference passages also support another choice; automatic submission is paused.",docs);
        return new Decision(options.get(best),"One choice has relevant reference support; the other choices did not meet the same evidence threshold.",evidence[best],quotes[best]);
    }
}
