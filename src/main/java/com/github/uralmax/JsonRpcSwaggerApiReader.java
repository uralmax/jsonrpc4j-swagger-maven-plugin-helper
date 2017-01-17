package com.github.uralmax;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.logging.Log;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.reader.AbstractReader;
import com.github.kongchen.swagger.docgen.reader.ClassSwaggerReader;
import com.github.kongchen.swagger.docgen.spring.SpringResource;
import com.googlecode.jsonrpc4j.JsonRpcMethod;
import com.googlecode.jsonrpc4j.JsonRpcService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.util.ParameterProcessor;

/**
 * Reader for json-rpc
 *
 * @author Rozhkov Maksim
 * @created 16.01.17
 */
public class JsonRpcSwaggerApiReader extends AbstractReader implements ClassSwaggerReader {

    private String resourcePath;

    /**
     * Content type is constant
     */
    private String[] CONTENT_TYPE = ArrayUtils.toArray(MediaType.APPLICATION_JSON_VALUE);

    public JsonRpcSwaggerApiReader(Swagger swagger, Log log) {

        super(swagger, log);
    }

    public Swagger read(Set<Class<?>> classes) throws GenerateException {

        Map<String, SpringResource> resourceMap = new HashMap<String, SpringResource>();
        for (Class<?> aClass : classes) {
            resourceMap = analyzeController(aClass, resourceMap, "");
        }
        if (swagger == null) {
            swagger = new Swagger();
        }
        for (String str : resourceMap.keySet()) {
            SpringResource resource = resourceMap.get(str);
            read(resource);
        }
        return swagger;
    }

    /**
     * Read resource and fill swagger info
     *
     * @param resource -resource
     * @return swagger main object
     */
    public Swagger read(SpringResource resource) {

        List<Method> methods = resource.getMethods();
        Map<String, Tag> tags = new HashMap<String, Tag>();

        List<SecurityRequirement> resourceSecurities = new ArrayList<SecurityRequirement>();

        Class<?> controller = resource.getControllerClass();

        if (controller.isAnnotationPresent(Api.class)) {
            Api api = AnnotationUtils.findAnnotation(controller, Api.class);
            if (!canReadApi(false, api)) {
                return swagger;
            }
            tags = updateTagsForApi(null, api);
            resourceSecurities = getSecurityRequirements(api);
        }

        resourcePath = resource.getControllerMapping();

        Map<String, List<Method>> apiMethodMap = collectApisByRequestMapping(methods);

        for (String path : apiMethodMap.keySet()) {
            for (Method method : apiMethodMap.get(path)) {
                ApiOperation apiOperation =
                        AnnotationUtils.findAnnotation(method, ApiOperation.class);
                if (apiOperation == null || apiOperation.hidden()) {
                    continue;
                }

                Map<String, String> regexMap = new HashMap<String, String>();

                Operation operation = parseMethod(apiOperation, method);
                updateOperationProtocols(apiOperation, operation);
                updateTagsForOperation(operation, apiOperation);
                updateOperation(CONTENT_TYPE, CONTENT_TYPE, tags, resourceSecurities, operation);
                swagger.path(parseOperationPath(path, regexMap), new Path().post(operation));
            }
        }
        return swagger;
    }

    /**
     * Search params from method
     *
     * @param type        type
     * @param annotations annotation list
     * @return list of params
     */
    protected List<Parameter> getParameters(Type type, List<Annotation> annotations) {

        Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        List<Parameter> parameters = new ArrayList<Parameter>();
        if (chain.hasNext()) {
            SwaggerExtension extension = chain.next();
            parameters = extension.extractParameters(annotations, type, getTypesToSkip(), chain);
        }

        if (!parameters.isEmpty()) {
            for (Parameter parameter : parameters) {
                ParameterProcessor.applyAnnotations(swagger, parameter, type, annotations);
            }
            ;
        } else if (!getTypesToSkip().contains(type)) {
            Parameter param = ParameterProcessor.applyAnnotations(swagger, null, type, annotations);
            if (param != null) {
                parameters.add(param);
            }

        }
        return parameters;
    }

