package com.careerpilot;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.service.ConfigurableCareerAiService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurableCareerAiServiceTest {
 @Test void identifiesAgentRouterAsNotConfiguredWhenItsOwnKeyIsMissing() {
  var service=new ConfigurableCareerAiService(new ObjectMapper(),"agentrouter","openai-key-must-not-be-used","","","gpt-5.5","https://co.agentrouter.org/v1");
  assertFalse(service.providerConfigured());
  assertEquals("AGENTROUTER_NOT_CONFIGURED",service.providerMode());
 }

 @Test void reportsActionableQuotaErrorWithoutReturningRawProviderResponse() throws Exception {
  ObjectMapper json=new ObjectMapper();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{
   byte[] bytes="{\"error\":{\"message\":\"You have no credits remaining.\"}}".getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(429,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try {
   var service=new ConfigurableCareerAiService(json,"agentrouter","unused-openai-secret","test-secret","","mock-model","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
   var error=assertThrows(ConfigurableCareerAiService.AiProviderException.class,()->service.analyze("Java engineer","Java role"));
   assertTrue(error.getMessage().contains("no credits remaining"));
   assertFalse(error.getMessage().contains("test-secret"));
  } finally {server.stop(0);}
 }

 @Test void distinguishesAgentRouterClientBlockFromInvalidCredentials() throws Exception {
  ObjectMapper json=new ObjectMapper();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{
   byte[] bytes="{\"message\":\"unauthorized client detected, contact support for assistance\"}".getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(401,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try {
   var service=new ConfigurableCareerAiService(json,"agentrouter","unused-openai-secret","test-secret","","mock-model","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
   var error=assertThrows(ConfigurableCareerAiService.AiProviderException.class,()->service.analyze("Java engineer","Java role"));
   assertTrue(error.getMessage().contains("unsupported API client"));
   assertTrue(error.getMessage().contains("Ask AgentRouter support"));
   assertFalse(error.getMessage().contains("test-secret"));
  } finally {server.stop(0);}
 }

 @Test void groqOpenAiCompatibleProviderParsesAnalysisAndQuestionJson() throws Exception {
  ObjectMapper json=new ObjectMapper();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{
   assertEquals("Bearer test-secret",exchange.getRequestHeaders().getFirst("Authorization"));
   String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertTrue(request.contains("mock-model"));
   String content=request.contains("Compare resume")
     ?"{\"score\":78,\"summary\":\"Evidence review\",\"matchedTerms\":[\"Java\"],\"termsWithoutEvidence\":[\"Kubernetes\"],\"recommendations\":[\"Review evidence\"]}"
     :request.contains("Create exactly five")
       ?"{\"questions\":[{\"category\":\"ROLE\",\"question\":\"Question one?\",\"relevance\":\"Role\",\"answerStructure\":\"Plan\"},{\"category\":\"PROJECT\",\"question\":\"Question two?\",\"relevance\":\"Project\",\"answerStructure\":\"Context\"},{\"category\":\"TRADEOFF\",\"question\":\"Question three?\",\"relevance\":\"Judgment\",\"answerStructure\":\"Options\"},{\"category\":\"BEHAVIORAL\",\"question\":\"Question four?\",\"relevance\":\"Growth\",\"answerStructure\":\"Example\"},{\"category\":\"SCENARIO\",\"question\":\"Question five?\",\"relevance\":\"Planning\",\"answerStructure\":\"Steps\"}]}"
       :"{\"headline\":\"Good momentum\",\"insights\":[\"Track outcomes by source.\"]}";
   String response=json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content",content)))));byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try {
   var service=new ConfigurableCareerAiService(json,"groq","unused-openai-secret","","test-secret","mock-model","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");assertTrue(service.providerConfigured());
   var analysis=service.analyze("Java engineer","Java and Kubernetes role");assertEquals("GROQ",analysis.get("mode"));assertEquals(78,analysis.get("score"));assertEquals("Java",((List<?>)analysis.get("matchedTerms")).get(0));
   var owner=new UserAccount("ai@example.test","Test User","hash");var app=new JobApplication(owner,"Acme","Engineer",null,"Java role",null,"SAVED","MEDIUM",null);
   assertEquals(5,service.generateQuestions(owner,app,"MEDIUM").size());
   assertEquals("Good momentum",service.careerInsights(Map.of("total",5,"resolved",0)).get("headline"));
  } finally {server.stop(0);}
 }

 @Test void groqSelectionNeverSilentlyReturnsDemoWhenKeyIsMissing() {
  var service=new ConfigurableCareerAiService(new ObjectMapper(),"groq","","","","openai/gpt-oss-20b","https://api.groq.com/openai/v1");
  assertEquals("GROQ_NOT_CONFIGURED",service.providerMode());
  assertThrows(ConfigurableCareerAiService.AiProviderException.class,()->service.analyze("Java engineer","Java role"));
 }
}
