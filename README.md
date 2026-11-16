# NusukConnec 0.4.23

Completes pending-code recovery when the key counter is unreadable, a question is already open, or a no-key popup appears. A missing result on the Keys/code form is left unconfirmed after 15 seconds and a different code is generated. Door selection retries after three seconds when a category tap leaves the category page open. Tabs and existing settings are retained.

Versions and recovery history are recorded in [CHANGELOG.md](CHANGELOG.md). Git tags identify actual source snapshots; historical versions without source snapshots are not reconstructed as tags. This update is built locally and is not automatically installed on the phone.

If a pending code result has disappeared and the challenge door page is open, inspect the key count and continue selecting/opening the door. The old code remains in submission history but its unknown result is not counted as accepted or rejected. Zero keys returns to generation.

When resuming a pending code result on Nusuk Home, stop at Start the challenge and reopen Keys to inspect the result. CODE_RESULT no longer blindly scrolls unrelated pages or repeats Check the code.

Recognizes Nusuk's “The entered code is not correct” rejection, including wrapped text. The rejected submission is recorded, NusukCodes generates another code, and automation returns to the Keys form instead of timing out at CODE_RESULT.

Android companion for Nusuk, Nusuk Codes by Arshad and the ChatGPT Android app. Package: `com.nusukconnect.companion`. Separate from the AzureOps Flutter project.

## ChatGPT Temporary Chat only

1. Generate/copy a code and redeem it in Nusuk, or resume the waiting question.
2. Read the exact question and four choices in their original order.
3. Open the signed-in ChatGPT Android app and verify Temporary Chat using its explicit banner or Temporary Chat composer hint. The toggle icon alone does not count as verification.
4. Send the question and all four numbered choices. The prompt requests ChatGPT's own reasoning, no web browsing, and one numbered reply bearing a unique quiz identifier.
5. Accept only an unambiguous reply for that identifier, map the number to the original choice, return to Nusuk, and recheck the question/choice order before selecting and submitting.
6. Continue after either Correct or a wrong-answer result until the daily limit.

No ordinary Google search or reference websites are opened. The old Google activity and HTTP lookup implementation have been removed, the live service has no web-lookup call, and the companion no longer requests Internet permission. ChatGPT uses its own app connection and account. No API key is needed.

Temporary Chat is required before any prompt is sent. An existing unsent draft is left untouched. If ChatGPT is unavailable, Temporary Chat cannot be verified, or no matching reply arrives within about one minute, use the previously requested local-guess fallback. If nothing ranks the four choices, select the first. AI replies and guesses can be wrong; answer uncertainty does not stop the loop.

Stop/Cancel, a changed quiz, a locked phone, Nusuk's device-security restriction or missing controls can still prevent safe interaction. Pending submissions are checked, never blindly repeated.

## Five-page interface (0.4.19)

Fixed bottom navigation separates Home, Task, Dashboard, Settings and About. Home retains the requested branding, status and quick Start/Stop controls. Task holds the task/door selectors, Submit/Stop, workflow and latest AI reply. Settings holds daily caps, answer confirmation, ChatGPT mode, scheduling, connection and recovery. All controls are built once, so switching tabs preserves unsaved selections; Start task and Submit still save the current choices before starting.

Dashboard displays recorded code/quiz counts, correct/wrong results, acceptance percentages, pending results and a collapsible local activity log. Verified-cycle total is explicitly identified as all-time; the other metrics are today's counts. No history or estimated successes are fabricated.

About contains version/author information and a plain-language Disclaimer: unofficial status and no affiliation, AI/fallback answer uncertainty, account automation and review, Temporary Chat/local-log privacy, and device/security limitations. The disclaimer adds no new approval gate and does not change task settings.

## Manual review after No (0.4.18)

When confirmation is enabled, **Yes, submit** submits the suggestion. **No, I'll submit** returns to the existing Nusuk quiz page. You can change the suggestion and press Check your answer yourself. The companion observes the result without selecting, scrolling or submitting while you are in control; no 45-second automation timeout applies during manual review. Correct and wrong results both release the next cycle. Manual submissions are counted only when their result is observed, and are not credited as AI-confirmed full cycles. A duplicate result is not consumed twice. Manual-review state survives Stop/restart so resuming does not silently choose a new answer for you.

The Yes/No setting itself retains its meaning: No means fully automatic; Yes enables the per-answer confirmation dialog.

## Optional daily limit (0.4.15)

**Daily code limit check** has Yes/No options. Yes (the default for existing settings) shows the saved number and enforces the 1–349 attempt limit. No hides the number and disables the companion's code and quiz attempt caps throughout the loop. A hidden/blank number is not validated when No is selected. The saved number is retained for switching back to Yes, and counters continue to be recorded. Save settings or Submit applies the selection on the next run. Limits or restrictions actually reported by Nusuk are still respected.

