package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.ExitTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.agent.api.impl.NoOpTransaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectOutboundInterceptor extends MyBaseInterceptor {

    IReflector toStringR, getQueueManagerNameR, getHostnameR, getPortR, getChannelR, getLocalAddressR, getConnectionNameR, getRemoteApplicationTagR; //WMQConnectionData
    public ConnectOutboundInterceptor() {
        getLogger().info(String.format("Initializing IBM MQ FTE ConnectOutboundInterceptor for ISO8583 Messages"));

        toStringR = makeInvokeInstanceMethodReflector("toString"); //String
        getQueueManagerNameR = makeInvokeInstanceMethodReflector("getQueueManagerName"); //String
        getHostnameR = makeInvokeInstanceMethodReflector("getHostname"); //String
        getPortR = makeInvokeInstanceMethodReflector("getPort"); //Int
        getChannelR = makeInvokeInstanceMethodReflector("getChannel"); //String
        getLocalAddressR = makeInvokeInstanceMethodReflector("getLocalAddress"); //String
        getConnectionNameR = makeInvokeInstanceMethodReflector("getConnectionName"); //String
        getRemoteApplicationTagR = makeInvokeInstanceMethodReflector("getRemoteApplicationTag"); //String
    }

    @Override
    public Object onMethodBegin (Object objectIntercepted, String className, String methodName, Object[] params) {
        Transaction transaction = AppdynamicsAgent.getTransaction();
        boolean transactionStartedHere = false;
        if( transaction instanceof NoOpTransaction) {
            //return null;
            transaction = AppdynamicsAgent.startTransaction("IBM MQ FTE-Placeholder", null, EntryTypes.POJO, true);
            transactionStartedHere = true;
            getLogger().debug("WARNING, no transaction active, but backend called");
        }
        Object wmqConnectionData = params[0];
        String connTag = (String) params[2];
        getLogger().debug(String.format("Connection Tag: '%s' Connection Data: '%s'", connTag, getReflectiveString(wmqConnectionData, toStringR, "None")));
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put("Queue Manager Name", getReflectiveString(wmqConnectionData, getQueueManagerNameR, "Unknown"));
        propertyMap.put("Remote Host", getReflectiveString(wmqConnectionData, getHostnameR, "Unknown"));
        propertyMap.put("Remote Port", String.valueOf(getReflectiveInteger(wmqConnectionData, getPortR, -1)));
        propertyMap.put("Local Address", getReflectiveString(wmqConnectionData, getLocalAddressR, "Unknown"));
        propertyMap.put("Connection Name", getReflectiveString(wmqConnectionData, getConnectionNameR, "Unknown"));
        propertyMap.put("Remote Application Tag", getReflectiveString(wmqConnectionData, getRemoteApplicationTagR, "Unknown"));
        ExitCall exitCall = null; //transaction.startExitCall( propertyMap, String.format("IBM-MQ-message"), ExitTypes.CUSTOM, true);
        return new State(transaction, exitCall, transactionStartedHere);
    }

    @Override
    public void onMethodEnd (Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return;
        State s = (State) state;
        Object channelFuture = returnVal;
        if( exception != null ) {
            s.transaction.markAsError(String.format("Exit Call threw Exception: '%s' ", exception.toString()));
        }
        if( s.exitCall != null )
            s.exitCall.end();
        if( s.startedHere ) s.transaction.end();
    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.ibm.wmqfte.wmqiface.WMQApiImpl")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("connect")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        return rules;
    }
}
