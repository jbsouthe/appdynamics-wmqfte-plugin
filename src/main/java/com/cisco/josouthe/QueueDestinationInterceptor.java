package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.agent.api.impl.NoOpTransaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.List;

public class QueueDestinationInterceptor extends MyBaseInterceptor{

    IReflector getNameR, getMQMDR; //WMQMessage
    IReflector getStringPropertyR; //com.ibm.mq.MQMessage


    public QueueDestinationInterceptor() {

        getNameR = makeInvokeInstanceMethodReflector("getName");
        getMQMDR = makeInvokeInstanceMethodReflector("getMQDR");

        getStringPropertyR = makeInvokeInstanceMethodReflector(" getStringProperty", "java.lang.String");
    }

    @Override
    public Object onMethodBegin (Object objectIntercepted, String className, String methodName, Object[] params) {
        Object mqMessage = params[0];
        Transaction transaction = AppdynamicsAgent.getTransaction();
        boolean transactionStartedHere = false;
        if( transaction instanceof NoOpTransaction) {
            //return null;
            transaction = AppdynamicsAgent.startTransaction(String.format("IBM MQ FTE %s %s", methodName, getName(objectIntercepted)), getCorrelationHeader(mqMessage), EntryTypes.POJO, true);
            transactionStartedHere = true;
            getLogger().debug("WARNING, no transaction active, but backend called");
        }
        return new State(transaction, null, transactionStartedHere);
    }

    private String getCorrelationHeader (Object wMQMessage) {
        if(wMQMessage == null) return null;
        Object mqmd = getReflectiveObject(wMQMessage, getMQMDR);
        //we are going to hope this is actually an com.ibm.mq.MQMessage for this next operation
        String correlationHeader = (String) getReflectiveObject(mqmd, getStringPropertyR, AppdynamicsAgent.TRANSACTION_CORRELATION_HEADER);
        getLogger().info(String.format("Correlation Header: %s", correlationHeader));
        return correlationHeader;
    }

    private String getName( Object object ) {
        return getReflectiveString(object, getNameR, "NoName");
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
                "com.ibm.wmqfte.wmqiface.WMQQueue")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("get")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        rules.add(new Rule.Builder(
                "com.ibm.wmqfte.wmqiface.WMQQueue")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("put")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        return rules;
    }
}