## New in 0.4.14

- Scroll home/challenge navigation when the Start the challenge button is below the viewport; recognize arrow suffixes such as --> and →.
- Read the key counter in the upper-right Play/door header. Zero keys goes to NusukCodes. A no-key popup is dismissed using its Close link before code generation; this recovery also works in quiz-only mode.
- Recognize used-code rejection even when its result text is off screen, then generate a fresh code without resubmitting the rejected one.
- Retain question and choices across scrolls, verify partial viewports without bouncing up/down, and stabilize a complete snapshot before asking ChatGPT. If the long page is still incomplete after 15 seconds, try its first readable choice as an explicitly unconfirmed fallback. Changed question/choice ordering still blocks stale submissions.
- User confirmation before submitting an answer: No (default) keeps automatic submission; Yes displays a scrollable question/choices/selected-answer dialog. Yes in the dialog rechecks the quiz before submission. No in the dialog returns to Nusuk for manual review: change the suggested choice and submit yourself. Automation waits without touching the answer and continues after either correct or wrong result. No confirmation is auto-approved by timeout.
- Save settings without starting a run. The Teach an answer section has been removed; existing local answers are retained for fallback compatibility.

## Setup and privacy

- Install `dist/NusukConnect-debug.apk` and enable NusukConnect automation in Android Accessibility settings.
- Install/sign into ChatGPT yourself. Enable **Ask ChatGPT in Temporary Chat** in NusukConnect.
- Use English in the target apps. Keep the phone unlocked and screen on.
- The service can operate Nusuk, Nusuk Codes and ChatGPT. Only quiz questions and choices are put in ChatGPT; generated codes and passwords are not sent.
- ChatGPT manages Temporary Chat under its own settings. This is not a claim of zero server retention.
- Local logs record workflow stages and the selected answer, not the full ChatGPT conversation or account screen.
- Nusuk has repeatedly refused to open while USB debugging was enabled. Disconnect/disable debugging for standalone trials. No restriction is bypassed.

## Workflow and recovery

The selected task can be full codes + quizzes, redemption only, or quizzes with existing keys. Submit resumes a waiting question or an unsubmitted copied code before generating a fresh code. A pending submitted result takes priority. Keys, choices and submit buttons are scrolled into view before clicking. An answer cannot be reused after a question or choice-order change.

Random door mode visits each of the four doors in a shuffled rotation. Its position advances when a question is actually opened and survives pauses. Daily limits are 1-349, with separate code and quiz attempt counters. Counts and logs use the phone's local date. Clear log preserves counters and pending state.

Daily scheduling is inexact. A locked or sleeping phone receives a notification. Realme battery management can delay the service. The active run prevents timeout for up to six hours but cannot unlock a phone. Stop releases the wake lock and does not disable future daily schedules.

Only a visible Correct result increments correct-answer counts. A wrong result is recorded and continues. Verified-full-cycle credit requires the generated code, key confirmation, quiz read, ChatGPT response, selection, submission and Correct confirmation in the same cycle. A research-only diagnostic does not count as a completed quiz.

## Build and checks

Requires JDK 17, SDK Platform 36 and Build Tools 36.0.0. Configure untracked `local.properties` with the SDK path.

```powershell
.\gradlew.bat assembleDebug
javac -encoding UTF-8 -d build/rules-test app/src/main/java/com/nusukconnect/companion/Rules.java app/src/main/java/com/nusukconnect/companion/ResearchMatcher.java app/src/main/java/com/nusukconnect/companion/GoogleResults.java app/src/main/java/com/nusukconnect/companion/QuizViewport.java app/src/main/java/com/nusukconnect/companion/ChatGptQuiz.java app/src/main/java/com/nusukconnect/companion/QuizScan.java app/src/main/java/com/nusukconnect/companion/ManualAnswer.java tests/RulesTest.java tests/ResearchMatcherTest.java tests/GoogleResultsTest.java tests/QuizViewportTest.java tests/ChatGptQuizTest.java tests/QuizScanTest.java tests/ManualAnswerTest.java
java -cp build/rules-test RulesTest
java -cp build/rules-test ResearchMatcherTest
java -cp build/rules-test GoogleResultsTest
java -cp build/rules-test QuizViewportTest
java -cp build/rules-test ChatGptQuizTest
java -cp build/rules-test QuizScanTest
java -cp build/rules-test ManualAnswerTest
```

The legacy matcher/link tests remain regression coverage for local guessing and earlier code. They do not enable web searching. Current local validation: 245 checks (123 workflow/results, 53 matching, 17 legacy links, 11 quiz viewport, 14 ChatGPT protocol/privacy checks, 17 long-page scanning checks, 10 manual handoff checks). The APK uses personal debug signing. Live recovery in Nusuk remains unverified because the connected debugging session triggered Nusuk's device-security restriction.
