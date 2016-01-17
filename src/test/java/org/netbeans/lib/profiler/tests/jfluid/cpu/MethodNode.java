package org.netbeans.lib.profiler.tests.jfluid.cpu;

import java.util.ArrayList;
import java.util.Collection;

/**
 *
 */
public class MethodNode {
    private String name;
    private double time;
    private float percent;
    private double selfTime = -1;
    private float selfPercent = -1;
    protected int invocation;
    private Collection<MethodNode> child;

    public MethodNode() {
    }

    public String getName() {
        return name;
    }

    public double getTime() {
        return time;
    }

    public float getPercent() {
        return percent;
    }

    public double getSelfTime() {
        return selfTime;
    }

    public float getSelfPercent() {
        return selfPercent;
    }

    public int getInvocation() {
        return invocation;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public void setPercent(float percent) {
        this.percent = percent;
    }

    public void setInvocation(int invocation) {
        this.invocation = invocation;
    }

    public void setSelfTime(double selfTime) {
        this.selfTime = selfTime;
    }

    public void setSelfPercent(float selfPercent) {
        this.selfPercent = selfPercent;
    }

    public Collection<MethodNode> getChild() {
        if(child==null){
            child = new ArrayList<MethodNode>();
        }
        return child;
    }
}
