package com.nusukconnect.companion;

import android.content.*;
import java.util.List;

/** ADB-only diagnostic in debug builds; never selects/submits in Nusuk. */
public final class ChatGptDebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        Store store=new Store(context);
        CompanionService service=CompanionService.instance;
        if(service==null) {store.put("aiDiagnostic","Accessibility service is not connected.");return;}
        if(service.isRunning()) {store.put("aiDiagnostic","Stop the active run before testing.");return;}
        if("confirmation".equals(intent.getStringExtra("case"))) {service.testAnswerConfirmation();return;}
        String testCase=intent.getStringExtra("case"),question,expected;
        List<String> choices;
        if("well".equals(testCase)) {
            question="What is the blessed well located in the Sacred Mosque?";
            choices=List.of("Zamzam","Rumah","Ares","Merchandise");expected="Zamzam";
        } else if("migration".equals(testCase)) {
            question="To which city did the Prophet ﷺ migrate from Mecca?";
            choices=List.of("Medina (Yathrib)","Khaybar","Ta’if","Abyssinia");expected="Medina (Yathrib)";
        } else {
            question="What work did the Prophet ﷺ do in his youth?";
            choices=List.of("Farming","Shepherding sheep","Blacksmithing","Carpentry");expected="Shepherding sheep";
        }
        store.put("aiDiagnostic","Running ChatGPT Temporary Chat diagnostic: "+question);
        service.testChatGptLookup(question,choices,result->{
            boolean passed=result.reason().startsWith("ChatGPT selected option")&&expected.equals(result.answer());
            store.put("aiDiagnostic",(passed?"PASS":"FAIL")+"\nQuestion: "+question+"\n"+result.reason()+"\nAnswer: "+result.answer()+"\nExpected: "+expected+"\nNo Nusuk answer submitted.");
        });
    }
}
