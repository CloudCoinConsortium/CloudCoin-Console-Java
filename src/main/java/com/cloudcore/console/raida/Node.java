package com.cloudcore.console.raida;

import com.cloudcore.console.utils.Utils;
import com.google.gson.Gson;
import org.asynchttpclient.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.asynchttpclient.Dsl.asyncHttpClient;

/*
 * This Class Contains the properties of a RAIDA node.
 */
public class Node {

    public enum NodeStatus {
        Ready,
        NotReady,
    }


    /* Fields */

    public enum TicketHistory {Untried, Failed, Success}

    private AsyncHttpClient client;
    private Gson gson;

    public int nodeNumber;
    public String fullUrl;
    public MultiDetectResponse multiResponse = new MultiDetectResponse();

    public boolean failsDetect;
    public boolean failsFix;
    public boolean failsEcho;

    public TicketHistory ticketHistory = TicketHistory.Untried;
    public String ticket = "";
    public boolean hasTicket;

    public NodeStatus RAIDANodeStatus = NodeStatus.NotReady;

    public long responseTime;

    //Constructor
    public Node(int NodeNumber) {
        this.nodeNumber = NodeNumber;
        fullUrl = GetFullURL();
        //System.out.println(fullUrl);

        client = asyncHttpClient();
        gson = Utils.createGson();
    }

    public Node(int NodeNumber, RAIDANode node) {
        this.nodeNumber = NodeNumber;
        fullUrl = "https://" + node.urls[0].url + "/service/";

        client = asyncHttpClient();
        gson = Utils.createGson();
    }


    /* Methods */

    public String getFullURL() {
        return "https://raida" + (nodeNumber - 1) + ".cloudcoin.global/service/";
    }

    public void resetTicket() {
        hasTicket = false;
        ticketHistory = TicketHistory.Untried;
        ticket = "";
    }

    public void newCoin() {
        hasTicket = false;
        ticketHistory = TicketHistory.Untried;
        ticket = "";
        failsDetect = false;
    }

    public class MultiDetectResponse {
        public Response[] responses;
    }

    public boolean isFailed() {
        return failsEcho || failsDetect;
    }

    public String GetFullURL() {
        return "https://raida" + (nodeNumber - 1) + ".cloudcoin.global/service/";
    }



    public CompletableFuture<Response> echo() {
        return CompletableFuture.supplyAsync(() -> {
            Response echoResponse = new Response();
            echoResponse.fullRequest = this.fullUrl + "echo";
            long before = System.currentTimeMillis();
            failsEcho = true;
            try {
                echoResponse.fullResponse = Utils.getHtmlFromURL(echoResponse.fullRequest);
                //System.out.println("Echo From Node - " + nodeNumber + ". " + echoResponse.fullResponse);
                //System.out.println("Echo URL - "+ fullUrl);
                if (echoResponse.fullResponse.contains("ready")) {
                    echoResponse.success = true;
                    echoResponse.outcome = "ready";
                    this.RAIDANodeStatus = NodeStatus.Ready;
                    failsEcho = false;
                } else {
                    this.RAIDANodeStatus = NodeStatus.NotReady;
                    echoResponse.success = false;
                    echoResponse.outcome = "error";
                    failsEcho = true;
                }
            } catch (Exception ex) {
                echoResponse.outcome = "error";
                echoResponse.success = false;
                this.RAIDANodeStatus = NodeStatus.NotReady;
                failsEcho = true;
                if (ex.getMessage() != null)
                    echoResponse.fullResponse = ex.getMessage();
                //System.out.println("Error---" + ex.getMessage());
            }
            long after = System.currentTimeMillis();
            responseTime = after - before;
            echoResponse.milliseconds = (int) responseTime;
            //System.out.println("Echo Complete-Node No.-" + NodeNumber + ".Status-" + RAIDANodeStatus);
            return echoResponse;
        });
    }

