# Protocol (step 1)

Transport: WebSocket at ws://<ip>:<port>/ws. JSON messages.
Envelope: { "t": "<type>", "id": "<optional request id>", "d": { ... } }

## Phone -> Server
- join  d: { pin, name, deviceToken? }
- hb    d: {}   (every 3 s)

## Server -> Phone
- joined d: { studentId, deviceToken }
- error  d: { code }   codes: BAD_PIN | NAME_TAKEN | RATE_LIMITED | CLOSED
- kick   d: {}

## Rules
- Missing heartbeat for 10 s => student status "disconnected".
- Same deviceToken rejoining => same studentId (restore identity).
- NAME_TAKEN: names are unique per session, case-insensitive, unless the deviceToken matches.
- RATE_LIMITED: after 5 wrong PINs from one IP within 60 s, reject joins from that IP for 60 s.

## Discovery

mDNS/DNS-SD, advertised over Bonjour by the instructor. Purely a convenience:
a phone can always join from the scanned QR code or by typing the address, and a
failed advertisement never blocks a session.

Source of truth: `src/main/server/mdns.ts`.

- Service type: `_quiz._tcp.local.` (configured as `type: 'quiz'`; the library
  defaults the protocol to `tcp` and builds `_<type>._tcp`, then appends
  `.local`). Browsers query `_quiz._tcp.local.`
- Instance name: `quiz-<PIN>`, so a phone can browse by the PIN the instructor
  is showing. Example: `quiz-5719`.
- Full name (FQDN): `quiz-<PIN>._quiz._tcp.local` as built by the library (DNS
  conventionally writes this with a trailing dot). Example:
  `quiz-5719._quiz._tcp.local.`
- Port: the port actually bound, published in the SRV record. Starts at 8080 and
  increments to the next free port on EADDRINUSE, so always read it from the
  advertisement rather than assuming 8080.
- TXT records: exactly one, `pin=<PIN>` (for example `pin=5719`). No other TXT
  data is published.
- Address: a standard A/AAAA record for the host, resolved by the phone.

Published records for PIN 5719 on port 8080:

```
PTR  _quiz._tcp.local                      -> quiz-5719._quiz._tcp.local
SRV  quiz-5719._quiz._tcp.local            -> port 8080
TXT  quiz-5719._quiz._tcp.local            -> pin=5719
A/AAAA quiz-5719._quiz._tcp.local          -> host address
```

Advertisements are re-published on every New session, with the new PIN in both
the instance name and the TXT record. The previous advertisement is destroyed
(not just stopped) first so the instance name is released immediately,
otherwise rapid new sessions collide with "Service name is already in use".

PINs are always 4 characters, zero-padded (`generatePin()` in
`src/main/server/session.ts`), so an instance may be named `quiz-0423`.

### Verified on the LAN

Browsing `_quiz._tcp` while the app ran returned exactly one record, with the
name, TXT pin and port all matching the PIN shown in the UI:

```
name: quiz-4875
fqdn: quiz-4875._quiz._tcp.local
type: quiz   protocol: tcp
port: 8080
txt: { "pin": "4875" }
```

Pressing New session (UI PIN 0423 -> 7417) re-advertised as `quiz-7417` with
`txt pin=7417` on the same port 8080, and the old record disappeared.

Note: a VPN adapter such as Cloudflare WARP, or a firewall that blocks
224.0.0.251:5353, silently breaks discovery here even though the app reports no
advertisement error. Browsing for any service type also returning nothing is the
quickest way to tell an environment problem from a real bug. The QR code is
unaffected and remains the reliable path.

### QR payload

Source of truth: `joinPayload()` in `src/renderer/Lobby.tsx`.

The QR code encodes a compact JSON object, UTF-8, and nothing else:

```json
{"ip":"<selectedIp>","port":<port>,"pin":"<PIN>"}
```

Example: `{"ip":"192.168.0.235","port":8080,"pin":"5719"}`

The PIN keeps its zero padding here too, so a session on PIN 0042 encodes as
`{"ip":"10.0.0.5","port":8081,"pin":"0042"}`. The code is rendered as an inline
SVG (`type: 'svg', margin: 1, width: 512`); no error correction level is set,
so the library default applies.

Notes for anyone writing the phone-side scanner:
- It is a bare JSON string, NOT a URL. There is no `http://`, `ws://`, or any
  scheme, so a generic camera app will not open it; the quiz app must parse it.
- `ip` is the instructor-selected address (`selectedIp`), not the QR-hosting
  host, so it may be one of several LAN addresses. The instructor can switch it
  in the UI, which regenerates the QR code.
- The `/ws` path is not in the payload; append it to form the WebSocket URL:
  `ws://<ip>:<port>/ws`.
- The PIN in the payload equals the PIN in the mDNS TXT record. Treat it as a
  convenience for the user, not as authentication on its own: the server still
  requires the `join` message to carry the correct PIN, and rate-limits wrong
  ones.

  # Quiz run (step 2)

