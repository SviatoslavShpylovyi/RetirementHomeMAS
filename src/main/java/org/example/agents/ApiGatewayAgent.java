package org.example.agents;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import org.example.logging.FrontendLogStore;

public class ApiGatewayAgent extends Agent {
    private static final String ConversationId = "activity-proposal-create";
    private final BlockingQueue<String> incomingActivityJson = new LinkedBlockingQueue<>();
    private HttpServer server;
    @Override
    protected void setup(){
        int port = 8080;
        Object[]args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Integer) {
            port = (Integer) args[0];
        }
        try{
            startHttpServer(port);
            System.out.println(getLocalName()+" HTTP API started on http://localhost:"+port);
        }catch(IOException ex){
            System.err.println("Cann't start API gateway:"+ex.getMessage());
            doDelete();
            return;
        }
        addBehaviour(new TickerBehaviour(this, 500) {
            @Override
            protected void onTick(){
                String json;
                while((json = incomingActivityJson.poll())!=null){
                    ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
                    msg.addReceiver(new AID("scenario-agent", AID.ISLOCALNAME));
                    msg.setConversationId(ConversationId);
                    msg.setLanguage("JSON");
                    msg.setContent(json);
                    send(msg);
                }
            }
        });
    }
    private void startHttpServer(int port) throws IOException{
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/activity-proposals", this::handleActivityProposal);
        server.createContext("/api/logs", this::handleLogs);
        server.createContext("/api/health", this::handleHealth);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }
    private void handleLogs(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();

        long after = parseLongQueryParam(query, "after", 0L);
        int limit = (int) parseLongQueryParam(query, "limit", 200L);

        String json = FrontendLogStore.toJson(after, limit);

        respond(exchange, 200, json);
    }
    private void handleHealth(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        respond(exchange, 200, "{\"status\":\"ok\"}");
    }
    private long parseLongQueryParam(String query, String parameterName, long defaultValue) {
        if (query == null || query.isBlank()) {
            return defaultValue;
        }

        String[] parameters = query.split("&");

        for (String parameter : parameters) {
            String[] keyValue = parameter.split("=", 2);

            if (keyValue.length == 2 && parameterName.equals(keyValue[0])) {
                try {
                    return Long.parseLong(keyValue[1]);
                } catch (NumberFormatException ex) {
                    return defaultValue;
                }
            }
        }

        return defaultValue;
    }
    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }




    private void handleActivityProposal(HttpExchange exchange) throws IOException{
        if(handleCorsPreflight(exchange)){
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
        if(body.isBlank()){
            respond(exchange, 400, "{\"error\":\"Empty JSON body\"}");
            return;
        }
        incomingActivityJson.offer(body);
        respond(exchange, 202,"{\"status\":\"accepted\"}" );
    }
    private void respond(HttpExchange exchange, int statusCode,String body) throws IOException {
        addCorsHeaders(exchange);
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
    @Override
    protected void takeDown(){
        if(server != null){
            server.stop(0);
        }
    }
}
