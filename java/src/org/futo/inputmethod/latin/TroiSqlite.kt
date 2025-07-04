package org.futo.inputmethod.latin
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.keyboard.KeyDetector

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

import java.io.File

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
private fun buildWildcards(s: String): List<String> {
    val blanks = mutableListOf<String>()
    for (i in 1 until s.length - 1) {
        blanks.add(s.substring(0, i) + "?" + s.substring(i))
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
// Equivalent to ngram.py -> PHRASE_SEPARATOR. Used to split context into phrases
private val PHRASE_SEPARATOR = Regex("(?:-+(?!\\w)|(?<!\\w)-+|[^-\\w'’\\s]|\\S*[0-9]+\\S*)+")


/**
 * Predictive text engine using SQLite n-gram database (Singleton)
 */
object TroiSqliteIME {
    private var db: SupportSQLiteDatabase? = null
    private val predictions = mutableListOf<WordPrediction>()
    private var isInitialized = false

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

    private fun initializeDatabase(context: Context) {
        val dbFile = File(context.filesDir, "$DATABASE_FILE_NAME")
        

        // check the database file exists, if not copy it from literatim.zip in assets folder to the files directory
        if (!dbFile.exists()) {
            // Copy the .sqlite file directly from assets to the files directory
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
        
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Database should already exist from assets
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // Handle upgrades if needed
                    }
                })
                .build()
        )
        
        // Open as read-only since we only need to query
        db = helper.readableDatabase
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


    fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, keyDetector: KeyDetector): List<WordPrediction> {
        if (!isInitialized) {
            throw IllegalStateException("TroiSqliteIME not initialized. Call initialize() first.")
        }

        // the following closely follows LanguageModel.kt -> getSuggestions() to get the context, then split it on ' ' to pass to predict()
        val composeInfo = getComposeInfo(composedData, keyDetector)
        val context = getContext(composeInfo, ngramContext)
        // context can be very long (includes everything written, only consider new sentences /
        var ngram = context.split(PHRASE_SEPARATOR).last().split(" ").toTypedArray()
        // add composeInfo.partialWord -> even if it's empty, that way we get ["fy", "enw", ""] signifying new word
        ngram += composeInfo.partialWord

        // only really ever need 3 predictions
        return predict(ngram, maxRows = 5)

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
        
        val nextWordScores = mutableMapOf<String, Pair<Int, Int>>()

        // nextword either empty (start of a word) or the last word in ngram
        val nextword = ngram.lastOrNull() ?: ""
        val spellings = mutableListOf<String>()
        
        if (nextword.isNotEmpty()) {
            // user has started typing a word, not empty
            // Call to WelshSpellings.generateSpellings() as requested
            val spellingCorrections = WelshSpellings.generateSpellings(nextword, nextword).toMutableSet()
            spellingCorrections.add(Pair(nextword, nextword))
            
            spellings.addAll(spellingCorrections.map { it.first })
        }

        var context = if (ngram.size >= 5) {
            ngram.takeLast(5).dropLast(1)
        } else {
            ngram.dropLast(1)
        }

        while (true) {
            val limit = maxRows - nextWordScores.size
            var sql = "SELECT wordform, score FROM ngrams WHERE context=?"
            val args = mutableListOf<Any>()
            args.add(context.joinToString(" "))

            if (nextword.isNotEmpty()) {
                sql += " AND (false"
                for (spelling in spellings) {
                    sql += " OR wordform GLOB ?||'*'"
                    args.add(spelling)
                    if (spelling.length >= 3) {
                        for (wildcard in buildWildcards(spelling)) {
                            sql += " OR wordform GLOB ?||'*'"
                            args.add(wildcard)
                        }
                    }
                }
                sql += ")"
                sql += " ORDER BY CASE WHEN wordform=? THEN 0 ELSE 1 END, score DESC"
                args.add(nextword)
            } else {
                sql += " ORDER BY score DESC"
            }
            sql += " LIMIT ?"
            args.add(limit)

            val cursor = currentDb.query(sql, args.toTypedArray())
            cursor.use {
                while (it.moveToNext()) {
                    val wordform = it.getString(0)
                    val score = it.getInt(1)
                    val contextLength = -context.size
                    val currentValue = Pair(contextLength, score * -contextLength * CONTEXT_LENGTH_MULTIPLIER)
                    nextWordScores[wordform] = minOf(
                        currentValue,
                        nextWordScores[wordform] ?: currentValue
                    ) { a, b ->
                        when {
                            a.first != b.first -> a.first.compareTo(b.first)
                            else -> a.second.compareTo(b.second)
                        }
                    }
                }
            }

            // Second query for cross_wordforms - only if nextword has at least 2 chars
            if (nextword.isNotEmpty() && nextword.length >= 2) {
                sql = """SELECT n.wordform, cw.score + n.score as cmb_score 
                        FROM cross_wordforms cw 
                        INNER JOIN ngrams n ON cw.wordform=n.wordform 
                        WHERE context=?"""
                args.clear()
                args.add(context.joinToString(" "))
                
                sql += " AND (false"
                sql += " OR cw.cross_wordform GLOB ?||'*'"
                args.add(nextword)
                
                for (wildcard in buildWildcards(nextword)) {
                    sql += " OR cross_wordform GLOB ?||'*'"
                    args.add(wildcard)
                }
                sql += ")"
                sql += " ORDER BY CASE WHEN cross_wordform=? THEN 0 ELSE 1 END, cmb_score DESC"
                args.add(nextword)
                sql += " LIMIT ?"
                args.add(limit)

                val cursor2 = currentDb.query(sql, args.toTypedArray())
                cursor2.use {
                    while (it.moveToNext()) {
                        val wordform = it.getString(0)
                        val score = it.getInt(1)
                        val contextLength = -context.size
                        // Change from IME - here we *multiply* by the context Length since we want to account for longer sentence formations having higher matches
                        val currentValue = Pair(contextLength, score * -contextLength * CONTEXT_LENGTH_MULTIPLIER)
                        nextWordScores[wordform] = maxOf(
                            currentValue,
                            nextWordScores[wordform] ?: currentValue
                        ) { a, b ->
                            when {
                                a.first != b.first -> a.first.compareTo(b.first)
                                else -> a.second.compareTo(b.second)
                            }
                        }
                    }
                }
            }

            if (nextWordScores.size >= maxRows) {
                break
            }
            if (context.isEmpty()) {
                break
            }
            context = context.drop(1)
        }

        // Sort by <exact match>, score
        return nextWordScores.entries
            .sortedWith(compareBy<Map.Entry<String, Pair<Int, Int>>> 
                { it.value.first }.thenByDescending { it.value.second }.thenBy { it.key })
            .take(maxRows)
            // multiply by FULL_WORD_MULTIPLIER = 2 for exact match -> see FULL_WORD_MULTIPLIER constant in autocorrection_threshold_utils.cpp
            .map { WordPrediction(it.key, it.value.second ) }
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