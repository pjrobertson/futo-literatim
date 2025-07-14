package org.futo.inputmethod.latin
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.keyboard.KeyDetector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

import java.io.File
import kotlin.math.pow

/**
 * TroiSqliteIME - Predictive Text Engine for Welsh Language Input (Singleton)
 * 
 * Converted from Python to Kotlin for Android integration.
 * 
 * This singleton provides n-gram based text prediction using a SQLite database containing
 * word frequencies and scores. It integrates with WelshSpellings for generating
 * spelling variants and corrections.
 * 
 * Key features:
 * - Context-aware word prediction using n-gram models
 * - Welsh spelling correction and variant generation
 * - Fuzzy matching with wildcard patterns
 * - Score-based ranking of predictions
 * - Singleton pattern for efficient database reuse
 * 
 * Database setup:
 * - Place your SQLite database file in assets/literatim.sqlite
 * - The database will be copied to files directory on first use
 * 
 * Dependencies added to build.gradle:
 * - androidx.sqlite:sqlite-ktx:2.4.0 
 * - androidx.sqlite:sqlite-framework:2.4.0
 * 
 * Usage:
 * ```kotlin
 * // Initialize once (e.g., in LatinIME.onCreate())
 * TroiSqliteIME.initialize(context)
 * 
 * // Use anywhere in your app
 * val predictions = TroiSqliteIME.predict(listOf("hello", "wor"), maxRows = 5)
 * 
 * // Cleanup when done (e.g., in LatinIME.onDestroy())
 * TroiSqliteIME.cleanup()
 * ```
 */

data class ComposeInfo(
    val partialWord: String,
    val xCoords: IntArray,
    val yCoords: IntArray,
    val inputMode: Int
)

/**
 * Builds wildcard patterns for fuzzy matching by inserting '?' at various positions
 */
private fun buildWildcards(s: String, first_x_chars: Int = -1): List<String> {
    val blanks = mutableListOf<String>()
    val clippedString = if (first_x_chars > 0 && first_x_chars <= s.length) {
        s.substring(0, first_x_chars)
    } else {
        s
    }
    // Insert '?' at each position except the first and last character
    for (i in 1 until clippedString.length - 1) {
        val wildcard = clippedString.substring(0, i) + "?" + clippedString.substring(i)
        blanks.add(wildcard)
    }
    return blanks
}

/**
 * Data class representing a word prediction with its score
 */
data class WordPrediction(
    val wordform: String,
    val score: Int
)

private const val FULL_WORD_MULTIPLIER = 2
private const val CONTEXT_LENGTH_MULTIPLIER = 10
private const val DATABASE_FILE_NAME = "literatim.sqlite"
private const val ASSET_FILE = "sqlite/$DATABASE_FILE_NAME"
private const val VERSION_FILE = "sqlite/version.txt"

private const val MAX_SCORE = 4194304 // 2**22 -> but kotlin doesn't like that
// Equivalent to ngram.py -> PHRASE_SEPARATOR. Used to split context into phrases
private val PHRASE_SEPARATOR = Regex("(?:-+(?!\\w)|(?<!\\w)-+|[^-\\w'’\\s]|\\S*[0-9]+\\S*)+")


/**
 * Predictive text engine using SQLite n-gram database (Singleton)
 */
object TroiSqliteIME {

    private val APOSTROPHE_REPLACEMENT = Regex("""(?!:[aeiouy])(i|u|n|m|r|w|ch)$""")

    private var db: SupportSQLiteDatabase? = null
    private val predictions = mutableListOf<WordPrediction>()
    private var isInitialized = false
    private var zeroContextResults: List<WordPrediction>? = null
    /**
     * Initialize the singleton with application context
     * Call this once during app startup
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            // Copy database from assets if it doesn't exist
            initializeDatabase(context)
            isInitialized = true
        }
    }

    private fun copyFile(context: Context, dbFile: File) {
        context.assets.open(ASSET_FILE).use { inputStream ->
            dbFile.outputStream().use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun loadDatabase(context: Context, dbFile: File, version: Int) {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Database should already exist from assets
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        throw IllegalStateException("Database version mismatch")
                    }
                })
                .build()
        )

        // Open as read-only since we only need to query
        db = helper.readableDatabase
    }
    private fun initializeDatabase(context: Context) {
        val dbFile = File(context.filesDir, DATABASE_FILE_NAME)

        val assetsVersion = context.assets.open(VERSION_FILE).bufferedReader().readText().toInt()

        if (!dbFile.exists()) {
            // Copy the .sqlite file directly from assets to the files directory if first run
            copyFile(context, dbFile)
            loadDatabase(context, dbFile, assetsVersion)
            return
        } 


        // get the current database version from the app assets folder version.txt

        try {
            // DB check the current filedDir database version
            loadDatabase(context, dbFile, assetsVersion)
        } catch (e: IllegalStateException) {
            db?.close()
            dbFile.delete()
            copyFile(context, dbFile)
            loadDatabase(context, dbFile, assetsVersion)
        }
        
    }

    /**
    * Calculates the number of matched characters between two words
    * Characters are matched based on their frequency in each word
    * 
    * @param word1 First word to compare
    * @param word2 Second word to compare
    * @return Number of matched characters
    */
    fun countMatchedCharacters(word1: String, word2: String): Int {
        if (word1.length < word2.length) {
            // we want to check the longer word for presence of letters in the shorter word
            return countMatchedCharacters(word2, word1)
        }
        // Convert to lowercase for case-insensitive comparison
        val w1 = word1.lowercase()
        var w2 = word2.lowercase()

        // Iterate through w1 and count how many characters from w2 are present, removing that character from w2 then continuing
        var matchedCount = 0
        for (c in w1) {
            val index = w2.indexOf(c)
            if (index >= 0) {
                matchedCount++
                // Remove the character from w2 to prevent double counting
                w2 = w2.removeRange(index, index + 1)
            }
        }
        return matchedCount
    }

