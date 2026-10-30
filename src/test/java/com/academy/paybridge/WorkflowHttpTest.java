package com.academy.paybridge;

import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(WorkflowHttpTest.Tokens.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowHttpTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;
 @org.springframework.test.context.DynamicPropertySource
 static void optionalPostgres(org.springframework.test.context.DynamicPropertyRegistry r){
  String url=System.getenv("PAYBRIDGE_TEST_DB_URL");
  if(url!=null && !url.isBlank()){
   r.add("spring.datasource.url",()->url);
   r.add("spring.datasource.username",()->System.getenv().getOrDefault("PAYBRIDGE_TEST_DB_USER","paybridge"));
   r.add("spring.datasource.password",()->System.getenv().getOrDefault("PAYBRIDGE_TEST_DB_PASSWORD","paybridge-test-only"));
   r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
   r.add("spring.flyway.enabled",()->true);
   r.add("spring.sql.init.mode",()->"never");
  }
 }
 static KeyPair pair;
 static {try {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);pair=g.generateKeyPair();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @TestConfiguration static class Tokens {
  @Bean JwtDecoder decoder(){
   var d=NimbusJwtDecoder.withPublicKey((RSAPublicKey)pair.getPublic()).build();
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("https://issuer.test"),
     new JwtClaimValidator<List<String>>("aud",a->a!=null&&a.contains("paybridge-api"))));return d;
  }
 }
 final HttpClient http=HttpClient.newHttpClient();final JsonMapper json=JsonMapper.builder().build();
 static UUID alice,bob,remit;static String quote,payoutReference;
 String token(String sub,String role){return token(sub,role,"paybridge-api",Instant.now().plusSeconds(300));}
 String token(String sub,String role,String aud,Instant expiry){
  try {
   var c=new JWTClaimsSet.Builder().issuer("https://issuer.test").subject(sub).audience(aud)
    .issueTime(Date.from(Instant.now().minusSeconds(120))).expirationTime(Date.from(expiry))
    .claim("realm_access",Map.of("roles",List.of(role))).claim("email",sub+"@example.com").claim("email_verified",true).build();
   var j=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),c);j.sign(new RSASSASigner((RSAPrivateKey)pair.getPrivate()));return j.serialize();
  }catch(Exception e){throw new RuntimeException(e);}
 }
 record Result(int status,String body){String text(String name){return JsonMapper.builder().build().readTree(body).get(name).asText();}}
 Result call(String method,String path,String bearer,String body,String key){
  try {
   var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
   if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);
   b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
   var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
   synchronized(WorkflowHttpTest.class){
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/endpoint-transcript.jsonl"),
     json.writeValueAsString(Map.of("method",method,"path",path,"status",r.statusCode(),"body",r.body()))+"\n",
     java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
   }
   return new Result(r.statusCode(),r.body());
  }catch(Exception e){throw new RuntimeException(e);}
 }
 Result post(String path,String user,String role,String body){return call("POST",path,token(user,role),body,null);}
 void status(Result r,int expected){assertThat(r.status()).as(r.body()).isEqualTo(expected);}
 String createBody(long amount){return "{\"sourceUsdMinor\":"+amount+",\"beneficiaryName\":\"Ada Obi\",\"bankCode\":\"058\",\"accountNumber\":\"0123456789\"}";}
 Result create(String key,long amount){return call("POST","/api/v1/remittances",token("alice","CUSTOMER"),createBody(amount),key);}
 String base(){return "/api/v1/remittances/"+remit;}
 String local(){return "/api/v1/local/remittances/"+remit;}
 @Test @Order(1) void rejectsMissingInvalidExpiredAndWrongAudience(){
  status(call("GET","/api/v1/settlements/float",null,null,null),401);
  status(call("GET","/api/v1/settlements/float","bad-token",null,null),401);
  status(call("GET","/api/v1/settlements/float",token("treasury","TREASURY","other-api",Instant.now().plusSeconds(300)),null,null),401);
  status(call("GET","/api/v1/settlements/float",token("treasury","TREASURY","paybridge-api",Instant.now().minusSeconds(120)),null,null),401);
 }
 @Test @Order(2) void onboardBindsIdentityAndProtectsKyc(){
  var a=post("/api/v1/customers","alice","CUSTOMER","{\"firstName\":\"Alice\",\"lastName\":\"Example\",\"email\":\"alice@example.com\",\"phoneNumber\":\"08031230001\"}");status(a,201);alice=UUID.fromString(a.text("id"));
  var b=post("/api/v1/customers","bob","CUSTOMER","{\"firstName\":\"Bob\",\"lastName\":\"Example\",\"email\":\"bob@example.com\",\"phoneNumber\":\"08031230002\"}");status(b,201);bob=UUID.fromString(b.text("id"));
  status(post("/api/v1/customers/"+alice+"/kyc/verify","alice","CUSTOMER",null),403);
  status(post("/api/v1/customers/"+alice+"/kyc/verify","reviewer","COMPLIANCE",null),200);
  status(post("/api/v1/customers/"+bob+"/kyc/verify","reviewer","COMPLIANCE",null),200);
  status(call("GET","/api/v1/customers/"+bob,token("alice","CUSTOMER"),null,null),403);
  status(call("GET","/api/v1/accounts/by-customer/"+bob+"/balance",token("alice","CUSTOMER"),null,null),403);
  status(call("GET","/api/v1/customers/me",token("alice","CUSTOMER"),null,null),200);
 }
 @Test @Order(3) void treasuryTopupsAreAuthorizedIdempotentAndReconciled(){
  status(post("/api/v1/local/settlements/topups","alice","CUSTOMER","{\"reference\":\"float-1\",\"amountMinor\":100000000}"),403);
  String body="{\"reference\":\"float-1\",\"amountMinor\":100000000}";
  var r=post("/api/v1/local/settlements/topups","treasury","TREASURY",body);status(r,200);assertThat(r.text("cashMinor")).isEqualTo("100000000");
  status(post("/api/v1/local/settlements/topups","treasury","TREASURY",body),200);
  status(post("/api/v1/local/settlements/topups","treasury","TREASURY","{\"reference\":\"float-1\",\"amountMinor\":1}"),409);
  var m=post("/api/v1/settlements/reconciliations","treasury","TREASURY","{\"statementId\":\"s1\",\"closingBalanceMinor\":100000000}");status(m,200);assertThat(m.text("status")).isEqualTo("MATCHED");
  var br=post("/api/v1/settlements/reconciliations","treasury","TREASURY","{\"statementId\":\"s2\",\"closingBalanceMinor\":99999999}");status(br,200);assertThat(br.text("status")).isEqualTo("BREAK");
 }
 @Test @Order(4) void remittanceCreationIsReplaySafeAndOwned(){
  var r=create("r1",10000);status(r,200);remit=UUID.fromString(r.text("id"));payoutReference=r.text("payoutReference");
  var replay=create("r1",10000);status(replay,200);assertThat(replay.text("id")).isEqualTo(remit.toString());
  status(create("r1",20000),409);
  status(call("GET",base(),token("bob","CUSTOMER"),null,null),403);
  status(call("POST","/api/v1/remittances",token("alice","CUSTOMER"),createBody(10000),null),400);
  status(call("POST","/api/v1/remittances",token("alice","CUSTOMER"),"{\"sourceUsdMinor\":10000,\"beneficiaryName\":\"Ada\",\"bankCode\":\"058\",\"accountNumber\":\"bad\"}","bad"),400);
 }
 @Test @Order(5) void fundingMustBeOperatorVerifiedAndMatchAmount(){
  status(post(local()+"/funding","alice","CUSTOMER","{\"fundingReference\":\"f1\",\"sourceUsdMinor\":10000}"),403);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(q,200);quote=q.text("id");assertThat(q.text("destinationMinor")).isEqualTo("14850000");
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}"),422);
  status(post(local()+"/funding","treasury","TREASURY","{\"fundingReference\":\"f1\",\"sourceUsdMinor\":9999}"),422);
  String funding="{\"fundingReference\":\"f1\",\"sourceUsdMinor\":10000}";
  status(post(local()+"/funding","treasury","TREASURY",funding),200);
  status(post(local()+"/funding","treasury","TREASURY",funding),200);
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,remit+":fund")).isEqualTo(1);
 }
 @Test @Order(6) void rejectsExpiredAndForeignQuotesAndRollsBack(){
  db.update("update fx_quote set expires_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),UUID.fromString(quote));
  var expired=post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}");status(expired,422);assertThat(expired.text("code")).isEqualTo("QUOTE_EXPIRED");
  var foreign=post("/api/v1/fx/quotes","bob","CUSTOMER","{\"sourceUsdMinor\":10000}");status(foreign,200);
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+foreign.text("id")+"\"}"),403);
  assertThat(db.queryForObject("select status from remittance where id=?",String.class,remit)).isEqualTo("FUNDED");
  var fresh=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(fresh,200);quote=fresh.text("id");
 }
 @Test @Order(7) void conversionReservesFloatAndUnknownOutcomeDoesNotRefund(){
  var r=post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}");status(r,200);assertThat(r.text("status")).isEqualTo("PAYOUT_PENDING");
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}"),200);
  status(post(local()+"/outcome","treasury","TREASURY","{\"outcome\":\"UNKNOWN\"}"),200);
  var unknown=post(local()+"/process","treasury","TREASURY",null);status(unknown,200);assertThat(unknown.text("status")).isEqualTo("PAYOUT_PENDING");
  assertThat(db.queryForObject("select reserved_minor from settlement_float",Long.class)).isEqualTo(14850000L);
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(100000000L);
 }
 @Test @Order(8) void recoveryAndDuplicateSuccessCaptureOnlyOnce() throws Exception {
  status(post(local()+"/outcome","treasury","TREASURY","{\"outcome\":\"SUCCESS\"}"),200);
  try(var ex=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new ArrayList<java.util.concurrent.Future<Result>>();
   for(int i=0;i<6;i++)tasks.add(ex.submit(()->post(local()+"/process","treasury","TREASURY",null)));
   for(var f:tasks){var rr=f.get();status(rr,200);assertThat(rr.text("status")).isEqualTo("PAID_OUT");}
  }
  status(post("/api/v1/local/remittances/recover","treasury","TREASURY",null),200);
  var done=post(local()+"/process","treasury","TREASURY",null);status(done,200);assertThat(done.text("status")).isEqualTo("PAID_OUT");
  assertThat(db.queryForObject("select reserved_minor from settlement_float",Long.class)).isZero();
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(85150000L);
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,payoutReference+":paid")).isEqualTo(1);
 }
 @Test @Order(9) void failedPayoutPreservesNgnEntitlementAndCanRetry(){
  var r=create("r2",10000);status(r,200);String id=r.text("id"),b="/api/v1/remittances/"+id,l="/api/v1/local/remittances/"+id;
  status(post(l+"/funding","treasury","TREASURY","{\"fundingReference\":\"f2\",\"sourceUsdMinor\":10000}"),200);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(q,200);
  status(post(b+"/convert","alice","CUSTOMER","{\"quoteId\":\""+q.text("id")+"\"}"),200);
  status(post(l+"/outcome","treasury","TREASURY","{\"outcome\":\"FAILED\"}"),200);
  var failed=post(l+"/process","treasury","TREASURY",null);status(failed,200);assertThat(failed.text("status")).isEqualTo("PAYOUT_FAILED");
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(85150000L);
  var retry=post(b+"/retry","alice","CUSTOMER",null);status(retry,200);assertThat(retry.text("attempt")).isEqualTo("2");
  var paid=post(l+"/process","treasury","TREASURY",null);status(paid,200);assertThat(paid.text("status")).isEqualTo("PAID_OUT");
 }
 @Test @Order(10) void insufficientFloatRollsBackQuoteAndJournals(){
  var r=create("too-large",1000000);status(r,200);String id=r.text("id");
  status(post("/api/v1/local/remittances/"+id+"/funding","treasury","TREASURY","{\"fundingReference\":\"f3\",\"sourceUsdMinor\":1000000}"),200);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":1000000}");status(q,200);
  var bad=post("/api/v1/remittances/"+id+"/convert","alice","CUSTOMER","{\"quoteId\":\""+q.text("id")+"\"}");status(bad,422);assertThat(bad.text("code")).isEqualTo("INSUFFICIENT_FLOAT");
  assertThat(db.queryForObject("select status from fx_quote where id=?",String.class,UUID.fromString(q.text("id")))).isEqualTo("OPEN");
  assertThat(db.queryForObject("select status from remittance where id=?",String.class,UUID.fromString(id))).isEqualTo("FUNDED");
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,id+":fx-usd")).isZero();
 }
 @Test @Order(11) void apiDocsExposeOAuthAndNewEndpoints(){
  var r=call("GET","/v3/api-docs",null,null,null);status(r,200);
  assertThat(r.body()).contains("/api/v1/settlements/float","/api/v1/fx/quotes","/api/v1/remittances","authorizationCode","PayBridge");
 }
 @Test @Order(12) void concurrentDuplicateCreateProducesOneOperation() throws Exception {
  try(var ex=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new java.util.ArrayList<java.util.concurrent.Future<Result>>();
   for(int i=0;i<6;i++)tasks.add(ex.submit(()->create("parallel-1",10000)));
   var ids=new HashSet<String>();
   for(var f:tasks){var r=f.get();status(r,200);ids.add(r.text("id"));}
   assertThat(ids).hasSize(1);
  }
 }
 @Test @Order(13) void everyJournalBalancesPerCurrency(){
  var bad=db.queryForList("select journal_id,currency,sum(case when direction='DEBIT' then amount_minor else -amount_minor end) net from ledger_entry group by journal_id,currency having sum(case when direction='DEBIT' then amount_minor else -amount_minor end)<>0");
  assertThat(bad).isEmpty();
 }
}
