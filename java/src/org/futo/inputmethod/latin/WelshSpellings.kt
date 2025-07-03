package org.futo.inputmethod.latin

import java.util.regex.Pattern

/**
 * Welsh spelling correction and variant generation for FUTO keyboard
 * Converted from Python implementation for Welsh language processing
 */
object WelshSpellings {
    
    private val UNACCENTED = mapOf(
        'â' to 'a', 'ê' to 'e', 'î' to 'i', 'ô' to 'o', 'û' to 'u', 'ŵ' to 'w', 'ŷ' to 'y',
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ẃ' to 'w', 'ý' to 'y',
        'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u', 'ẁ' to 'w', 'ỳ' to 'y',
        'ä' to 'a', 'ë' to 'e', 'ï' to 'i', 'ö' to 'o', 'ü' to 'u', 'ẅ' to 'w', 'ÿ' to 'y',
        'Â' to 'A', 'Ê' to 'E', 'Î' to 'I', 'Ô' to 'O', 'Û' to 'U', 'Ŵ' to 'W', 'Ŷ' to 'Y',
        'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U', 'Ẃ' to 'W', 'Ý' to 'Y',
        'À' to 'A', 'È' to 'E', 'Ì' to 'I', 'Ò' to 'O', 'Ù' to 'U', 'Ẁ' to 'W', 'Ỳ' to 'Y',
        'Ä' to 'A', 'Ë' to 'E', 'Ï' to 'I', 'Ö' to 'O', 'Ü' to 'U', 'Ẅ' to 'W', 'Ÿ' to 'Y',
        'ç' to 'c', 'Ç' to 'C', 'ñ' to 'n', 'Ñ' to 'N', 'ś' to 's', 'Ś' to 'S', 'ć' to 'c', 'Ć' to 'C'
    )
    
    private val COMMON_MISTAKES = listOf(
        // pattern, replacements
        Pair("^t(?!h)", listOf("d", "nh")),
        Pair("^d(?!d)", listOf("dd", "t")),
        Pair("^dd", listOf("d")),
        Pair("^c(?!h)", listOf("g", "ngh")),
        Pair("^g", listOf("", "c", "ng")),
        Pair("^p(?!h)", listOf("b", "mh")),
        Pair("^b", listOf("f", "p", "m")),
        Pair("^f", listOf("b", "m")),
        Pair("^m(?!h)", listOf("f", "mh")),
        Pair("^ng(?!h)", listOf("ngh")),
        Pair("^(?=[aeiouwy])", listOf("h")),
        Pair("ae", listOf("ai", "au", "ay")),
        Pair("ai", listOf("ae", "au", "ay")),
        Pair("au", listOf("ae", "ai", "ay")),
        Pair("ch", listOf("c")),
        Pair("dd", listOf("th")),
        Pair("ei", listOf("eu", "ey")),
        Pair("eu", listOf("ei", "ey")),
        Pair("ey", listOf("ei", "eu")),
        Pair("(?<!f)f(?!f)", listOf("v", "ff")),
        Pair("ff", listOf("f")),
        Pair("i(?![aeiouwy])", listOf("u", "y")),
        Pair("ll", listOf("l")),
        Pair("nn", listOf("n", "nh")),
        Pair("oe", listOf("oi", "oy")),
        Pair("oi", listOf("oe", "oy")),
        Pair("oy", listOf("oe", "oi")),
        Pair("ph", listOf("ff")),
        Pair("rr", listOf("r", "rh")),
        Pair("rh", listOf("r", "rh")),
        Pair("u", listOf("i", "y")),
        Pair("w(?![aeiouwy])", listOf("u", "oo", "y")),
        Pair("(?<![aeiouwy])y", listOf("u", "w")),
        Pair("yn", listOf("in")),
        Pair("wy", listOf("wi", "oi")),
        Pair("y", listOf("i", "u", "w"))
    )
    
    /**
     * Remove accents from a letter sequence
     * Only works on normal precomposed characters, not combining accents
     */
    fun removeAccents(letterSequence: String): String {
        return letterSequence.map { char ->
            UNACCENTED[char] ?: char
        }.joinToString("")
    }
    
    /**
     * Generate spelling variants for a Welsh word, returning a new set
     * @param wordform The word form to generate variants for
     * @param lemma The lemma form of the word
     * @return Set of spelling variants (mistake -> correct)
     */
    fun generateSpellings(wordform: String, lemma: String): Set<Pair<String, String>> {
        val spellings = mutableSetOf<Pair<String, String>>()
                val normalizedWordform = removeAccents(wordform).lowercase()
        
        // Generate common variants
        for ((pattern, replacements) in COMMON_MISTAKES) {
            val regex = Pattern.compile(pattern)
            for (replacement in replacements) {
                val matcher = regex.matcher(normalizedWordform)
                if (matcher.find()) {
                    val mistake = matcher.replaceFirst(replacement)
                    if (mistake != normalizedWordform) {
                        spellings.add(Pair(mistake, normalizedWordform))
                    }
                }
            }
        }
        
        // Generate deletions
        val letters = Digraphs.splitWord(normalizedWordform, lemma)
        for (i in 1 until letters.size - 1) {
            val deletion = letters.subList(0, i) + letters.subList(i + 1, letters.size)
            val deletionString = deletion.joinToString("")
            spellings.add(Pair(deletionString, normalizedWordform))
        }
        return spellings
    }
}