    private fun getContext(composeInfo: ComposeInfo, ngramContext: NgramContext): String {
        // Copied from LanguageModel.kt -> getContext()
        var context = ngramContext.extractPrevWordsContext()
            .replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim { it <= ' ' }
        if (ngramContext.fullContext.isNotEmpty()) {
            context = ngramContext.fullContext
            context = context.substring(context.lastIndexOf("\n") + 1).trim { it <= ' ' }
        }

        var partialWord = composeInfo.partialWord
        if (partialWord.isNotEmpty() && context.endsWith(partialWord)) {
            context = context.substring(0, context.length - partialWord.length).trim { it <= ' ' }
        }

        return context
    }
    private fun getComposeInfo(composedData: ComposedData, keyDetector: KeyDetector): ComposeInfo {
        // copied from LanguageModel.kt -> getComposeInfo()
        var partialWord = composedData.mTypedWord

        val inputPointers = composedData.mInputPointers
        val isGesture = composedData.mIsBatchMode

        var inputMode = 0
        if (isGesture) {
            partialWord = ""
        }

        val xCoords: IntArray = inputPointers.xCoordinates.toList().toIntArray()
        val yCoords: IntArray = inputPointers.yCoordinates.toList().toIntArray()

        return ComposeInfo(
            partialWord = partialWord,
            xCoords = xCoords,
            yCoords = yCoords,
            inputMode = inputMode
        )
    }


