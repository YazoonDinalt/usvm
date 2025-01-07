package org.usvm.statistics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.usvm.UState
import kotlinx.serialization.encodeToString
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.atomicfu.atomic

@Serializable
data class Params(val funName: String, val type: String, val value: String, val transitive: Boolean)

@Serializable
data class Metric(val guid: String, val name: String, val params: Params)

class Agent<Method, Statement, State : UState<*, Method, Statement, *, *, State>>   (
    private val timeStatistics: TimeStatistics<Method, State>,
    private val stepsStatistics: StepsStatistics<Method, State>,
    private val coverage: CoverageStatistics<Method, Statement, State>
) : UMachineObserver<State> {

    private val shouldStop = atomic(false)
    private val serverConnection = ServerConnection(CONFIG.getUidUrl, CONFIG.saveMetricUrl)
    private var methodUnderMonitoring = mutableListOf<Method>()

    init {
        getCurrentMethods()
    }

    /**
     * The class responsible for sending data to the server
     */

    inner class ServerConnection(private var getUidUrl: String, private var saveMetricUrl: String) {

        /**
         * Personal uid of a separate session
         */

        var uid = "NotConnection"

        private fun connection(url: String, type: String): HttpURLConnection {
            val httpUrlConnection = URL(url).openConnection() as HttpURLConnection
            httpUrlConnection.requestMethod = type
            if (type == "POST") {
                httpUrlConnection.doOutput = true
                httpUrlConnection.setRequestProperty("Content-Type", "application/json")
            }

            return httpUrlConnection
        }

        fun getUID() {
            val httpUrlConnection = connection(getUidUrl, "GET")
            val streamReader = InputStreamReader(httpUrlConnection.inputStream)
            streamReader.use { uid = it.readText() }
            httpUrlConnection.disconnect()
        }

        fun saveMetrics(metrics: String) {
            val httpUrlConnection = connection(saveMetricUrl, "POST")
            OutputStreamWriter(httpUrlConnection.outputStream).use { it.write(metrics) }
            httpUrlConnection.disconnect()
        }

    }

    /**
     * A function that starts collecting statistics, serializing them and sending them to the server
     */

    override fun onMachineStarted() {
        startCollectionStatistics()
    }

    private fun startCollectionStatistics() {
        Thread {
            try {
                serverConnection.getUID()
            } catch (e: Exception) {
                println("Failed to get session uid, perhaps the server is not active")
                return@Thread
            }


            if (serverConnection.uid == "NotConnection") return@Thread

            while (!shouldStop.value) {
                try {
                    serverConnection.saveMetrics(serialize(getStatistic()))
                } catch (e: Exception) {
                    println("Failed to send metrics, check the server")
                    return@Thread
                }
                Thread.sleep(CONFIG.deltaTime)
            }
        }.start()
    }

    override fun onMachineStopped() {
        try {
            serverConnection.saveMetrics(serialize(getStatistic()))
        } catch (e: Exception) {
            println("Failed to send metrics, check the server")
        }
        shouldStop.value = true
    }

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        getCurrentMethods()
    }

    private fun getCurrentMethods() {
        methodUnderMonitoring = coverage.getCoverageMethods().toMutableList()
    }

    /**
     * Function to get statistics
     */

    private fun getStatistic() : Map<Method, Map<String, Comparable<*>>>{
        val statsMap = mutableMapOf<Method, Map<String, Comparable<*>>>()

        for (method in methodUnderMonitoring) {
            statsMap[method] = mapOf(
                "Steps" to stepsStatistics.getMethodSteps(method),
                "Time" to timeStatistics.getTimeSpentOnMethod(method)
            )
        }

        return statsMap
    }

    /**
     * A function that is responsible for serializing statistics
     */

    private fun serialize(currentStatistic: Map<Method, Map<String, Comparable<*>>>): String {

        val metricsList = mutableListOf<Metric>()

        for ((method, statMap) in currentStatistic) {
            for ((name, count) in statMap) {
                val metric = Metric(
                    guid = serverConnection.uid,
                    name = name,
                    params = Params(
                        funName = method.toString(),
                        type = "int",
                        value = count.toString(),
                        transitive = false
                    )
                )
                metricsList += metric
            }
        }

        val json = Json { prettyPrint = true }
        val jsonString = json.encodeToString(metricsList)

        return jsonString
    }

}

/**
 * Configurations necessary for the agent to work correctly
 */

object CONFIG {
    var getUidUrl: String = "http://localhost:8080/new-session"
    var saveMetricUrl: String = "http://localhost:8080/metrics"
    var deltaTime: Long = 1000L
}
