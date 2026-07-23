package com.echoling.app.transcription

/**
 * Pure Kotlin port of `c:/Users/MING/myagent/split_srt_sentences.py` —
 * splits long ASR transcript cues into shorter, complete-sentence pieces
 * at sentence / clause / comma / relative / filler boundaries, with
 * subject + verb validation on each candidate split.
 *
 * The Python script was originally tuned against BBC News transcripts
 * (the same register as our Vosk-small output for news / documentary
 * media). Behavior parity is the priority: any case the Python script
 * splits, this class splits; any case the Python leaves alone, this
 * class leaves alone. Drift should only ever be a Kotlin-side bug fix.
 *
 * **Algorithm (per oversize cue)** — runs in `findBestSplit`, with
 * recursive fallback on pieces that are still too long:
 *  1. **Sentence boundary** — `. ` / `? ` / `! ` + capital, skipping
 *     common abbreviations (Mr., U.S., BBC, ...) and decimals (5.5).
 *     Most natural — speaker paused, the SRT cue chain already aligns.
 *  2. **Comma boundary** — FIRST comma where both sides are complete
 *     sentences (subject + verb, non-dangling). Catches appositive
 *     clauses ("...centre of Leipzig, in the very touristy area, ...").
 *  3. **Clause boundary** — `, and ` / ` and ` / ` but ` / ` or ` /
 *     ` so ` between two complete clauses. Requires the word after the
 *     conjunction to be a subject-starter AND both sides to have a
 *     verb — guards against `drones and missiles` (compound noun) and
 *     `killed and 22 injured` (parallel predicate). When multiple
 *     valid `and` matches exist, the most-balanced one is chosen.
 *  4. **Relative clause** — `, which is/was/...` / `, that said/...`
 *     between a main clause and a subordinate clause with its own verb.
 *  5. **Filler** — `, you know` / `, I mean` / `, like` — last resort
 *     for unscripted-speaker register; can be split before any other
 *     pass lands.
 *  6. **No clean split** — return the original cue as-is. Better an
 *     overlong cue than a mid-clause chop.
 *
 * **Validation** ([isCompleteSentence]) rejects:
 *  - too-few-words (< [MIN_PIECE_WORDS])
 *  - dangling trailing conjunction / subordinator (`and`, `because`,
 *    `that`, `which`, ...) — the defining signal of a mid-clause cut
 *  - dangling trailing punctuation (`(`, `[`, `/`, `-`, `,`)
 *  - no finite verb on either side
 *  - doesn't start with a subject-like word (rejects dangling
 *    participle / adverb / bare noun: "Trying to disrupt...", "and 22
 *    injured", "drones at targets")
 *
 * **Why a separate class** rather than living in [SrtSynthesizer]:
 * `findBestSplit` is a pure text transformation — no Android imports,
 * no SRT I/O, no timestamps. Pulling it out lets `SentenceSegmenterTest`
 * cover 30+ cases with the exact strings + expected splits, without
 * standing up a WorkManager + ffmpeg + Vosk fixture.
 *
 * **Why constants live here** rather than in [SrtSynthesizer]: the
 * smart-split thresholds (`TARGET_WORDS`, `MIN_CUE_MS`, `OVERLAP_MS`)
 * ARE the policy — `SrtSynthesizer.redistributeTimestamps` reads them
 * back rather than redeclaring its own. One source of truth, mirroring
 * the Python module layout.
 */
internal object SentenceSegmenter {

    // ---- Tunables (mirror Python module-level constants) ----

    /**
     * Cues longer than this word count are candidates for splitting.
     *
     * 10 (lower than the previous 14 / Python's 18) because the user's
     * requirement #2 is "如果一句话太长，以 and/but/or/so 切分" — that's
     * about *clause structure*, not raw length. A 12-word sentence like
     * "She came home yesterday afternoon and she left the party very
     * quickly" has an obvious `and`-clause boundary; treating it as one
     * subtitle makes the cue unreadable. Lowering to 10 means every
     * candidate cue gets evaluated; [isCompleteSentence] still rejects
     * bad split points (parallel predicate, compound noun, shared
     * subject), so the safety net doesn't change — short sentences
     * without clean boundaries remain whole.
     */
    const val TARGET_WORDS = 10

    /** Each split piece must have at least this many words. */
    const val MIN_PIECE_WORDS = 3

    /** Minimum subtitle display time per cue, in ms. */
    const val MIN_CUE_MS = 1500L

