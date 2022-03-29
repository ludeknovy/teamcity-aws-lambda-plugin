package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import jetbrains.buildServer.runner.lambda.LambdaConstants.EDIT_PARAMS_HTML
import jetbrains.buildServer.runner.lambda.LambdaConstants.EDIT_PARAMS_JSP
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_DESCR
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_DISPLAY_NAME
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.LambdaConstants.VIEW_PARAMS_HTML
import jetbrains.buildServer.runner.lambda.LambdaConstants.VIEW_PARAMS_JSP
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.RunType
import jetbrains.buildServer.serverSide.RunTypeRegistry
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView

class LambdaRunType(
    registry: RunTypeRegistry,
    private val descriptor: PluginDescriptor,
    private val controllerManager: WebControllerManager,
    private val serverSettings: ServerSettings
) : RunType() {

    private val myEditParamsPath: String = registerController(EDIT_PARAMS_JSP, EDIT_PARAMS_HTML)
    private val myViewEditParamsPath: String = registerController(VIEW_PARAMS_JSP, VIEW_PARAMS_HTML)

    init {
        registry.registerRunType(this)
    }

    private fun registerController(jspPath: String, htmlPath: String): String {
        val resolvedHtmlPath = descriptor.getPluginResourcesPath(htmlPath)
        val resolvedJspPath = descriptor.getPluginResourcesPath(jspPath)

        controllerManager.registerController(resolvedHtmlPath) { _, _ ->
            ModelAndView(resolvedJspPath)
        }

        return resolvedHtmlPath
    }

    override fun getRunnerPropertiesProcessor(): PropertiesProcessor = LambdaPropertiesProcessor {
        AWSCommonParams.withAWSClients<AmazonIdentityManagement, Exception>(it) { clients ->
            AmazonIdentityManagementClientBuilder.standard()
                .withClientConfiguration(clients.clientConfiguration)
                .withCredentials(AWSCommonParams.getCredentialsProvider(it))
                .build()
        }
    }

    override fun getEditRunnerParamsJspFilePath(): String = myEditParamsPath

    override fun getViewRunnerParamsJspFilePath(): String = myViewEditParamsPath

    override fun getDefaultRunnerProperties(): MutableMap<String, String> =
        AWSCommonParams.getDefaults(serverSettings.serverUUID)

    override fun getType(): String = RUNNER_TYPE

    override fun getDisplayName(): String = RUNNER_DISPLAY_NAME

    override fun getDescription(): String = RUNNER_DESCR
}