    /**
     * Get real body param for json rpc
     *
     * @param allParams    all find params
     * @param apiOperation - swagger annotation fo operation
     * @param methodName   metho
     * @return real body for request
     */
    private BodyParameter getBobyParam(List<Parameter> allParams, ApiOperation apiOperation,
            String methodName) {

        BodyParameter requestBody = null;
        if (!allParams.isEmpty()) {
            ModelImpl jsonRpcModel = new ModelImpl();
            jsonRpcModel.setType("object");
            StringBuffer kyyJsonRpcWrapper = new StringBuffer();
            Map<String, Property> properties = new HashMap<String, Property>();
            if (allParams.size() == 1) {
                // for json rpc all params body
                if (allParams.get(0) instanceof BodyParameter) {
                    BodyParameter param = (BodyParameter) allParams.get(0);
                    RefProperty property = new RefProperty();
                    RefModel modelParam = (RefModel) param.getSchema();
                    kyyJsonRpcWrapper.append(modelParam.getSimpleRef());
                    property.set$ref(modelParam.get$ref());
                    property.setRequired(true);
                    properties.put("params", new ArrayProperty(property));
                    String description = param.getDescription();
                    if (description == null) {
                        description = swagger.getDefinitions().get(modelParam.getSimpleRef())
                                .getDescription();
                    }
                    jsonRpcModel.setDescription(description);
                }
            } else {
                jsonRpcModel.setDescription(apiOperation.value());
                StringBuffer wrapperOfParams = new StringBuffer();
                ModelImpl paramsWrapperModel = new ModelImpl();
                for (Parameter param : allParams) {
                    // for json rpc all params body
                    if (param instanceof BodyParameter) {
                        BodyParameter bodyParam = (BodyParameter) param;
                        RefProperty property = new RefProperty();
                        RefModel model = (RefModel) bodyParam.getSchema();
                        wrapperOfParams.append(model.getSimpleRef());
                        kyyJsonRpcWrapper.append(model.getSimpleRef());
                        property.set$ref(model.get$ref());
                        property.setRequired(true);
                        paramsWrapperModel.addProperty(bodyParam.getName(), property);
                    }
                }
                wrapperOfParams.append("ParamsWrapper");
                String wrapperOfParamsKey = wrapperOfParams.toString();
                swagger.addDefinition(wrapperOfParamsKey, paramsWrapperModel);
                properties.put("params", new ArrayProperty(new RefProperty(wrapperOfParamsKey)));
            }
            properties.put("jsonrpc",
                    new StringProperty()._enum("2.0").example("2.0").required(true));
            properties.put("id", new StringProperty().example("101").required(true));
            properties.put("method",
                    new StringProperty()._enum(methodName).example(methodName).required(true));
            jsonRpcModel.setProperties(properties);
            kyyJsonRpcWrapper.append("JsonRpcWrapper");
            String key = kyyJsonRpcWrapper.toString();
            swagger.addDefinition(key, jsonRpcModel);
            requestBody = new BodyParameter();
            requestBody.setRequired(true);
            requestBody.setName("request");
            requestBody.setDescription(jsonRpcModel.getDescription());
            requestBody.setSchema(new RefModel(key));
        }
        return requestBody;
    }

    /**
     * Response wrapper for list type
     *
     * @param type     -type
     * @param property - swagger property
     * @return wrapped response
     */
    private Property withResponseContainer(String type, Property property) {

        if ("list".equalsIgnoreCase(type)) {
            return new ArrayProperty(property);
        }
        if ("set".equalsIgnoreCase(type)) {
            return new ArrayProperty(property).uniqueItems();
        }
        if ("map".equalsIgnoreCase(type)) {
            return new MapProperty(property);
        }
        return property;
    }

