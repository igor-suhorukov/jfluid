/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

/*
 * CPUSnapshotTestCase.java
 *
 * Created on July 19, 2005, 5:20 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package org.netbeans.lib.profiler.tests.jfluid.cpu;

import org.netbeans.lib.profiler.ProfilerClient;
import org.netbeans.lib.profiler.ProfilerEngineSettings;
import org.netbeans.lib.profiler.TargetAppRunner;
import org.netbeans.lib.profiler.client.ClientUtils;
import org.netbeans.lib.profiler.client.MonitoredData;
import org.netbeans.lib.profiler.global.CommonConstants;
import org.netbeans.lib.profiler.results.CCTNode;
import org.netbeans.lib.profiler.results.EventBufferResultsProvider;
import org.netbeans.lib.profiler.results.ProfilingResultsDispatcher;
import org.netbeans.lib.profiler.results.RuntimeCCTNode;
import org.netbeans.lib.profiler.results.cpu.*;
import org.netbeans.lib.profiler.tests.jfluid.*;
import org.netbeans.lib.profiler.tests.jfluid.utils.TestProfilerAppHandler;
import org.netbeans.lib.profiler.tests.jfluid.utils.TestProfilingPointsProcessor;
import org.netbeans.lib.profiler.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.concurrent.TimeUnit;


/**
 *
 * @author ehucka
 */
public abstract class CPUSnapshotTestCase extends CommonProfilerTestCase {
    private TargetAppRunner runner;

    //~ Inner Classes ------------------------------------------------------------------------------------------------------------

    private class CPUResultListener implements CPUCCTProvider.Listener {
        //~ Instance fields ------------------------------------------------------------------------------------------------------

        private final Object resultsLock = new Object();
        private boolean hasResults = false;

        //~ Methods --------------------------------------------------------------------------------------------------------------

        public void cctEstablished(RuntimeCCTNode appRootNode) {
            log("CCT Results established");

            synchronized (resultsLock) {
                hasResults = true;
                resultsLock.notify();
            }
        }

        public void cctReset() {
            log("CCT Results reset");

            synchronized (resultsLock) {
                hasResults = false;
                resultsLock.notify();
            }
        }

        public boolean wait4results(long timeout) {
            synchronized (resultsLock) {
                if (!hasResults) {
                    try {
                        resultsLock.wait(timeout);
                    } catch (InterruptedException e) {
                    }
                }

                return hasResults;
            }
        }

        public void cctEstablished(RuntimeCCTNode appRootNode, boolean empty) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance of CPUSnapshotTestCase
     */
    public CPUSnapshotTestCase(String name) {
        super(name);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    protected boolean checkSerialization(CPUResultsSnapshot snapshot) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            snapshot.writeToStream(dos);
            dos.close();

            byte[] bytes = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            CPUResultsSnapshot snapshot2 = new CPUResultsSnapshot();
            snapshot2.readFromStream(dis);
            dis.close();

            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);
            snapshot2.writeToStream(dos);
            dos.close();

            byte[] bytes2 = baos.toByteArray();

