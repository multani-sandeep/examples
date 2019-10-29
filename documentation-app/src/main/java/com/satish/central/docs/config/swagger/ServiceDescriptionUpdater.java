package com.satish.central.docs.config.swagger;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;


/**
 * 
 * @author satish sharma
 * <pre>
 *   Periodically poll the service instaces and update the in memory store as key value pair	
 * </pre>
 */
@Component
public class ServiceDescriptionUpdater {
	
	private static final Logger logger = LoggerFactory.getLogger(ServiceDescriptionUpdater.class);
	
	private static final String DEFAULT_SWAGGER_URL="/v2/api-docs";
	private static final String KEY_SWAGGER_URL="swagger_url";
	
//	@Autowired
//	private DiscoveryClient discoveryClient;

	private final RestTemplate template;

	@Autowired
	private final RestTemplate wtrtemplate;
	
	public ServiceDescriptionUpdater(){
		this.template = new RestTemplate();
		this.wtrtemplate = new RestTemplate(getClientHttpRequestFactory());
	}

	@Autowired
	private Services list ;

	@Component
	@ConfigurationProperties(prefix="swagger")
	public class Services {

		private Map<String, String> list = new HashMap<String, String>();

		public Map<String, String> getList() {
			return this.list;
		}
	}
	
	@Autowired
	private ServiceDefinitionsContext definitionContext;
	
	private Map<String, String> failedSwaggers = null;
	
	@Scheduled(fixedDelayString= "${swagger.config.refreshrate}")
	public void refreshSwaggerConfigurations(){
		logger.debug("Starting Service Definition Context refresh");
		
//		discoveryClient.getServices().stream().forEach(serviceId -> {
//			logger.debug("Attempting service definition refresh for Service : {} ", serviceId);
//			List<ServiceInstance> serviceInstances = discoveryClient.getInstances(serviceId);
//			if(serviceInstances == null || serviceInstances.isEmpty()){ //Should not be the case kept for failsafe
//				logger.info("No instances available for service : {} ",serviceId);
//			}else{
//				ServiceInstance instance = serviceInstances.get(0);
//				String swaggerURL =  getSwaggerURL( instance);
//				
//				Optional<Object> jsonData = getSwaggerDefinitionForAPI(serviceId, swaggerURL);
//				
//				if(jsonData.isPresent()){
//					String content = getJSON(serviceId, jsonData.get());
//					definitionContext.addServiceDefinition(serviceId, content);
//				}else{
//					logger.error("Skipping service id : {} Error : Could not get Swagegr definition from API ",serviceId);
//				}
//				
//				logger.info("Service Definition Context Refreshed at :  {}",LocalDate.now());
//			}
//		});
		
		failedSwaggers = new HashMap<String, String>();

		for (Map.Entry<String, String> entry : list.getList().entrySet()) {
 		    logger.info(entry.getKey() + "/" + entry.getValue());
 		    String serviceId = entry.getKey();
	   		String swaggerURL =  entry.getValue();
			
			Optional<Object> jsonData = getWTRSwaggerDefinitionForAPI(serviceId, swaggerURL);
			
			if(jsonData.isPresent()){
				String content = getJSON(serviceId, jsonData.get());
				definitionContext.addServiceDefinition(serviceId, content);
			}else{
				logger.error("Skipping service id : {} Error : Could not get Swagegr definition from API ",serviceId);
			}
			
			logger.info("Service Definition Context Refreshed at :  {}",LocalDate.now());

		}
		logger.info("Failed swaggers: "+ failedSwaggers);
	}
	
	private HttpComponentsClientHttpRequestFactory getClientHttpRequestFactory()
    {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory
                          = new HttpComponentsClientHttpRequestFactory();
         
        clientHttpRequestFactory.setHttpClient(httpClient());
              
        return clientHttpRequestFactory;
    }
     
    private HttpClient httpClient()
    {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
 
        credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials("sandeep.singh@waitrose.co.uk", "Gr3mlin0*"));
 
        HttpClient client = HttpClientBuilder
                                .create()
                                .setDefaultCredentialsProvider(credentialsProvider)
                                .build();
        return client;
    }
    
    ObjectMapper jsonMapper = new ObjectMapper();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

	private Optional<Object> getWTRSwaggerDefinitionForAPI(String serviceName, String url){
		logger.debug("Accessing the SwaggerDefinition JSON for Service : {} : URL : {} ", serviceName, url);
		try{
			wtrtemplate.getInterceptors().add(new BasicAuthorizationInterceptor("sandeep.singh@waitrose.co.uk", "Gr3mlin0!"));
			org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
			headers.set("Content-Type", "application/json");
			
			HttpEntity entity = new HttpEntity(headers);
			
			Object jsonData = Collections.EMPTY_MAP;
			try {
//				jsonData = new ObjectMapper().readValue(wtrtemplate.exchange(
//				    url, HttpMethod.GET, entity, String.class).getBody(), HashMap.class);
				String response = wtrtemplate.exchange(
					    url, HttpMethod.GET, entity, String.class).getBody();
				if(url.endsWith(".yaml")) {
					jsonData = yamlMapper.readValue(response, HashMap.class);
				}else {
					jsonData = jsonMapper.readValue(response, HashMap.class);
				}
			} catch (HttpClientErrorException | JsonParseException | JsonMappingException e) {
				failedSwaggers.put(serviceName, url);
				e.printStackTrace();
			}catch (IOException e) {
				failedSwaggers.put(serviceName, url);
				e.printStackTrace();				
			}
			
			return Optional.of(jsonData);
		}catch(RestClientException ex){
			ex.printStackTrace();
			logger.error("Error while getting service definition for service : {} Error : {} ", serviceName, ex.getMessage());
			return Optional.empty();
		}
	}

//	private String getSwaggerURL( ServiceInstance instance){
//		String swaggerURL = instance.getMetadata().get(KEY_SWAGGER_URL);
//		return swaggerURL != null ? instance.getUri()+swaggerURL : instance.getUri()+DEFAULT_SWAGGER_URL;
//	}
	
	private Optional<Object> getSwaggerDefinitionForAPI(String serviceName, String url){
		logger.debug("Accessing the SwaggerDefinition JSON for Service : {} : URL : {} ", serviceName, url);
		try{
			Object jsonData = template.getForObject(url, Object.class);
			logger.info(jsonData.getClass().toString());
			logger.info(jsonData.toString().substring(0, 400));
			return Optional.of(jsonData);
		}catch(RestClientException ex){
			logger.error("Error while getting service definition for service : {} Error : {} ", serviceName, ex.getMessage());
			return Optional.empty();
		}
		 
	}

	public String getJSON(String serviceId, Object jsonData){
		try {
			return new ObjectMapper().writeValueAsString(jsonData);
		} catch (JsonProcessingException e) {
			logger.error("Error : {} ", e.getMessage());
			return "";
		}
	}
}
