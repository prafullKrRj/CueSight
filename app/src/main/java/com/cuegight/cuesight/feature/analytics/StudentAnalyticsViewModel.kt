package com.cuegight.cuesight.feature.analytics

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.model.Student
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import com.cuegight.cuesight.feature.practice.domain.engine.ErpfEngine
import com.cuegight.cuesight.feature.practice.domain.model.CwaResult
import com.cuegight.cuesight.feature.practice.domain.model.ErpiResult
import com.cuegight.cuesight.feature.practice.domain.model.MasteryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * ViewModel for Student Analytics Dashboard
 * Computes comprehensive analytics for a specific student including:
 * - ERPI (Emotion Recognition Progress Index)
 * - Per-Emotion Mastery scores
 * - Confusion Matrix
 * - Response Time Analysis
 * - CWA (Confusion-Weighted Accuracy)
 */
class StudentAnalyticsViewModel(
    private val studentId: Long,
    private val studentRepository: StudentRepository,
    private val practiceRepository: PracticeRepository,
    private val erpfEngine: ErpfEngine
) : ViewModel() {

    private val _state = MutableStateFlow(StudentAnalyticsState())
    val state: StateFlow<StudentAnalyticsState> = _state.asStateFlow()

    init {
        loadAnalytics()
    }

    fun refresh() {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Load student info
                val student = withContext(Dispatchers.IO) {
                    studentRepository.getAllStudents().value.find { it.id == studentId }
                }

                if (student == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Student not found"
                    )
                    return@launch
                }

                // Load practice data
                val sessions = withContext(Dispatchers.IO) {
                    practiceRepository.getSessionsByStudent(studentId)
                }

                val guesses = withContext(Dispatchers.IO) {
                    practiceRepository.getGuessesByStudent(studentId)
                }

                // Compute analytics
                val erpiResult = computeErpi(sessions)
                val masteryResult = computeMastery(guesses)
                val cwaResult = computeCwa(guesses)
                val confusionMatrix = computeConfusionMatrix(guesses)
                val mostConfusedPairs = findMostConfusedPairs(confusionMatrix)
                val responseTimeStats = computeResponseTimeStats(guesses)
                val sessionHistory = buildSessionHistory(sessions, guesses)
                
                val overallAccuracy = if (guesses.isNotEmpty()) {
                    guesses.count { it.isCorrect }.toFloat() / guesses.size
                } else 0f

                _state.value = _state.value.copy(
                    isLoading = false,
                    student = student,
                    sessionCount = sessions.size,
                    totalGuesses = guesses.size,
                    overallAccuracy = overallAccuracy,
                    erpiResult = erpiResult,
                    masteryResult = masteryResult,
                    cwaResult = cwaResult,
                    confusionMatrix = confusionMatrix,
                    mostConfusedPairs = mostConfusedPairs,
                    responseTimeDistribution = responseTimeStats.distribution,
                    averageResponseTime = responseTimeStats.average,
                    fastestResponseTime = responseTimeStats.fastest,
                    slowestResponseTime = responseTimeStats.slowest,
                    sessionHistory = sessionHistory
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun computeErpi(sessions: List<PracticeSession>): ErpiResult? {
        if (sessions.size < 2) return null

        val accuracies = sessions
            .filter { it.totalGuesses > 0 }
            .map { it.sessionAccuracy }

        if (accuracies.size < 2) return null

        // Compute ERPI using linear regression
        val n = accuracies.size
        val x = (0 until n).map { it.toFloat() }
        val y = accuracies

        val xMean = x.average().toFloat()
        val yMean = y.average()

        var numerator = 0f
        var denominator = 0f

        for (i in x.indices) {
            numerator += (x[i] - xMean) * (y[i] - yMean)
            denominator += (x[i] - xMean) * (x[i] - xMean)
        }

        val slope = if (denominator != 0f) numerator / denominator else 0f

        // Compute standard deviation
        val variance = y.map { (it - yMean) * (it - yMean) }.average()
        val stdDev = kotlin.math.sqrt(variance.toDouble()).toFloat()

        // ERPI formula: slope / (stdDev + epsilon)
        val erpiScore = slope / (stdDev + 0.001f)

        val interpretation = when {
            erpiScore > 0.3f -> "📈 Excellent progress! Student is improving rapidly."
            erpiScore > 0.0f -> "📈 Good progress. Student is showing steady improvement."
            erpiScore > -0.1f -> "⚠️ Plateau detected. Consider varying practice sessions."
            else -> "📉 Declining performance. Intervention recommended."
        }

        return ErpiResult(
            erpiScore = erpiScore,
            slope = slope,
            interpretation = interpretation
        )
    }

    private fun computeMastery(guesses: List<PracticeGuess>): MasteryResult? {
        if (guesses.isEmpty()) return null

        val emotions = guesses.map { it.teacherEmotion }.distinct()
        if (emotions.isEmpty()) return null

        val perEmotionScores = mutableMapOf<String, Float>()

        for (emotion in emotions) {
            val emotionGuesses = guesses.filter { it.teacherEmotion == emotion }
            if (emotionGuesses.isNotEmpty()) {
                val recentGuesses = emotionGuesses.takeLast(10) // Last 10 guesses for this emotion
                val accuracy = recentGuesses.count { it.isCorrect }.toFloat() / recentGuesses.size
                perEmotionScores[emotion] = accuracy
            }
        }

        val aggregateMastery = perEmotionScores.values.average().toFloat()
        val strongestEmotion = perEmotionScores.maxByOrNull { it.value }?.key ?: "Unknown"
        val weakestEmotion = perEmotionScores.minByOrNull { it.value }?.key ?: "Unknown"

        return MasteryResult(
            aggregateMastery = aggregateMastery,
            perEmotionScores = perEmotionScores,
            strongestEmotion = strongestEmotion,
            weakestEmotion = weakestEmotion
        )
    }

    private suspend fun computeCwa(guesses: List<PracticeGuess>): CwaResult? {
        if (guesses.isEmpty()) return null

        val emotions = guesses.map { it.teacherEmotion }.distinct()
        if (emotions.isEmpty()) return null

        // Build confusion matrix for CWA calculation
        val confusionMatrix = computeConfusionMatrix(guesses)

        // Compute per-emotion recall (TP / (TP + FN))
        val recalls = mutableMapOf<String, Float>()
        for (emotion in emotions) {
            val tp = confusionMatrix[emotion]?.get(emotion) ?: 0
            val fn = confusionMatrix[emotion]?.values?.sum()?.minus(tp) ?: 0
            val total = tp + fn
            recalls[emotion] = if (total > 0) tp.toFloat() / total else 0f
        }

        // Get therapist weights from database
        val therapistWeights = withContext(Dispatchers.IO) {
            practiceRepository.getWeights()
        }
        
        // Create weight map from therapist weights, or use equal weights as fallback
        val weightMap = if (therapistWeights.isNotEmpty()) {
            therapistWeights.associate { it.emotion to it.weight }
        } else {
            val equalWeight = 1.0f / emotions.size
            emotions.associateWith { equalWeight }
        }

        // Compute CWA: sum(weight * recall)
        var cwaScore = 0f
        for (emotion in emotions) {
            val weight = weightMap[emotion] ?: (1.0f / emotions.size)
            val recall = recalls[emotion] ?: 0f
            cwaScore += weight * recall
        }

        return CwaResult(
            cwaScore = cwaScore,
            weights = weightMap,
            perEmotionRecall = recalls,
            confusionMatrix = confusionMatrix
        )
    }

    private fun computeConfusionMatrix(guesses: List<PracticeGuess>): Map<String, Map<String, Int>> {
        val matrix = mutableMapOf<String, MutableMap<String, Int>>()

        for (guess in guesses) {
            val actual = guess.teacherEmotion
            val predicted = guess.userGuess

            if (!matrix.containsKey(actual)) {
                matrix[actual] = mutableMapOf()
            }

            matrix[actual]!![predicted] = (matrix[actual]!![predicted] ?: 0) + 1
        }

        return matrix
    }

    private fun findMostConfusedPairs(
        confusionMatrix: Map<String, Map<String, Int>>
    ): List<Pair<String, String>> {
        val confusedPairs = mutableListOf<Triple<String, String, Int>>()

        for ((actual, predictions) in confusionMatrix) {
            for ((predicted, count) in predictions) {
                if (actual != predicted && count > 0) {
                    confusedPairs.add(Triple(actual, predicted, count))
                }
            }
        }

        return confusedPairs
            .sortedByDescending { it.third }
            .take(5)
            .map { Pair(it.first, it.second) }
    }

    private fun computeResponseTimeStats(guesses: List<PracticeGuess>): ResponseTimeStats {
        if (guesses.isEmpty()) {
            return ResponseTimeStats(
                distribution = emptyMap(),
                average = 0L,
                fastest = 0L,
                slowest = 0L
            )
        }

        val responseTimes = guesses.map { it.responseTimeMs }

        // Categorize into buckets
        val distribution = mutableMapOf<String, Int>()
        for (time in responseTimes) {
            val bucket = when {
                time < 1000 -> "< 1s"
                time < 3000 -> "1-3s"
                time < 5000 -> "3-5s"
                else -> "> 5s"
            }
            distribution[bucket] = (distribution[bucket] ?: 0) + 1
        }

        return ResponseTimeStats(
            distribution = distribution,
            average = responseTimes.average().toLong(),
            fastest = responseTimes.minOrNull() ?: 0L,
            slowest = responseTimes.maxOrNull() ?: 0L
        )
    }

    private fun buildSessionHistory(
        sessions: List<PracticeSession>,
        guesses: List<PracticeGuess>
    ): List<SessionHistoryItem> {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        return sessions
            .sortedByDescending { it.startTime }
            .map { session ->
                val sessionGuesses = guesses.filter { it.sessionId == session.sessionId }
                val correct = sessionGuesses.count { it.isCorrect }
                val total = sessionGuesses.size

                SessionHistoryItem(
                    sessionId = session.sessionId,
                    formattedDate = dateFormat.format(Date(session.startTime)),
                    totalGuesses = total,
                    correctGuesses = correct,
                    accuracy = if (total > 0) correct.toFloat() / total else 0f
                )
            }
    }

    suspend fun updateTherapistWeights(weights: Map<String, Float>): Boolean {
        return try {
            val sum = weights.values.sum()
            if (abs(sum - 1.0f) > 0.01f) {
                return false
            }

            withContext(Dispatchers.IO) {
                practiceRepository.updateAllWeights(weights)
            }

            // Refresh analytics to reflect new weights
            refresh()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportData(context: Context, format: String): Uri? {
        return when (format) {
            "CSV" -> exportToCsv(context)
            "PDF" -> null // Not yet implemented
            else -> null
        }
    }

    private suspend fun exportToCsv(context: Context): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val student = _state.value.student ?: return@withContext null
                val sessions = practiceRepository.getSessionsByStudent(studentId)
                val guesses = practiceRepository.getGuessesByStudent(studentId)

                val fileName = "${student.name.replace(" ", "_")}_analytics_${System.currentTimeMillis()}.csv"

                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return@withContext null

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        // Write header
                        writer.write("Student Analytics Export\n")
                        writer.write("Student Name,${student.name}\n")
                        writer.write("Age,${student.age}\n")
                        writer.write("Export Date,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                        writer.write("\n")

                        // Write session summary
                        writer.write("Session Summary\n")
                        writer.write("Total Sessions,${sessions.size}\n")
                        writer.write("Total Guesses,${guesses.size}\n")
                        writer.write("Overall Accuracy,${_state.value.overallAccuracy}\n")
                        writer.write("\n")

                        // Write ERPI data
                        _state.value.erpiResult?.let { erpi ->
                            writer.write("ERPI Metrics\n")
                            writer.write("ERPI Score,${erpi.erpiScore}\n")
                            writer.write("Slope,${erpi.slope}\n")
                            writer.write("Interpretation,${erpi.interpretation}\n")
                            writer.write("\n")
                        }

                        // Write per-emotion mastery
                        writer.write("Emotion Mastery\n")
                        writer.write("Emotion,Mastery Score\n")
                        _state.value.masteryResult?.perEmotionScores?.forEach { (emotion, score) ->
                            writer.write("$emotion,$score\n")
                        }
                        writer.write("\n")

                        // Write all guesses
                        writer.write("Detailed Guess Log\n")
                        writer.write("Timestamp,Session ID,Teacher Emotion,User Guess,Correct,Response Time (ms)\n")
                        guesses.forEach { guess ->
                            writer.write("${guess.timestamp},${guess.sessionId},${guess.teacherEmotion},${guess.userGuess},${guess.isCorrect},${guess.responseTimeMs}\n")
                        }
                    }
                }

                uri
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class StudentAnalyticsState(
    val isLoading: Boolean = false,
    val student: Student? = null,
    val sessionCount: Int = 0,
    val totalGuesses: Int = 0,
    val overallAccuracy: Float = 0f,
    val erpiResult: ErpiResult? = null,
    val masteryResult: MasteryResult? = null,
    val cwaResult: CwaResult? = null,
    val confusionMatrix: Map<String, Map<String, Int>> = emptyMap(),
    val mostConfusedPairs: List<Pair<String, String>> = emptyList(),
    val responseTimeDistribution: Map<String, Int> = emptyMap(),
    val averageResponseTime: Long = 0L,
    val fastestResponseTime: Long = 0L,
    val slowestResponseTime: Long = 0L,
    val sessionHistory: List<SessionHistoryItem> = emptyList(),
    val error: String? = null
)

private data class ResponseTimeStats(
    val distribution: Map<String, Int>,
    val average: Long,
    val fastest: Long,
    val slowest: Long
)