    //int[] nn, int[] sn, String[] an, String[] pan, int[] d, int timeout
    public CompletableFuture<MultiDetectResponse> MultiDetect() {
        /*PREPARE REQUEST*/
        RAIDA raida = RAIDA.getInstance();
        int[] nn = raida.multiRequest.nn;
        int[] sn = raida.multiRequest.sn;
        String[] an = raida.multiRequest.an[nodeNumber - 1];
        String[] pan = raida.multiRequest.pan[nodeNumber - 1];
        int[] d = raida.multiRequest.d;
        int timeout = raida.multiRequest.timeout;

        return MultiDetect(nn, sn, an, pan, d, timeout);
    }

    public CompletableFuture<MultiDetectResponse> MultiDetect(int[] nn, int[] sn, String[] an, String[] pan, int[] d, int timeout) {
        Response[] response = new Response[nn.length];
        for (int i = 0; i < nn.length; i++) {
            response[i] = new Response();
        }

        ArrayList<Param> formParams = new ArrayList<>();
        for (int i = 0; i < nn.length; i++) {
            formParams.add(new Param("nns[]", Integer.toString(nn[i])));
            formParams.add(new Param("sns[]", Integer.toString(sn[i])));
            formParams.add(new Param("ans[]", an[i]));
            formParams.add(new Param("pans[]", pan[i]));
            formParams.add(new Param("denomination[]", Integer.toString(d[i])));
            //System.out.println("url is " + this.fullUrl + "detect?nns[]=" + nn[i] + "&sns[]=" + sn[i] + "&ans[]=" + an[i] + "&pans[]=" + pan[i] + "&denomination[]=" + d[i]);
            response[i].fullRequest = this.fullUrl + "detect?nns[]=" + nn[i] + "&sns[]=" + sn[i] + "&ans[]=" + an[i] + "&pans[]=" + pan[i] + "&denomination[]=" + d[i]; // Record what was sent
        }

        /* MAKE REQUEST */
        final long before = System.currentTimeMillis();

        return client.preparePost(fullUrl + "multi_detect")
                .setFormParams(formParams)
                .setRequestTimeout(timeout)
                .execute(new AsyncHandler() {
                    private final org.asynchttpclient.Response.ResponseBuilder builder = new org.asynchttpclient.Response.ResponseBuilder();

                    @Override
                    public State onStatusReceived(HttpResponseStatus responseStatus) {
                        builder.accumulate(responseStatus);
                        return State.CONTINUE;
                    }

                    @Override
                    public State onHeadersReceived(HttpResponseHeaders headers) {
                        builder.accumulate(headers);
                        return State.CONTINUE;
                    }

                    @Override
                    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
                        builder.accumulate(bodyPart);
                        return State.CONTINUE;
                    }

                    @Override
                    public MultiDetectResponse onCompleted() {
                        /* MAKE REQUEST */
                        long after, ts;

                        org.asynchttpclient.Response httpResponse = builder.build();
                        String totalResponse = httpResponse.getResponseBody();
                        try {
                            if (200 == builder.build().getStatusCode()) {
                                /* PROCESS REQUEST*/
                                after = System.currentTimeMillis();
                                ts = after - before;

                                try {
                                    System.out.println("Response: " + totalResponse);
                                    DetectResponse[] responses = gson.fromJson(totalResponse, DetectResponse[].class);

                                    for (int i = 0; i < nn.length; i++) {
                                        response[i].fullResponse = totalResponse;
                                        response[i].success = "pass".equals(responses[i].status);
                                        response[i].outcome = responses[i].status;
                                    }
                                } catch (Exception e) {
                                    System.out.println("/4: " + e.getLocalizedMessage() + httpResponse.getUri().toUrl());
                                    for (int i = 0; i < nn.length; i++) {
                                        response[i].fullResponse = totalResponse;
                                        response[i].outcome = "e";
                                    }
                                }

                                multiResponse.responses = response;
                                return multiResponse;
                            } else { // 404 not found or 500 error.
                                System.out.println("RAIDA " + nodeNumber + " had an error: " + httpResponse.getStatusCode());
                                after = System.currentTimeMillis();
                                ts = after - before;

                                for (int i = 0; i < nn.length; i++) {
                                    response[i].outcome = "error";
                                    response[i].fullResponse = Integer.toString(httpResponse.getStatusCode());
                                }
                                multiResponse.responses = response;
                                return multiResponse;
                            }
                        } catch (Exception e) {
                            System.out.println("Exception: " + e.getLocalizedMessage());
                            e.printStackTrace();
                        }
                        return multiResponse;
                    }

                    @Override
                    public void onThrowable(Throwable e) {
                        long after = System.currentTimeMillis();
                        long ts = after - before;

                        switch (e.getClass().getCanonicalName()) {
                            case "TimeoutException":
                                for (int i = 0; i < nn.length; i++) {
                                    response[i].outcome = "noresponse";
                                    response[i].fullResponse = e.getLocalizedMessage();
                                }
                                multiResponse.responses = response;
                                return;
                            default:
                                System.out.println("Node#MD" + e.getLocalizedMessage());
                                for (int i = 0; i < nn.length; i++) {
                                    response[i].outcome = "error";
                                    response[i].fullResponse = e.getLocalizedMessage();
                                }
                                multiResponse.responses = response;
                                return;
                        }
                    }
                }).toCompletableFuture();
    }

    /**
     * Method FIX
     * Repairs a fracked RAIDA
     *
     * @param triad three ints trusted server RAIDA numbers
     * @param m1 String ticket from the first trusted server
     * @param m2 String ticket from the second trusted server
     * @param m3 String ticket from the third trusted server
     * @param pan String proposed authenticity number (to replace the wrong AN the RAIDA has)
     * @return String status sent back from the server: sucess, fail or error.
     */
    public Response fix(int[] triad, String m1, String m2, String m3, String pan) {
        Response fixResponse = new Response();
        long before = System.currentTimeMillis();
        fixResponse.fullRequest = fullUrl + "fix?fromserver1=" + triad[0] + "&message1=" + m1 + "&fromserver2=" + triad[1] + "&message2=" + m2 + "&fromserver3=" + triad[2] + "&message3=" + m3 + "&pan=" + pan;

        try {
            fixResponse.fullResponse = Utils.getHtmlFromURL(fixResponse.fullRequest);
            long after = System.currentTimeMillis();
            long ts = after - before;
            fixResponse.milliseconds = (int) ts;

            if (fixResponse.fullResponse.contains("success")) {
                fixResponse.outcome = "success";
                fixResponse.success = true;
            } else {
                fixResponse.outcome = "fail";
                fixResponse.success = false;
            }
        } catch (Exception ex) {//quit
            fixResponse.outcome = "error";
            fixResponse.fullResponse = ex.getMessage();
            fixResponse.success = false;
        }
        return fixResponse;
    }

    /**
     * Returns an ticket from a trusted server
     *
     * @param nn  int that is the coin's Network Number
     * @param sn  int that is the coin's Serial Number
     * @param an String that is the coin's Authenticity Number (GUID)
     * @param d int that is the Denomination of the Coin
     * @return Response Object.
     */
    public CompletableFuture<Response> getTicket(int nn, int sn, String an, int d) {
        return CompletableFuture.supplyAsync(() -> {
            Response get_ticketResponse = new Response();
            get_ticketResponse.fullRequest = fullUrl + "get_ticket?nn=" + nn + "&sn=" + sn + "&an=" + an + "&pan=" + an + "&denomination=" + d;
            long before = System.currentTimeMillis();

            try {
                get_ticketResponse.fullResponse = Utils.getHtmlFromURL(get_ticketResponse.fullRequest);
                long after = System.currentTimeMillis();
                long ts = after - before;
                get_ticketResponse.milliseconds = (int) ts;

                TicketResponse response = Utils.createGson().fromJson(get_ticketResponse.fullResponse, TicketResponse.class);

                if (get_ticketResponse.fullResponse.contains("ticket")) {
                    ticket = get_ticketResponse.outcome = response.message;
                    get_ticketResponse.success = true;
                    hasTicket = true;
                    ticketHistory = TicketHistory.Success;
                } else {
                    get_ticketResponse.success = false;
                    hasTicket = false;
                    ticketHistory = TicketHistory.Failed;
                }

            } catch (Exception e) {
                e.printStackTrace();
                get_ticketResponse.outcome = "error";
                get_ticketResponse.fullResponse = e.getMessage();
                get_ticketResponse.success = false;
                hasTicket = false;
                ticketHistory = TicketHistory.Failed;
            }
            return get_ticketResponse;
        });
    }
}

