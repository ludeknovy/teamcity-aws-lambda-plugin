package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.util.CollectionsUtil
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.amazon.AWSCommonParams
import org.apache.commons.validator.routines.UrlValidator

class LambdaPropertiesProcessor: PropertiesProcessor {
    override fun process(properties: MutableMap<String, String>): MutableCollection<InvalidProperty> {
        val invalids = mutableMapOf <String, String>()

        invalids.putAll(AWSCommonParams.validate(properties, false))

        val endpointUrl = properties[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM]
        if (StringUtil.isNotEmpty(endpointUrl) && !UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid(endpointUrl)){
            invalids[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM] = LambdaConstants.LAMBDA_ENDPOINT_URL_ERROR
        }

        if (StringUtil.isEmpty(properties[LambdaConstants.SCRIPT_CONTENT_PARAM])){
            invalids[LambdaConstants.SCRIPT_CONTENT_PARAM] = LambdaConstants.SCRIPT_CONTENT_ERROR
        }

        return CollectionsUtil.convertCollection(invalids.entries) { source ->
            InvalidProperty(source.key, source.value)
        }
    }
}