    /**
     * Get swagger operation from method
     *
     * @param apiOperation - swagger operation
     * @param method       method
     * @return swagger Operation object
     */
    private Operation parseMethod(ApiOperation apiOperation, Method method) {

        Operation operation = new Operation();
        Type responseClass = null;
        String responseContainer = null;
        String operationId = method.getName();
        if (apiOperation.hidden()) {
            return null;
        }
        if (!apiOperation.nickname().isEmpty()) {
            operationId = apiOperation.nickname();
        }

        Map<String, Property> defaultResponseHeaders =
                parseResponseHeaders(apiOperation.responseHeaders());

        operation.summary(apiOperation.value()).description(apiOperation.notes());

        Set<Map<String, Object>> customExtensions =
                parseCustomExtensions(apiOperation.extensions());

        for (Map<String, Object> extension : customExtensions) {
            if (extension == null) {
                continue;
            }
            for (Map.Entry<String, Object> map : extension.entrySet()) {
                operation.setVendorExtension(
                        map.getKey().startsWith("x-") ? map.getKey() : "x-" + map.getKey(),
                        map.getValue());
            }
        }

        if (!apiOperation.response().equals(Void.class)) {
            responseClass = apiOperation.response();
        }
        if (!apiOperation.responseContainer().isEmpty()) {
            responseContainer = apiOperation.responseContainer();
        }
        List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
        for (Authorization auth : apiOperation.authorizations()) {
            if (!auth.value().isEmpty()) {
                SecurityRequirement security = new SecurityRequirement();
                for (AuthorizationScope scope : auth.scopes()) {
                    if (!scope.scope().isEmpty()) {
                        security.addScope(scope.scope());
                    }
                }
                securities.add(security);
            }
        }
        for (SecurityRequirement sec : securities) {
            operation.security(sec);
        }

        if (responseClass == null) {
            responseClass = method.getGenericReturnType();
        }
        boolean hasApiAnnotation = false;
        if (responseClass instanceof Class) {
            hasApiAnnotation =
                    AnnotationUtils.findAnnotation((Class) responseClass, Api.class) != null;
        }
        if (responseClass != null && !responseClass.equals(Void.class) && !responseClass
                .equals(ResponseEntity.class) && !hasApiAnnotation) {
            if (isPrimitive(responseClass)) {
                Property property = ModelConverters.getInstance().readAsProperty(responseClass);
                if (property != null) {
                    Property responseProperty = withResponseContainer(responseContainer, property);
                    operation.response(apiOperation.code(),
                            new Response().description("").schema(responseProperty)
                                    .headers(defaultResponseHeaders));
                }
            } else if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)) {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClass);
                if (models.isEmpty()) {
                    Property pp = ModelConverters.getInstance().readAsProperty(responseClass);
                    operation.response(apiOperation.code(),
                            new Response().description("").schema(pp)
                                    .headers(defaultResponseHeaders));
                }
                for (String key : models.keySet()) {
                    Property responseProperty = withResponseContainer(responseContainer,
                            new RefProperty().asDefault(key));
                    operation.response(apiOperation.code(),
                            new Response().description("").schema(responseProperty)
                                    .headers(defaultResponseHeaders));
                    swagger.model(key, models.get(key));
                }
                models = ModelConverters.getInstance().readAll(responseClass);
                for (Map.Entry<String, Model> entry : models.entrySet()) {
                    swagger.model(entry.getKey(), entry.getValue());
                }
            }
        }

        operation.operationId(operationId);

        ApiResponses responseAnnotation =
                AnnotationUtils.findAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            updateApiResponse(operation, responseAnnotation);
        }
        Deprecated annotation = AnnotationUtils.findAnnotation(method, Deprecated.class);
        if (annotation != null) {
            operation.deprecated(true);
        }
        Class[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        List<Parameter> allParams = new ArrayList<Parameter>();
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = genericParameterTypes[i];
            List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            List<Parameter> parameters = getParameters(type, annotations);
            allParams.addAll(parameters);
        }
        JsonRpcMethod jsonRpcMethod = AnnotationUtils.findAnnotation(method, JsonRpcMethod.class);
        BodyParameter bobyParam = getBobyParam(allParams, apiOperation, jsonRpcMethod.value());
        if (bobyParam != null) {
            operation.parameter(bobyParam);
        }
        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("Success"));
        }
        return operation;
    }

    /**
     * Get map of url and methods
     *
     * @param methods analized methods
     * @return map url-> Methods
     */
    private Map<String, List<Method>> collectApisByRequestMapping(List<Method> methods) {

        Map<String, List<Method>> apiMethodMap = new HashMap<String, List<Method>>();
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsonRpcMethod.class)) {
                JsonRpcMethod jsonRpcMethod =
                        AnnotationUtils.findAnnotation(method, JsonRpcMethod.class);
                // It is necessary to modify as few methods able to live on the same url in swagger
                String path = resourcePath + " " + jsonRpcMethod.value();
                if (apiMethodMap.containsKey(path)) {
                    apiMethodMap.get(path).add(method);
                } else {
                    List<Method> ms = new ArrayList<Method>();
                    ms.add(method);
                    apiMethodMap.put(path, ms);
                }
            }
        }
        return apiMethodMap;
    }

    /**
     * Analyze controller and search resource
     *
     * @param controllerClazz class
     * @param resourceMap     map of result
     * @param description     description
     * @return return result map
     */
    private Map<String, SpringResource> analyzeController(Class<?> controllerClazz,
            Map<String, SpringResource> resourceMap, String description) {

        JsonRpcService serviceAnnotation =
                AnnotationUtils.findAnnotation(controllerClazz, JsonRpcService.class);
        if (serviceAnnotation != null) {
            String requestUrl = serviceAnnotation.value();
            for (Method method : controllerClazz.getMethods()) {
                JsonRpcMethod jsonRpcMethod =
                        AnnotationUtils.findAnnotation(method, JsonRpcMethod.class);
                if (jsonRpcMethod != null) {
                    // It is necessary to modify as few methods able to live on the same url in
                    // swagger
                    String resourceKey =
                            controllerClazz.getCanonicalName() + requestUrl + ' ' + jsonRpcMethod
                                    .value();
                    if (!resourceMap.containsKey(resourceKey)) {
                        SpringResource springResource =
                                new SpringResource(controllerClazz, requestUrl, resourceKey,
                                        description);
                        springResource.setControllerMapping(requestUrl);
                        resourceMap.put(resourceKey, springResource);
                    }
                    resourceMap.get(resourceKey).addMethod(method);
                }
            }
        }
        return resourceMap;
    }
}