    /** Maximum subtitle display time per cue, in ms. */
    const val MAX_CUE_MS = 7000L

    /**
     * Look-ahead overlap between consecutive cues, in ms. Next cue's
     * start = current cue's end - OVERLAP_MS. Shadowing/dictation
     * learners see the next line briefly before the current ends.
     */
    const val OVERLAP_MS = 750L

    // ---- Lexicon (mirror Python module-level sets) ----

    /**
     * Common English abbreviations. A `.` after one of these is NOT
     * a sentence end. Keys are lowercase.
     */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "ft",
        "vs", "etc", "inc", "ltd", "co", "corp",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
        "mon", "tue", "wed", "thu", "fri", "sat", "sun",
        "adm", "capt", "col", "gen", "gov", "lt", "maj", "pres", "rev", "sgt",
        "hon", "cpl", "pvt",
        "us", "uk", "uae", "eu", "un", "nato",
        "bbc", "cnn", "nyc", "la", "dc", "usa",
        "ceo", "cfo", "cto", "ai", "it", "ok", "gps", "mp", "pm", "am",
        "a.m", "p.m", "i.e", "e.g",
        "dot", "pod", // ASR quirks seen in BBC transcripts
    )

    /** Words that signal a mid-clause cut if they end a piece. */
    private val DANGLING_ENDS = setOf(
        "and", "or", "but", "so", "because", "that", "which", "if", "when", "while",
        "where", "although", "though", "since", "until", "unless", "whereas",
        "as", "like",
    )

    /** Auxiliary verbs (covers is/has/will/may etc. without a giant verb lexicon). */
    private val AUX_VERBS = setOf(
        "is", "are", "was", "were", "be", "been", "being", "am",
        "has", "have", "had", "having",
        "do", "does", "did",
        "will", "would", "shall", "should",
        "can", "could", "may", "might", "must",
        "i'm", "i've", "i'll", "i'd", "you're", "you've", "you'll",
        "he's", "she's", "it's", "we're", "we've", "we'll", "they're", "they've", "they'll",
        "isn't", "aren't", "wasn't", "weren't",
        "hasn't", "haven't", "hadn't",
        "doesn't", "don't", "didn't",
        "won't", "wouldn't", "shan't", "shouldn't",
        "can't", "cannot", "couldn't", "mayn't", "mightn't", "mustn't",
    )

    /** Irregular past-tense / past-participle verbs not caught by suffix rules. */
    private val IRREGULAR_VERBS = setOf(
        "said", "told", "made", "gave", "took", "went", "came", "saw",
        "knew", "thought", "found", "brought", "bought", "caught", "fought",
        "taught", "sold", "held", "kept", "left", "lost", "met", "paid",
        "sent", "won", "done", "gone", "been", "had", "did", "hid", "slid",
        "lit", "sat", "stood", "hung", "struck", "stuck", "dug", "spun",
        "sung", "begun", "drunk", "shrunk", "sunk", "stunk", "woken", "broken",
        "spoken", "chosen", "frozen", "stolen", "driven", "ridden", "written",
        "eaten", "beaten", "fallen", "forgotten", "gotten", "hidden", "torn",
        "worn", "flown", "grown", "thrown", "blown", "drawn", "known", "shown",
        "understood", "undertaken", "overcome", "withdrawn", "forbidden",
        "forgiven", "mistaken", "shaken", "risen", "arisen", "awoken",
        "set", "put", "cut", "hit", "let", "shut", "spread", "cost", "hurt",
        "split", "quit", "shed", "beat", "dealt", "meant",
        "led", "read", "fed", "bled", "bred", "sped", "fled", "swept", "wept",
        "slept", "crept", "leapt",
        "says", // 3rd-person present
    )

    /**
     * Verb forms in `-es` (3rd-person singular present). The Python script
     * only matches -ches / -shes / -xes / -zes / -oes / -ses sound patterns
     * plus a small explicit list — which misses common BBC/news verbs like
     * `continues`, `contains`, `expects`, `requires`, `completes`. We extend
     * the explicit list with the most common ones seen in news transcripts
     * so the smart-split can recognize "and the conflict continues..." as a
     * valid clause boundary instead of leaving the whole sentence un-split.
     */
    private val ES_SUFFIX_VERBS = setOf(
        // Original short list (Python parity)
        "takes", "goes", "does", "gives", "lives", "moves",
        "leaves", "loves", "proves", "serves", "tries", "dies",
        "lies", "ties", "agrees", "freezes", "sees", "feels",
        // Common BBC 3rd-person verbs not caught by sound patterns
        // (-ches/-shes/-xes/-zes/-oes/-ses). The hasVerb() suffix check
        // requires both length > 4 AND one of those 6 sound endings;
        // "comes / makes / plays / stays" end in -es with no sound-
        // pattern match, so without an explicit entry the splitter
        // would reject a sentence like "He works hard and comes home
        // late" as right-side-not-complete.
        "comes", "makes", "plays", "stays", "shows", "says",
        "tells", "calls", "holds", "keeps", "leads", "needs",
        // Extended BBC / news-register 3rd-person verbs.
        // (Not adding the full -es heuristic because plural nouns like
        // "oranges" / "issues" / "forces" would also match — explicit
        // list keeps false-positive rate low.)
        "continues", "remains", "becomes", "happens", "appears",
        "seems", "decides", "requires", "expects", "provides",
        "suggests", "indicates", "contains", "demonstrates",
        "includes", "considers", "offers", "suffers", "explains",
        "claims", "believes", "receives", "discovers", "encounters",
        "ignores", "supports", "reduces", "produces", "uses",
        "involves", "hopes", "prepares", "declares", "expresses",
        "fails", "intends", "refuses", "tends", "pretends",
        "represents", "threatens", "completes", "affects", "reflects",
        "causes", "creates", "addresses", "assumes", "confirms",
        "denies", "describes", "develops", "discusses", "estimates",
        "faces", "follows", "identifies", "increases", "introduces",
        "manages", "mentions", "notices", "observes", "permits",
        "permits", "presents", "promises", "proposes", "protects",
        "pursues", "realizes", "recognizes", "refers", "regards",
        "relates", "removes", "reports", "requests", "resembles",
        "responds", "reveals", "shares", "states", "struggles",
        "studies", "succeeds", "survives", "suspects", "touches",
        "treats", "urges", "wishes", "worries",
    )

    /**
     * Words that can plausibly start a complete sentence (subject /
     * fronted prepositional phrase / coordinator-after-period).
     * Sorted longest-first by [sortedSubjectStarters], used to build
     * the alternation in [CLAUSE_RE].
     */
    private val SUBJECT_STARTERS = setOf(
        // Pronouns
        "i", "you", "he", "she", "it", "we", "they",
        "me", "him", "her", "us", "them",
        "my", "your", "his", "its", "our", "their",
        "this", "that", "these", "those",
        "there", "here", "what", "who", "whom", "where", "when",
        // Articles + determiners (almost always followed by subject noun)
        "the", "a", "an",
        "some", "any", "no", "all", "every", "each", "both", "several", "many", "few",
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        // Coordinating conjunctions starting a sentence (news register uses these freely)
        "but", "and", "or", "so", "yet", "for", "nor",
        // Discourse markers
        "also", "meanwhile", "furthermore", "moreover",
        "however", "therefore", "thus", "additionally", "finally",
        "first", "second", "third", "next", "then", "now",
        "perhaps", "maybe", "instead", "still",
        // Prepositions introducing fronted prepositional phrases
        // (valid complete-sentence starters in English)
        "in", "on", "at", "by", "for", "with", "from", "to", "of",
        "about", "into", "onto", "upon", "over", "under",
        "after", "before", "during", "until", "between", "among",
        "through", "across", "against", "around", "along", "beyond",
        "since",
        // News-register noun phrase starters (drop-headline fragments)
        "fears", "reports", "according", "amid", "backlash",
        // Temporal sentence openers (BBC/news register uses these as
        // sentence-starters, e.g. "and today the PM announced...")
        "today", "yesterday", "tomorrow", "tonight", "nowadays",
        "recently", "currently", "ultimately", "eventually",
        "subsequently", "previously", "initially", "originally",
        "presently", "momentarily", "meanwhile", "afterwards",
        // Imperative / question forms
        "let", "does", "did",
        "is", "are", "was", "were",
        "has", "have", "had", "can", "could", "will", "would", "should",
        // Short answers
        "yes", "no",
    )

    // Contraction suffixes used by [startsWithSubject]: strip "'s" → "it", "'re" → "we", ...
    private val CONTRACTION_SUFFIXES = listOf(
        "'s", "'re", "'ve", "'ll", "'d", "'m", "'t",
    )

    // ---- Regex constants (mirror Python re.compile at module load) ----

    private val WORD_RE = Regex("\\b[\\w']+\\b")

    /** Pass 1: sentence boundary — punctuation + space + capital. */
    private val SENTENCE_RE = Regex("([.?!])\\s+(?=[A-Z])")

    /**
     * Pass 1.5: comma boundary — first comma where both sides are
     * complete sentences. Appositive clauses ("..., ..., ..., ...")
     * split linearly; recursion handles multi-cut decomposition.
     */
    private val COMMA_RE = Regex(",\\s+")

    /**
     * Pass 2: clause boundary — optional-comma + space + (and|but|so|or)
     * + space + (subject-starter | capitalized word). REJECTED by
     * caller unless both sides contain a finite verb (so `drones and
     * missiles` fails the verb check, while `Germany and backlash as
     * celebrities` passes).
     *
     * **Lookahead wrapper** `(?=,?\s+(and|...)\s+...)` — the regex finds
     * the position BEFORE the conjunction but does NOT consume it. This
     * way the split point lands before `and` and the right piece starts
     * WITH the conjunction (after [stripTrim] removes any preceding
     * comma / whitespace), so subtitles read "X. And Y continues..."
     * instead of "X. Y continues..." — preserving the user's stated
     * requirement that and/but/so/or connectors not be dropped.
     *
     * **`(?-i:[A-Z])` cap alternative** — the global `IGNORE_CASE` flag
     * makes `[A-Z]` also match lowercase letters, which lets the regex
     * wrongly match ` and drones ` / ` and offered ` / ` and economists `
     * (none of which are real clause-initial subjects). The inline
     * `(?-i:)` re-enables case-sensitivity for the cap alternative so it
     * only accepts genuinely-capitalized words like `and The conflict`.
     *
     * SUBJECT_STARTER_ALT is sorted longest-first so `themselves`
     * matches before `them`, `however` before `how`, etc.
     */
    private val SUBJECT_STARTER_ALT: String = SUBJECT_STARTERS
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        .joinToString("|") { Regex.escape(it) }
    private val CLAUSE_RE = Regex(
        "(?=,?\\s+(and|but|so|or)\\s+(?:($SUBJECT_STARTER_ALT|(?-i:[A-Z])[\\w'\\-]*)\\b))",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Pass 4: relative clause — `, which is/was/...` or `, that said/...`
     * introducing a subordinate clause with its own verb.
     */
    private val REL_VERBS = listOf(
        "is", "are", "was", "were", "has", "have", "had",
        "will", "would", "can", "could", "may", "might", "must", "should",
        "said", "says", "told", "tells", "means", "shows", "show", "suggests", "suggest",
    ).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val REL_RE = Regex(
        ",\\s+(which|that)\\s+(?=($REL_VERBS)\\b)",
        RegexOption.IGNORE_CASE,
    )

    /** Pass 5: filler — `, you know` / `, I mean` / `, like` / `, you see` / `, actually`. */
    private val FILLER_RE = Regex(
        ",\\s+(you know|I mean|like|you see|actually)\\s+",
        RegexOption.IGNORE_CASE,
    )

    /** Pass 5b: conjunction + filler — ` and you know` / ` but I mean`. */
    private val CONJ_FILLER_RE = Regex(
        "\\s+(and|but)\\s+(you know|I mean|you see|like)\\s+",
        RegexOption.IGNORE_CASE,
    )

    // ============================================================
    //   PUBLIC API
    // ============================================================

    /**
     * Find the best list of pieces to split [text] into. Returns
     * `[text]` if no clean split exists or `text` is already short.
     *
     * Mirrors `find_best_split` in the Python script.
     */
    fun findBestSplit(
        text: String,
        targetWords: Int = TARGET_WORDS,
        maxRecurseDepth: Int = 4,
    ): List<String> = findBestSplitRecursive(text, targetWords, depth = 0, maxDepth = maxRecurseDepth)

    /** Count word tokens — letters / digits / apostrophes, word-bounded. */
    fun wordCount(text: String): Int = WORD_RE.findAll(text).count()

    /**
     * Validate that [text] is a usable subtitle piece (not cut
     * mid-clause). See class KDoc for the rejection rules.
     */
    fun isCompleteSentence(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val words = WORD_RE.findAll(trimmed.lowercase()).map { it.value }.toList()
        if (words.size < MIN_PIECE_WORDS) return false

        if (words.last() in DANGLING_ENDS) return false
        if (trimmed.endsWith('/') || trimmed.endsWith('-') ||
            trimmed.endsWith('(') || trimmed.endsWith('[') || trimmed.endsWith(',')) {
            return false
        }

        if (!hasVerb(trimmed)) return false
        if (!startsWithSubject(trimmed)) return false
        return true
    }

    /**
     * Post-process a split piece: capitalize first letter, add terminal
     * period if missing. Without this, splitting "...also two people
     * have been killed" leaves the first piece dangling without a
     * period; subtitle viewers render it as a mid-sentence fragment.
     */
    fun normalizePiece(text: String): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        t = LEADING_PUNCT_RE.replace(t, "")
        if (t.isNotEmpty() && t[0].isLetter() && t[0].isLowerCase()) {
            t = t[0].uppercaseChar() + t.substring(1)
        }
        if (t.isNotEmpty() && t.last() !in ".!?") {
            t = "$t."
        }
        return t
    }

    private val LEADING_PUNCT_RE = Regex("^[,;\\s]+")

    // ============================================================
    //   PUBLIC HELPERS (exposed for testability + for SrtSynthesizer
    //   to reuse validate-each-piece logic via the splitter)
    // ============================================================

    /**
     * Check whether [text] contains a finite verb form. Combines three
     * checks: (1) word is in [AUX_VERBS], (2) word is in
     * [IRREGULAR_VERBS], (3) word ends in a regular verb suffix
     * (-ed / -ing / -es where the stem looks like a verb).
     */
    fun hasVerb(text: String): Boolean {
        val words = WORD_RE.findAll(text.lowercase()).map { it.value }.toList()
        if (words.any { it in AUX_VERBS }) return true
        if (words.any { it in IRREGULAR_VERBS }) return true
        for (w in words) {
            if (w.length <= 3) continue
            if (w.endsWith("ed") || w.endsWith("ied")) return true
            if (w.endsWith("ing")) return true
            if (w.endsWith("es") && w.length > 4) {
                if (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") ||
                    w.endsWith("zes") || w.endsWith("oes") || w.endsWith("ses")) return true
                if (w in ES_SUFFIX_VERBS) return true
            }
        }
        return false
    }

    /**
     * Check that the first word of [text] can plausibly be a subject.
     * Rejects dangling participles ("Trying to..."), adverbs
     * ("Sufficiently..."), and bare nouns ("drones at targets...").
     */
    fun startsWithSubject(text: String): Boolean {
        val firstRaw = WORD_RE.find(text)?.value ?: return false

        // Capitalized → proper noun or sentence-initial noun.
        if (firstRaw[0].isUpperCase()) return true

        val first = firstRaw.lowercase()

        // Strip common contraction suffixes and check the stem.
        for (suffix in CONTRACTION_SUFFIXES) {
            if (first.endsWith(suffix)) {
                val stem = first.dropLast(suffix.length)
                if (stem in SUBJECT_STARTERS) return true
            }
        }

        return first in SUBJECT_STARTERS
    }

    // ============================================================
    //   PRIVATE SPLITTERS (each returns `null` for "no valid split")
    // ============================================================

    private fun splitAtSentenceBoundary(text: String): List<String>? {
        val boundaries = mutableListOf<Int>()
        for (m in SENTENCE_RE.findAll(text)) {
            val periodPos = m.range.first
            val afterPunct = m.range.last + 1
            if (text[periodPos] == '.' && isAbbreviationWord(text, periodPos)) continue
            if (text[periodPos] == '.') {
                val prev = if (periodPos > 0) text[periodPos - 1] else ' '
                val next = if (afterPunct < text.length) text[afterPunct] else ' '
                if (prev.isDigit() && next.isDigit()) continue
            }
            boundaries.add(afterPunct)
        }
        if (boundaries.isEmpty()) return null
        val pieces = mutableListOf<String>()
        var last = 0
        for (b in boundaries) {
            pieces.add(text.substring(last, b).trim())
            last = b
        }
        pieces.add(text.substring(last).trim())
        return pieces.filter { it.isNotEmpty() }
    }

    private fun isAbbreviationWord(text: String, periodPos: Int): Boolean {
        var i = periodPos - 1
        while (i >= 0 && (text[i].isLetter() || text[i] == '\'')) i--
        val word = text.substring(i + 1, periodPos).lowercase()
        return word in ABBREVIATIONS
    }

    private fun splitAtCommaBoundary(text: String): List<String>? {
        for (m in COMMA_RE.findAll(text)) {
            val left = stripTrim(text.substring(0, m.range.first))
            val right = stripTrim(text.substring(m.range.last + 1))
            if (wordCount(left) < MIN_PIECE_WORDS || wordCount(right) < MIN_PIECE_WORDS) continue
            if (!isCompleteSentence(left) || !isCompleteSentence(right)) continue
            return listOf(left, right)
        }
        return null
    }

    private fun splitAtClauseBoundary(text: String): List<String>? =
        tryClauseSplit(text, CLAUSE_RE, requireVerbBothSides = true)

    private fun splitAtFiller(text: String): List<String>? =
        tryClauseSplit(text, FILLER_RE)

    private fun splitAtConjFiller(text: String): List<String>? =
        tryClauseSplit(text, CONJ_FILLER_RE)

    /**
     * Generic helper: try each regex match independently, validate
     * both sides, return the most-balanced valid candidate.
     */
    private fun tryClauseSplit(
        text: String,
        regex: Regex,
        requireVerbBothSides: Boolean = false,
    ): List<String>? {
        data class Cand(val maxWc: Int, val gap: Int, val left: String, val right: String)

        val candidates = mutableListOf<Cand>()
        for (m in regex.findAll(text)) {
            val left = stripTrim(text.substring(0, m.range.first))
            val right = stripTrim(text.substring(m.range.last + 1))
            val wcLeft = wordCount(left)
            val wcRight = wordCount(right)
            if (wcLeft < MIN_PIECE_WORDS || wcRight < MIN_PIECE_WORDS) continue
            if (requireVerbBothSides && (!hasVerb(left) || !hasVerb(right))) continue
            if (!isCompleteSentence(left) || !isCompleteSentence(right)) continue
            candidates.add(Cand(maxOf(wcLeft, wcRight), kotlin.math.abs(wcLeft - wcRight), left, right))
        }
        if (candidates.isEmpty()) return null
        candidates.sortWith(compareBy({ it.maxWc }, { it.gap }))
        val best = candidates.first()
        return listOf(best.left, best.right)
    }

    /** Strip leading/trailing spaces + the common punctuation commas / dashes. */
    private fun stripTrim(s: String): String =
        s.trim().trimEnd(',', ';', ':', '—', '–', ' ').trimStart(',', ';', ':', '—', '–', ' ')

    // ============================================================
    //   RECURSIVE CORE
    // ============================================================

    private fun findBestSplitRecursive(
        text: String,
        targetWords: Int,
        depth: Int,
        maxDepth: Int,
    ): List<String> {
        if (wordCount(text) <= targetWords) return listOf(text)
        if (depth >= maxDepth) return listOf(text) // give up — leave as-is

        // Pass 1: sentence boundary
        // No `wordCount <= target + N` cap here (Python has `+ 4`). With
        // TARGET_WORDS=14 the cap would be 18 and reject legitimate
        // 19-21w sentences whose only clean boundary IS the sentence period.
        // The leftover > target piece is re-evaluated by [maybeRecurse];
        // if no cleaner split exists downstream, the over-target piece
        // is accepted as-is (better than leaving the whole sentence intact).
        splitAtSentenceBoundary(text)?.let { pieces ->
            if (pieces.isNotEmpty() && pieces.all { isCompleteSentence(it) }) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // Pass 1.5: comma boundary
        splitAtCommaBoundary(text)?.let { pieces ->
            if (pieces.size >= 2) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // Pass 2: clause boundary
        splitAtClauseBoundary(text)?.let { pieces ->
            if (pieces.size >= 2 && pieces.all { isCompleteSentence(it) }) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // Pass 4: relative clause
        tryClauseSplit(text, REL_RE)?.let { pieces ->
            if (pieces.size >= 2 && pieces.all { isCompleteSentence(it) }) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // Pass 5: filler
        splitAtFiller(text)?.let { pieces ->
            if (pieces.size >= 2 && pieces.all { isCompleteSentence(it) }) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // Pass 5b: conjunction + filler
        splitAtConjFiller(text)?.let { pieces ->
            if (pieces.size >= 2 && pieces.all { isCompleteSentence(it) }) {
                return maybeRecurse(pieces, targetWords, depth, maxDepth)
            }
        }

        // No clean split — keep original
        return listOf(text)
    }

    private fun maybeRecurse(
        pieces: List<String>,
        targetWords: Int,
        depth: Int,
        maxDepth: Int,
    ): List<String> {
        val result = mutableListOf<String>()
        for (p in pieces) {
            if (wordCount(p) > targetWords) {
                result.addAll(findBestSplitRecursive(p, targetWords, depth + 1, maxDepth))
            } else {
                result.add(p)
            }
        }
        return result
    }
}