            //compare
            if (bytes.length != bytes2.length) {
                return false;
            }

            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] != bytes2[i]) {
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }

    protected void checkSumsOfCCTNodes(PrestimeCPUCCTNode node, String pre, double tolerance, String[] filterout, int level) {
        log(complete(pre + node.getNodeName(), 62) + complete(StringUtils.mcsTimeToString(node.getTotalTime0()), 9) + " ms   ("
            + complete(StringUtils.floatPerCentToString(node.getTotalTime0InPerCent()), 7) + " %)  "
            + complete(String.valueOf(node.getNCalls()), 3));

        boolean exclude = false;

        if (filterout != null) {
            for (int i = 0; i < filterout.length; i++) {
                if (node.getNodeName().startsWith(filterout[i])) {
                    exclude = true;

                    break;
                }
            }
        }

        if (!exclude) {
            long time = 0;
            float percent = 0.0f;

            for (int i = 0; i < node.getNChildren(); i++) {
                PrestimeCPUCCTNode pnode = (PrestimeCPUCCTNode) (node.getChild(i));
                checkSumsOfCCTNodes(pnode, pre + " ", tolerance, filterout, level + 1);
                time += pnode.getTotalTime0();
                percent += pnode.getTotalTime0InPerCent();
            }

            if ((level > 1) && (node.getNChildren() > 0)) {
                double timediff = (Math.abs(time - node.getTotalTime0()) * 100.0) / node.getTotalTime0();
                double percentdiff = (Math.abs(percent - node.getTotalTime0InPerCent()) * 100.0) / node.getTotalTime0InPerCent();

                if ((timediff > tolerance) || (percentdiff > tolerance)) {
                    log("Node : " + node.getNodeName());
                    log("Time diff: " + timediff + " %");
                    log("Percent diff: " + percentdiff + " %");
                    assertTrue("Node's and sum of subnodes values differ", false);
                }
            }
        }
    }

    protected int[] findThreadAndMethod(PrestimeCPUCCTNode node, String[] method, String[] filterout) {
        boolean exclude = false;

        if (filterout != null) {
            for (int i = 0; i < filterout.length; i++) {
                if (node.getNodeName().startsWith(filterout[i])) {
                    exclude = true;

                    break;
                }
            }
        }

        if (!exclude) {
            String[] nmethod = node.getMethodClassNameAndSig();
            boolean found = true;

            for (int i = 0; i < nmethod.length; i++) {
                if (!nmethod[i].equals(method[i])) {
                    found = false;
                }
            }

            if (found) {
                return new int[] { node.getThreadId(), node.getMethodId() };
            }

            for (int i = 0; i < node.getNChildren(); i++) {
                PrestimeCPUCCTNode pnode = (PrestimeCPUCCTNode) (node.getChild(i));
                int[] ret = findThreadAndMethod(pnode, method, filterout);

                if (ret != null) {
                    return ret;
                }
            }
        }

        return null;
    }

    protected ProfilerEngineSettings initSnapshotTest(String projectName, String mainClass, String[][] rootMethods) {
        ProfilerEngineSettings settings = initTest(projectName, mainClass, rootMethods);
        //defaults
        settings.setInstrScheme(CommonConstants.INSTRSCHEME_TOTAL);
        settings.setInstrumentEmptyMethods(false);
        settings.setInstrumentGetterSetterMethods(false);
        settings.setInstrumentMethodInvoke(true);
        settings.setInstrumentSpawnedThreads(true);
        settings.setExcludeWaitTime(true);
        //addJVMArgs(settings, "-Dorg.netbeans.lib.profiler.wireprotocol.WireIO=true");
        settings.setThreadCPUTimerOn(false);
        settings.setCPUProfilingType(CommonConstants.CPU_INSTR_FULL);

        return settings;
    }

    protected void refOfCCTNodes(PrestimeCPUCCTNode node, String pre, boolean time, boolean percent, boolean invocations, MethodNode methodNode){
        double totalTime = getCalculateNodeTime(node);
/*
        ref(complete(pre + node.getNodeName(), 200)
                + ((!time) ? "" : (complete(String.valueOf(totalTime), 9) + " ms   "))
                + ((!percent) ? "" : (complete(String.valueOf(node.getTotalTime0InPerCent()), 7) + " %  "))
                + ((!invocations) ? "" : complete(String.valueOf(node.getNCalls()), 3)));
*/
        methodNode.setName(node.getNodeName());
        methodNode.setTime(totalTime);
        methodNode.setPercent(node.getTotalTime0InPerCent());
        methodNode.setInvocation(node.getNCalls());
        for (int i = 0; i < node.getNChildren(); i++) {
            PrestimeCPUCCTNode pnode = (PrestimeCPUCCTNode) node.getChild(i);
            if("Self time".equals(pnode.getNodeName())){
                methodNode.setSelfTime(getCalculateNodeTime(pnode));
                methodNode.setSelfPercent(pnode.getTotalTime0InPerCent());
                continue;
            }
            MethodNode methodNodeChild = new MethodNode();
            methodNode.getChild().add(methodNodeChild);
            refOfCCTNodes(pnode, pre + " ", time, percent, invocations, methodNodeChild);
        }
    }

    private double getCalculateNodeTime(PrestimeCPUCCTNode node) {
        return node.getTotalTime0() / 1000.0;
    }
    //-Dplatform.home=....
    protected void startSnapshotTest(ProfilerEngineSettings settings, String[] reverseMethod, long initDelay, double diffPercent,
                                     String[] filterout) {
        CPUCallGraphBuilder builder = new CPUCallGraphBuilder();

        TestProfilerAppHandler testProfilerAppHandler = new TestProfilerAppHandler(this);
        runner = new TargetAppRunner(settings, testProfilerAppHandler,
                                                     new TestProfilingPointsProcessor());
        runner.addProfilingEventListener(org.netbeans.lib.profiler.tests.jfluid.utils.Utils.createProfilingListener(this));

        builder.removeAllListeners();
        ProfilingResultsDispatcher.getDefault().removeAllListeners();

        FlatProfileBuilder flattener = new FlatProfileBuilder();
        builder.addListener(flattener);
        flattener.setContext(runner.getProfilerClient(), null, null);

        ProfilingResultsDispatcher.getDefault().addListener(builder);

        builder.startup(runner.getProfilerClient());

        try {
            runner.readSavedCalibrationData();
            Process p = startTargetVM(runner);
            Thread.sleep(500);
            boolean step1 = runner.initiateSession(2, false);
            Thread.sleep(500);
            boolean step2 = runner.attachToTargetVM();
            assertNotNull("Target JVM is not started", p);
            bindStreams(p);
            boolean success = step1 && step2;
            System.out.println(success);
            runner.getProfilerClient().initiateRecursiveCPUProfInstrumentation(new ClientUtils.SourceCodeSelection[]{new ClientUtils.SourceCodeSelection("*","*","")}/*);settings.getInstrumentationRootMethods()*/);
            Thread.sleep(500);
            runner.resumeTargetAppIfSuspended();
            //waitForStatus(STATUS_RESULTS_AVAILABLE);
            while (runner.targetJVMIsAlive() && !isStatus(STATUS_RESULTS_AVAILABLE) && !isStatus(STATUS_ERROR)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                runner.resumeTargetAppIfSuspended();
            }

            int total = 0;
            while (runner.targetJVMIsAlive()) {
                if(total>10) break;
                Thread.sleep(100);
                try {
                    if(runner.getProfilerClient().cpuResultsExist()){

                        CPUResultsSnapshot snapshot = runner.getProfilerClient().getCPUProfilingResultsSnapshot();
                        if(snapshot == null) continue;

                        PrestimeCPUCCTNode root = snapshot.getRootNode(CPUResultsSnapshot.METHOD_LEVEL_VIEW);

                        if(root == null) continue;

                        MethodNode methodNode = new MethodNode();
                        refOfCCTNodes(root, "", true, true, true, methodNode);
                        total++;
                        System.out.println("get");
                        runner.getProfilerClient().resetProfilerCollectors();
                    }
                } catch (ClientUtils.TargetAppOrVMTerminated targetAppOrVMTerminated) {
                    targetAppOrVMTerminated.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    break;
                } catch (CPUResultsSnapshot.NoDataAvailableException e) {
                    System.out.println("not available");
                    continue;
                } catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
            setStatus(STATUS_MEASURED);
        } catch (Exception ex) {
            ex.printStackTrace();
            log(ex);
            assertTrue("Exception thrown: " + ex.getMessage(), false);
        } finally {
            try {
                runner.getProfilerClient().prepareDetachFromTargetJVM();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                runner.getProfilerClient().detachFromTargetJVM();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            ProfilingResultsDispatcher.getDefault().pause(true);
            builder.shutdown();
            flattener.setContext(null, null, null);
            builder.removeListener(flattener);
            //builder.removeListener(resultListener);
            ProfilingResultsDispatcher.getDefault().removeListener(builder);
            finalizeTest(runner);
        }
    }
}