    suspend fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, keyDetector: KeyDetector): List<WordPrediction> {
        if (!isInitialized) {
            throw IllegalStateException("TroiSqliteIME not initialized. Call initialize() first.")
        }

        // the following closely follows LanguageModel.kt -> getSuggestions() to get the context, then split it on ' ' to pass to predict()
        val composeInfo = withContext(Dispatchers.Main) {
            getComposeInfo(composedData, keyDetector)
        }

        // Disable gesture for now (same as LanguageModel.kt implementation)
        // swipe gestures support is currently built into BinaryDictionary at low level cpp code
        if(composedData.mIsBatchMode) {
            return emptyList()
        }

        val context = getContext(composeInfo, ngramContext)
        // context can be very long (includes everything written, only consider new sentences / phrases
        // take LOWERCASE - for better searching/matching
        // REMOVE any empty strings from the split (cases where user typed multiple spaces)
        var ngram = context.split(PHRASE_SEPARATOR).last().lowercase().split(" ").filter { it.isNotEmpty() }.toTypedArray()

        // optimisation for case where ngram is empty and word being typed is also empty -> this is a slow query in sql so we cache the result
        if (ngram.isEmpty() && composeInfo.partialWord.isEmpty()) {
            // if we have already cached the zero context results, return them
            if (zeroContextResults != null) {
                return zeroContextResults!!
            }
            // otherwise, we need to query the database for zero context results
            val results = predict(arrayOf(""), maxRows = 4)
            zeroContextResults = results
            return results
        }

        // add composeInfo.partialWord -> even if it's empty, that way we get ["fy", "enw", ""] signifying new word
        ngram += composeInfo.partialWord.lowercase()

        // take up to 8 predictions, then we will rescore them based on 'distance
        return predict(ngram, maxRows = 8)

    }

    /**
     * Predicts the next words based on the given n-gram context
     * 
     * @param ngram List of words representing the context
     * @param maxRows Maximum number of predictions to return
     * @return List of word predictions sorted by score
     */
    private fun predict(ngram: Array<String>, maxRows: Int = 5): List<WordPrediction> {
        
        val currentDb = db ?: return emptyList()
        
        val nextWordScores = mutableMapOf<String, Int>()

        // nextword either empty (start of a word) or the last word in ngram
        val nextword = ngram.lastOrNull() ?: ""
        val spellings = mutableListOf<String>()
        
        if (nextword.isNotEmpty()) {
            // user has started typing a word, not empty
            // Call to WelshSpellings.generateSpellings() as requested
            val spellingCorrections = WelshSpellings.generateSpellings(nextword)
            spellings.addAll(spellingCorrections)
            // add the original nextword to spellings as well
            spellings.add(nextword)
            for (spelling in spellingCorrections) {
                // add wildcard patterns for fuzzy matching
                val wildcards = buildWildcards(spelling, first_x_chars = 7)
                spellings.addAll(wildcards)
            }
        }

        var context = if (ngram.size >= 5) {
            ngram.takeLast(5).dropLast(1)
        } else {
            ngram.dropLast(1)
        }

        val apostropheReplacement = APOSTROPHE_REPLACEMENT.replace(nextword, "’$1")

        while (true) {
            val limit = maxRows - nextWordScores.size
            var sql = """
                SELECT wordform, 
                       MAX(context_length) as context_length,
                       MAX(final_score) * exact_match_priority as final_score,
                       lookup_wordform
                FROM (
                    -- Direct ngram predictions
                    SELECT wordform, 
                           ? as context_length,
                           score as final_score,
                           case when ? and (wordform=? or wordform=?) then 100 
                           else 1
                           end as exact_match_priority,
                           -- this is set to '0' since we're not going to give penalties on wordform_length for ngram cases 
                           -- (see cross_wordforms sub select for how we use it there)
                           wordform as lookup_wordform
                    FROM ngrams 
                    WHERE context=?""".trimIndent()
            val args = mutableListOf<Any>(context.size, nextword.isNotEmpty(), nextword.drop(1), apostropheReplacement.drop(1), context.joinToString(" ").trim())

            if (nextword.isNotEmpty()) {
                sql += " AND (false"
                for (spelling in spellings) {
                    sql += " OR wordform GLOB ?||'*'"
                    args.add(spelling)
                }
                if (apostropheReplacement != nextword) {
                    sql += " OR wordform GLOB ?||'*'"
                    args.add(apostropheReplacement)
                }
                sql += ")"
            }

            // Add cross-wordform predictions if nextword is long enough (>=3 chars, or >=2 chars when there's context)
            if (nextword.isNotEmpty() && (nextword.length >= 3 || (context.isNotEmpty() && nextword.length >= 2))) {
                sql += """
                    
                    UNION ALL
                     -- Cross-wordform predictions
                    SELECT n.wordform,
                           ? as context_length,
                           (cast(n.score as real) * cw.score) as final_score,
                           case when ? and lower(cw.cross_wordform)=? then 100 else 1 end as exact_match_priority,
                           cw.cross_wordform as lookup_wordform
                    FROM cross_wordforms cw 
                    INNER JOIN ngrams n ON cw.wordform=n.wordform 
                    WHERE n.context=?
                    AND cw.cross_wordform glob ?||'*' """.trimIndent()
                
                args.addAll(listOf(context.size, nextword.isNotEmpty(), nextword, context.joinToString(" ").trim(), nextword))
            }
            
            sql += """
                            ) combined_results
                            GROUP BY wordform
                            ORDER BY context_length DESC, 
                                     MAX(exact_match_priority) DESC, 
                                     final_score DESC,
                                     wordform ASC
                            LIMIT ?""".trimIndent()
            
            args.add(limit)


            val cursor = currentDb.query(sql, args.toTypedArray())
            cursor.use {
                while (it.moveToNext()) {
                    val wordform = it.getString(0)
                    // don't need to get this from the cursor, since we already know it
                    // val contextLength = it.getInt(1)
                    var score = it.getInt(2)
                    val lookupWordform = it.getString(3)

                    // do the bit-shifting *before* rescoring based on length/chars
                    var combinedScore = ((context.size shl 25) or score).toFloat()

                    // Apply length penalty *only* for cross-wordform predictions (when lookupWordform is not empty)
                    if (nextword.isNotEmpty()) {
                        // find total matched characters between lookupWordform and nextword
                        val matchedCharCount = countMatchedCharacters(lookupWordform.drop(1), nextword.drop(1))
                        if (matchedCharCount > 0) {
                            // Apply 0.5^Number of non-matched characters penalty
                            val penalty = 0.5.pow(lookupWordform.length - matchedCharCount).toFloat()
                            combinedScore *= penalty
                        }
                        // length difference penalty, smaller than character difference penalty
                        combinedScore *= 0.8.pow((lookupWordform.length - nextword.length)).toFloat()
                    }
                    score = combinedScore.toInt()
                    val currentScore = nextWordScores[wordform]

                    nextWordScores[wordform] = (if (currentScore == null) {
                        score
                    } else {
                        maxOf(currentScore, score)
                    })
                }
            }

            // If we have enough predictions, break
            if (nextWordScores.size >= maxRows) break
            // If we have no more context, break
            if (context.isEmpty()) break
            // Remove the last word from context to continue searching
            context = context.drop(1)
        }

        // NOTE: sorting not required here, that happens later on once troi vals and BinaryDict vals are merged
        return nextWordScores.entries
            // multiply by FULL_WORD_MULTIPLIER = 2 for exact match -> see FULL_WORD_MULTIPLIER constant in autocorrection_threshold_utils.cpp
            .map { WordPrediction(it.key, it.value ) }
    }

    /**
     * Clean up resources when app is closing
     * Call this in your Application's onTerminate() or similar
     */
    fun cleanup() {
        db?.close()
        db = null
        isInitialized = false
    }
}