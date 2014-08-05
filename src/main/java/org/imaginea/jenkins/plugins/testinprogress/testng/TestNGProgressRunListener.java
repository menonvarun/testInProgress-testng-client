package org.imaginea.jenkins.plugins.testinprogress.testng;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.jenkinsci.testinprogress.messagesender.IMessageSenderFactory;
import org.jenkinsci.testinprogress.messagesender.MessageSender;
import org.jenkinsci.testinprogress.messagesender.SocketMessageSenderFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.ConstructorOrMethod;
/**
 * Using ITestListener implementation of TestNg to implement the logic of test-In-progress jenkins plugin.
 * 
 * @author  Varun Menon (github id: menonvarun)
 * 
 */
public class TestNGProgressRunListener implements ITestListener {
	private Map<String, String> testKIds = new HashMap<String, String>();
	private AtomicLong atomicLong = new AtomicLong(0);

	private MessageSender messageSender;
	private IMessageSenderFactory messageSenderFactory = new SocketMessageSenderFactory();
	private long startTime;


	private void sendTestTree(Map<String, ArrayList<String>> classMap, ITestContext context) {
		String suiteName = context.getSuite().getName();
		String xmlTestName = context.getCurrentXmlTest().getName();
		//String parentName = suiteName + ":" + xmlTestName;
		String runId = getRunId(context);
		Iterator<Entry<String, ArrayList<String>>> it = classMap.entrySet()
				.iterator();

		while (it.hasNext()) {
			Entry<String, ArrayList<String>> entry = it.next();
			String className = entry.getKey();

			String clssTreedIdName = runId +":"+className;
			
			String classTestId = getTestId(clssTreedIdName);
			ArrayList<String> methods = entry.getValue();
			int classChilds = methods.size();
			
			messageSender.testTree(classTestId, className, getTestId(runId), xmlTestName, true, classChilds, runId);

			for (String method : methods) {
				String methodKey = method + "(" + className + ")";
				
				String mthdTreedIdName = runId +":"+methodKey;
				String mthdTestId = getTestId(mthdTreedIdName);
				
				messageSender.testTree(mthdTestId, methodKey, classTestId, className, false, 1, runId );
			}
		}

	}

	private String getTestId(String key) {
		String test = testKIds.get(key);
		if (test == null) {
			test = Long.toString(atomicLong.incrementAndGet());
			testKIds.put(key, test);
		}
		return test;
	}

	public void onTestSuccess(ITestResult result) {
		ITestNGMethod testMethod = result.getMethod();
		String testId = getIdForMethod(result.getTestContext(), testMethod);
		String mthdKey = getMessageSenderNameForMethod(testMethod);

		messageSender.testEnded(testId, mthdKey, false, getRunId(result));
	}

	private String getIdForMethod(ITestContext context, ITestNGMethod testMethod) {
		String methodKey = getMessageSenderNameForMethod(testMethod);
		String testMethodContextKey = getRunId(context)+":"+methodKey;
		String mthdTestId = getTestId(testMethodContextKey);

		return mthdTestId;
	}

	private String getMessageSenderNameForMethod(ITestNGMethod testMethod) {
		ConstructorOrMethod consMethod = testMethod.getConstructorOrMethod();
		String methodName = consMethod.getName();
		String className = consMethod.getDeclaringClass().getName();
		
		String methodKey = methodName + "(" + className + ")";
		return methodKey;
	}

	public void onTestFailure(ITestResult result) {
		ITestNGMethod testMethod = result.getMethod();
		String testId = getIdForMethod(result.getTestContext(), testMethod);
		String mthdKey = getMessageSenderNameForMethod(testMethod);

		String trace = getTrace(result.getThrowable());
		messageSender.testError(testId, mthdKey, trace, getRunId(result));
	}

	public void onTestSkipped(ITestResult result) {

		ITestNGMethod testMethod = result.getMethod();
		String testId = getIdForMethod(result.getTestContext(), testMethod);
		String mthdKey = getMessageSenderNameForMethod(testMethod);

		messageSender.testStarted(testId, mthdKey, true, getRunId(result));
		messageSender.testEnded(testId, mthdKey, true, getRunId(result));

	}

	public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
		// TODO Auto-generated method stub

	}

	public void onStart(ITestContext context) {
		
		messageSender = messageSenderFactory.getMessageSender();

		try {
			messageSender.init();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		String parentName = getRunId(context);
		String runId = parentName;
		
		messageSender.testRunStarted(context.getAllTestMethods().length, runId);
		
		Map<String, ArrayList<String>> classMap = processTestContext(context);
		
		String testId = getTestId(parentName);
		messageSender.testTree(testId, context.getCurrentXmlTest().getName(), true, classMap.keySet()
				.size(), runId);
		sendTestTree(classMap,context);
		startTime = System.currentTimeMillis();

	}

	public void onFinish(ITestContext context) {

		long stopTime = System.currentTimeMillis();
		messageSender.testRunEnded(stopTime - startTime, getRunId(context));
		try {
			messageSender.shutdown();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	/**
	 * Gets the run id based on the suite and current xml test name.
	 * @param context
	 * @return
	 */
	private String getRunId(ITestContext context){
		String suiteName = context.getSuite().getName();
		String xmlTestName = context.getCurrentXmlTest().getName();
		
		if ((xmlTestName == null) || ("".equalsIgnoreCase(xmlTestName))) {
			xmlTestName = "Testng xml test";
		}
		String parentName = suiteName + "-" + xmlTestName;
		
		return parentName;		
	}
	
	private String getRunId(ITestResult result){
		return getRunId(result.getTestContext());
	}

	private String getTrace(Throwable t) {
		StringWriter stringWriter = new StringWriter();
		PrintWriter writer = new PrintWriter(stringWriter);
		t.printStackTrace(writer);
		StringBuffer buffer = stringWriter.getBuffer();
		return buffer.toString();
	}


	private Map<String, ArrayList<String>> processTestContext(
			ITestContext context) {

		Map<String, ArrayList<String>> classMap = new HashMap<String, ArrayList<String>>();
		Collection<ITestNGMethod> testMethods = Arrays.asList(context.getAllTestMethods());

		for (ITestNGMethod testMethod : testMethods) {
			ConstructorOrMethod consMethod = testMethod.getConstructorOrMethod();
			String methodName = consMethod.getName();
			String className = consMethod.getDeclaringClass().getName();
			ArrayList<String> methodList;
			if (!classMap.containsKey(className)) {
				methodList = new ArrayList<String>();
			} else {
				methodList = classMap.get(className);
			}
			methodList.add(methodName);
			classMap.put(className, methodList);
		}
		return classMap;
	}

	public void onTestStart(ITestResult result) {
		ITestNGMethod testMethod = result.getMethod();
		String testId = getIdForMethod(result.getTestContext(), testMethod);
		String mthdKey = getMessageSenderNameForMethod(testMethod);

		messageSender.testStarted(testId, mthdKey, false, getRunId(result));

	}

}
