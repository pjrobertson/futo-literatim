package org.futo.inputmethod.latin

import java.util.regex.Pattern

/**
 * Welsh digraph splitting functionality for FUTO keyboard
 * Converted from Python implementation for Welsh language processing
 * https://github.com/menzy314/lecsicon-cymraeg-bangor-enghreifftiau/tree/deugraffau 
 * 
 * Converted using Claude 4, notes:
 * 
 * Key Conversion Changes:
 * Data Structures:
 *    > Python lists → Kotlin setOf() for constant lookups (more efficient)
 *    > Python strings with .split() → Kotlin sets with explicit elements
 *
 * Regex Handling:
 *    > Python re.findall() → Kotlin Pattern.compile() with Matcher
 *    > Pre-compiled patterns for better performance
 *    > Added helper functions for regex matching
 * 
 * Function Structure:
 *    > Python function → Kotlin object with static-like methods
 *    > Added proper type annotations and null safety
 *    > Default parameter handling: lemma: String? = null
 *
 * Language Features:
 *    > Python f-strings → Kotlin string templates (not needed here)
 *    > Added comprehensive KDoc documentation
 */
object Digraphs {
    
    // POSSIBLE TYPOS IN THE LEXICON
    // annrhigiadwy => anhrigiadwy
    // cyfarhos => cyfaros?
    // LIanrhystud => Llanrhystud
    // lianrhystud => Llanrhystud
    // sbringar => sbring-gar
    
    private val DONT_SPLIT_RH = setOf(
        "Caerhos", "Cilrhedyn", "Cwmyrhiwdre", "Nantyrhynnau", "Porthyrhyd", 
        "Trerhedyn", "Trerhingyll", "Troedyrhiw", "arhythmia", "arhythmig", 
        "coleorhisa", "ewrhythmeg", "gonorrhoea", "isorhythmig", "mycorhisa", 
        "pyorhea", "yrhawg"
    )
    
    // TODO: may need to include some of the following:
    // Angefin Bodringallt Brengain Ingli Langro Llandingad Llanddingad Pinged Tangwen Tangwyn *ffaryngeal
    private val SPLIT_NG = setOf(
        "Abergwyngregyn", "Angliad", "Anglican", "Anglicanaidd", "Angola", "Bangladesh", 
        "Bangor", "Bengal", "Blaengarw", "Blaengwrach", "Blaengwynfi", "Brongest", 
        "Bronglais", "Bryngarn", "Bryngwran", "Bryngwyn", "Carngowil", "Carnguwch", 
        "Carngwcw", "Cefngorwydd", "Cilmaengwyn", "Congo", "Cryngae", "Felinganol", 
        "Ffynnongroyw", "Garthbrengi", "Glangors", "Grongaer", "Hengastell", "Hengoed", 
        "Hengwm", "Hengwrt", "Hwngaraidd", "Hwngareg", "Hwngari", "Lingoed", 
        "Llanengan", "Llanfairpwllgwyngyll", "Llwyngroes", "Llwyngwair", "Llwyngwern", 
        "Maengwyn", "Melingriffith", "Mongolia", "Myngul", "Pengelli", "Penglais", 
        "Pengorffwysfa", "Pengrynwr", "Pengwern", "Penybenglog", "Singrug", 
        "Tafarngelyn", "Tanganyika", "Tongwynlais", "Tringarth", "Ynysymaengwyn", 
        "amcangyfrifyn", "arlwyngig", "arweingi", "bangaw", "bangorwaith", "bechingalw", 
        "bingo", "brongoch", "browngoch", "bryngaer", "cangarŵ", "conga", "congren", 
        "cringoch", "cwango", "cwangoaidd", "dychangerdd", "engram", "genglo", 
        "glingam", "gwerngoedwig", "gwyngoch", "gylfingroes", "hunglwyf", "hwiangerdd", 
        "ingot", "jingo", "jingoistiaeth", "jwngl", "jyngl", "lingri", "llieingant", 
        "llengig", "llinengrafiad", "llinganol", "llinglwm", "llongyfarch", 
        "llongyfarchiad", "llwyngwril", "llyfngrwn", "manganîs", "manglo", "mango", 
        "mangrof", "melyngoch", "mingam", "mingamu", "mwnglawdd", "mwngrel", 
        "plaengan", "prynhawngwaith", "rhangor", "rhangymeriad", "rhieingerdd", 
        "safnglo", "safngloi", "sbangl", "swyngan", "torlengig", "torllengig", 
        "tudalengipio", "yngymaint", "ysgafngalon"
    )
    
    // Regex patterns compiled once for efficiency
    private val splitNgPattern = Pattern.compile(
        "\\b(angio|bwngler|byngalo|dyngar|dyngas|gwangalon|mening|tang(?!iad)|(Llan|blaen|bon|bron|brown|bryn|calon|cefn|gwahan|gwyn|hunan|llun|mein|mewn|mwyn|pan|pen|sein|swyn|teyrn|un|union)g)|n(groen|gar|garwch|gyfrif)\\b",
        Pattern.CASE_INSENSITIVE
    )
    
    private val digraphPatternWithNgSplit = Pattern.compile(
        "ch|dd|ff|\\bng|ng\\b|ll|ph|\\brh|(?<=[dlmnt])rh|th|.",
        Pattern.CASE_INSENSITIVE
    )
    
    private val digraphPatternNormal = Pattern.compile(
        "ch|dd|ff|ng|ll|ph|\\brh|(?<=[dlmnt])rh|th|.",
        Pattern.CASE_INSENSITIVE
    )

    private val dontSplitRhPattern = Pattern.compile(
        "ch|dd|ff|ng|ll|ph|rh|th|.",
        Pattern.CASE_INSENSITIVE
    )
    
    /**
     * Splits a Welsh word into its constituent digraphs and single characters
     * following Welsh orthographic rules.
     * 
     * @param word The word to split
     * @param lemma The lemma form of the word (defaults to word if not provided)
     * @return List of strings representing the split components
     */
    fun splitWord(word: String, lemma: String? = null): List<String> {
        val actualLemma = lemma ?: word
        
        // DO NOT split rh (nor ng) if the lemma is one of the DONT_SPLIT_RH lemmas listed above.
        if (actualLemma in DONT_SPLIT_RH) {
            return findAllMatches(dontSplitRhPattern, word)
        }
        
        // Otherwise, DO split rh, unless it's at the start of a word, or after d/l/m/n/t
        //
        // DO split ng if the lemma is one of the SPLIT_NG lemmas listed above.
        // Otherwise, DO NOT split ng, unless the lemma starts with one of these:
        //
        //   angio bwngler byngalo dyngar dyngas gwangalon mening tang (except if it starts with tangiad)
        //
        // or one of these then a 'g':
        //
        //   Llan blaen bon bron brown bryn calon cefn gwahan gwyn hunan llun mein mewn mwyn pan pen sein swyn teyrn un union
        //
        // or ends with 'n' then one of these:
        //
        //   groen gar garwch gyfrif
        //
        // But even when splitting ng, don't split if it's at a word boundary
        return if (actualLemma in SPLIT_NG || splitNgPattern.matcher(actualLemma).find()) {
            findAllMatches(digraphPatternWithNgSplit, word)
        } else {
            findAllMatches(digraphPatternNormal, word)
        }
    }
    
    /**
     * Helper function to find all regex matches in a string
     */
    private fun findAllMatches(pattern: Pattern, text: String): List<String> {
        val matcher = pattern.matcher(text)
        val results = mutableListOf<String>()
        while (matcher.find()) {
            results.add(matcher.group())
        }
        return results
    }

}