## Question shapes (quiz_start.questions[])
Common fields: { qid, type, body, points }
- mcq:            options: [{id,text}]                        answer value: "<optionId>"
- tf:             (none)                                      answer value: true | false
- identification: (none)                                      answer value: "<text>"
- fillin:         blanks: <n> (body marks blanks as ___)      answer value: ["<text>", ...] (length n)
- enumeration:    count: <n>                                  answer value: ["<item>", ...] (up to n)
- problem:        (none)                                      answer value: "<text>" (final answer)
- matching:       left: [{id,text}], right: [{id,text}]       answer value: { "<leftId>": "<rightId>", ... }
  Answer keys, accepted answers, and tolerances NEVER appear in any message to phones.
- connect:        prompts: [{id,text}], answers: [{id,text}]   answer value: { "<promptId>": "<answerId>", ... }
  (Activity type. The phone shows prompts and answers as two columns of dots/cards, and the student draws a line from each prompt to its answer. Each prompt connects to at most one answer, and each answer is used by at most one prompt. The answers list may contain extra decoys that match nothing. Items may include an image later, but not in step 2.)

## Phone -> Server
- answer  d: { qid, value, seq }   (seq increases for every answer the phone sends; persisted on the phone)

## Server -> Phone
- quiz_start    d: { quizId, title, questions: [...], endsAt, serverTime }   (epoch ms)
- answers_state d: { answers: { "<qid>": <value>, ... } }   (sent right after quiz_start when a known student rejoins mid-quiz)
- ack           d: { qid, seq }
- quiz_end      d: { reason: "time" | "instructor" }
- error codes added: QUIZ_ENDED | BAD_ANSWER | UNKNOWN_QUESTION

## Rules
- Session status: lobby -> running -> ended.
- Time: the phone computes offset = serverTime - phoneNow when quiz_start arrives, and remaining = endsAt - (phoneNow + offset).
- Answers: the server keeps the latest value per (student, qid). An answer with seq <= the last seq seen for that (student, qid) is ignored but still acked. A value that doesn't match the question type gets BAD_ANSWER. After the quiz ends, answers get QUIZ_ENDED.
- Joining while running: only a known deviceToken can rejoin; anyone else gets CLOSED.
- Dropped connections do NOT pause the quiz yet (step 3 adds pause/lock). The phone just reconnects and resends unacked answers.
- Instructor commands (start, end) are IPC-only, never WebSocket.

# Lock and pause (step 3)

## Changes to existing messages
- quiz_start d gains: lockMode (boolean)
- error codes added: PAUSED

## Phone -> Server
- focus_lost    d: { reason: "background" | "window" | "unpinned" }
- focus_gained  d: {}
- resume_request d: {}   (a request only; it never resumes anything by itself)

## Server -> Phone
- paused  d: { reason: "focus" | "network" }
- resumed d: {}
- lock    d: { on: boolean }   (instructor toggled lock during the quiz)

## Rules
- A student is paused when (a) lockMode is on and the server receives focus_lost, or (b) the quiz is running and no heartbeat arrived for 10 s.
- focus_lost while lockMode is off is ignored (but logged). focus_lost while already paused is logged but changes nothing.
- While paused, an answer is rejected with error PAUSED and not stored. The phone keeps it queued and resends after resumed.
- The quiz clock never stops for paused students.
- Only the instructor can resume (IPC: approveResume(studentId), approveAllResume()). The server then sends resumed.
- A student paused for "network" who rejoins with the same deviceToken during the quiz gets quiz_start, answers_state, then paused { reason: "network" }.
- When the instructor turns lock off, students paused for "focus" are resumed automatically (server sends resumed). Network pauses are not.
- Events (focus_lost, focus_gained, paused, resumed, resume_request, disconnect, reconnect) are logged in memory per student with a timestamp. Count of focus_lost is shown per student.
- The server never trusts the phone to enforce anything. Lock is a deterrent plus visibility.

# Finish (step 5)

## Phone -> Server
- finish  d: {}   (the student submits; no further answers are accepted from them)

## Server -> Phone
- finished d: {}   (confirmation; also sent after a rejoin if the student already finished)
- error codes added: FINISHED (an answer arrived after finish)

## Rules
- finish is valid only while the quiz is running and the student is not paused. While paused the server replies PAUSED and does nothing. Finish is idempotent: a repeated finish gets another finished.
- After finish, answer messages get FINISHED and are not stored or acked. The student's stored answers stay as they are.
- A finished student is never paused: focus_lost and heartbeat timeouts are logged but do not pause them.
- A finished student who rejoins with the same deviceToken during the quiz gets quiz_start, answers_state, then finished.
- The quiz ends only on the timer or the instructor (quiz_end). When every connected student is finished the instructor sees a banner, but nothing ends automatically.
- Instructor commands stay IPC-only.