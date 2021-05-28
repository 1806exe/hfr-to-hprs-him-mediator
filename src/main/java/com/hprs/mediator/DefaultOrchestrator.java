package com.hprs.mediator;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.gson.Gson;
import com.hprs.mediator.model.SourceMassage;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;


import java.nio.charset.StandardCharsets;
import java.util.HashMap;


public class DefaultOrchestrator extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;


    public DefaultOrchestrator(MediatorConfig config) {
        this.config = config;
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {

            // get object
            String body = ((MediatorHTTPRequest) msg).getBody();

            // cast object to Source Object
            SourceMassage sourceMassage = new Gson().fromJson(body, SourceMassage.class);
            //Deserialize the object back to JSON
            String convertedMessage = new Gson().toJson(sourceMassage);

            HashMap<String, String> header = new HashMap<>();
            header.put("Content-Type", "application/json");

            String uri = "";
            String username = "";
            String password = "";

            if (!config.getDynamicConfig().isEmpty()) {
                JSONObject connectionProperties =
                        new JSONObject(config.getDynamicConfig()).getJSONObject("hprs");

                uri = "" + connectionProperties.get("scheme") +
                        connectionProperties.get("host") +
                        ":" +
                        connectionProperties.get("port") +
                        connectionProperties.get("path");

                username = connectionProperties.getString("username");
                password = connectionProperties.getString("password");
            } else {
                uri = config.getProperty("hprs.scheme") +
                        config.getProperty("hprs.host") +
                        ":" +
                        config.getProperty("hprs.port") +
                        config.getProperty("hprs.path");

                username = config.getProperty("hprs.username");
                password = config.getProperty("hprs.password");
            }

            String credentials = username + ":" + password;
            byte[] encodedAuth = Base64.encodeBase64(credentials.getBytes(StandardCharsets.ISO_8859_1));
            String authHeader = "Basic " + new String(encodedAuth);
            header.put("Authorization", authHeader);

            MediatorHTTPRequest hprsRequest =
                    new MediatorHTTPRequest(
                            ((MediatorHTTPRequest) msg).getRequestHandler(),
                            getSelf(), "Sending data from Mediator to HPRS",
                            "POST",
                            uri,
                            convertedMessage,
                            header,
                            null);


            ActorSelection httpConnector = getContext()
                    .actorSelection(config.userPathFor("http-connector"));
            httpConnector.tell(hprsRequest, getSelf());
        } else if (msg instanceof MediatorHTTPResponse) {
            ((MediatorHTTPResponse) msg)
                    .getOriginalRequest()
                    .getRequestHandler()
                    .tell(((MediatorHTTPResponse) msg).toFinishRequest(), getSelf());

        } else {
            unhandled(msg);
        }
    }
}
