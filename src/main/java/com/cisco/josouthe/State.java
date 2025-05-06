package com.cisco.josouthe;

import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;

public class State {
    public Transaction transaction;
    public ExitCall exitCall;
    public boolean startedHere=false;
    public long creationTimestamp;
    public State(Transaction t, ExitCall e, boolean transactionStartedHere) {
        this.transaction=t;
        this.exitCall=e;
        this.startedHere=transactionStartedHere;
        this.creationTimestamp = System.currentTimeMillis();
    }
}
