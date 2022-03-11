package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.RunDetails
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.api.Invocation
import org.jmock.lib.action.CustomAction
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.io.InputStream
import kotlin.random.Random

class LambdaCommandLineTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var generalCommandLine: GeneralCommandLine
    private lateinit var process: Process
    private lateinit var inputStream: InputStream
    private lateinit var logger: LambdaLogger
    private lateinit var detachedBuildApi: DetachedBuildApi
    private lateinit var workingDirectory: File
    private lateinit var runDetails: RunDetails

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        generalCommandLine = m.mock(GeneralCommandLine::class.java)
        process = m.mock(Process::class.java)
        inputStream = m.mock(InputStream::class.java)
        logger = m.mock(LambdaLogger::class.java)
        detachedBuildApi = m.mock(DetachedBuildApi::class.java)
        workingDirectory = m.mock(File::class.java)
        runDetails = RunDetails(USERNAME, PASSWORD, BUILD_ID, TEAMCITY_URl, ENV_PARAMS, SCRIPT_CONTENT, DIRECTORY_ID)
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }


    @Test
    fun testExecuteCommandLine() {
        val commandLine = LambdaCommandLine(generalCommandLine, logger)
        val numReads = 3

        m.checking(object : Expectations() {
            init {
                allowing(generalCommandLine).createProcess()
                will(returnValue(process))
                allowing(process).inputStream
                will(returnValue(inputStream))
                allowing(logger).log(with(any(String::class.java)))
                allowing(logger).log(with(any(ByteArray::class.java)))

                for (i in 0..numReads) {
                    oneOf(inputStream).read(with(any(ByteArray::class.java)))
                    will(returnValue(Random.nextInt(1, 5000)))
                    oneOf(detachedBuildApi).logAsync(with(any(String::class.java)))
                    will(object : CustomAction("Mock Async logging") {
                        override fun invoke(invocation: Invocation?): Any {
                            return GlobalScope.async { i }
                        }
                    })
                }

                oneOf(inputStream).read(with(any(ByteArray::class.java)))
                will(returnValue(-1))
                oneOf(process).waitFor()
                will(returnValue(0))
            }
        })

        val jobs = commandLine.executeCommandLine(detachedBuildApi)
        Assert.assertEquals(jobs.size, numReads + 1)

        runBlocking {
            val values = jobs.awaitAll()
            Assert.assertEquals(values, (0..numReads).toList())
        }
    }

    @Test
    fun testCreateCommandLine() {
        m.checking(object : Expectations() {
            init {
                allowing(workingDirectory).absolutePath
                will(returnValue(ABSOLUTE_PATH))
            }
        })

        val generalCommandLine = LambdaCommandLine.createCommandLine(workingDirectory, runDetails)
        Assert.assertEquals(generalCommandLine.exePath, "/usr/bin/sh")
        Assert.assertEquals(generalCommandLine.workDirectory, workingDirectory)
        Assert.assertEquals(generalCommandLine.envParams, ENV_PARAMS)
        Assert.assertEquals(
            generalCommandLine.parametersList.list,
            listOf("$ABSOLUTE_PATH/${runDetails.directoryId}/${runDetails.customScriptFilename}")
        )
    }

    companion object {
        private const val TEAMCITY_URl = "http://teamcityUrl"
        private const val BUILD_ID = "buildId"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private val ENV_PARAMS = mapOf<String, String>(Pair("key", "value"))
        private const val SCRIPT_CONTENT = "scriptContent"
        private const val DIRECTORY_ID = "directoryId"
        private const val ABSOLUTE_PATH = "absolutePath"
    }

}