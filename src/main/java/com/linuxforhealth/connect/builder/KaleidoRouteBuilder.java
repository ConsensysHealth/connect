package com.linuxforhealth.connect.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linuxforhealth.connect.processor.MetaDataProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.http.entity.ContentType;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.linuxforhealth.connect.support.CamelContextSupport;

import javax.ws.rs.HttpMethod;

public class KaleidoRouteBuilder extends BaseRouteBuilder {

    
    private final Logger logger = LoggerFactory.getLogger(KaleidoRouteBuilder.class);

    public final static String ROUTE_ID = "kaleido";
    public final static String ROUTE_PRODUCER_ID = "kaleido-producer-store-and-notify";

    @Override
    protected String getRoutePropertyNamespace() {return "lfh.connect.kaleido";}

    
    @Override
    // public void configure() throws Exception {
    protected void buildRoute(String routePropertyNamespace) {
        CamelContextSupport ctxSupport = new CamelContextSupport(getContext());
        String kaleidoUri = ctxSupport.getProperty("lfh.connect.kaleido.uri");
        String apiKey = System.getenv("APIKEY"); 

            from(kaleidoUri)
                .setHeader(Exchange.HTTP_METHOD,simple("GET"))
                .routeId(ROUTE_ID)
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.CONTENT_TYPE, constant(ContentType.APPLICATION_JSON))
                .setHeader("x-api-key",simple(apiKey))
                .choice()
                    .when( header("x-api-key").isNull())
                    .throwException(RuntimeException.class, "Invalid Credentials")
                .end()
                .log("Routing to REST")                
                //.to("https://jsonplaceholder.typicode.com/todos/1?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .to("https://hun-demo.hun-dev.kaleido.cloud/api/v1/ping?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .process(new MetaDataProcessor(routePropertyNamespace))
                .id(ROUTE_PRODUCER_ID)
                .log(LoggingLevel.DEBUG, logger, "Response code: ${header.CamelHttpResponseCode}")
                .unmarshal().json()
                .choice()

                    // Only process successful service responses
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .process(new MetaDataProcessor(getRoutePropertyNamespace()))
                        .to(LinuxForHealthRouteBuilder.STORE_AND_NOTIFY_CONSUMER_URI)
                    .endChoice()

                    .otherwise()
                        .log(LoggingLevel.ERROR, logger, "Kaleido Service error response code: ${header.CamelHttpResponseCode}")
                        .stop()
                    .endChoice()

                .end();
        }


}