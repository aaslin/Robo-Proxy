package se.aaslin.developer.roboproxy;

import java.lang.reflect.Proxy;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Map;

import se.aaslin.developer.roboproxy.policy.RpcPolicyFinder;
import se.aaslin.developer.roboproxy.remote.RemoteServiceInvocationHandler;

import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import android.content.Context;


public class RoboProxy {

	private static final CookieManager DEFAULT_COOKIE_MANAGER = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	
	public static Object newProxyInstance(Class<?> serviceIntf, String moduleBaseURL, Context context) {
		RemoteServiceRelativePath relativePathAnn = extractRemoteServiceRelativePath(serviceIntf);
		String remoteServiceRelativePath = relativePathAnn.value();
		return newProxyInstance(serviceIntf, moduleBaseURL, remoteServiceRelativePath, context);
	}
	
	protected static Object newProxyInstance(Class<?> serviceIntf, String moduleBaseURL, String remoteServiceRelativePath, Context context){
		Map<String, String> policyMap = RpcPolicyFinder.searchPolicyFilesInAssets(context, "gwt.rpc.policies");
		return newProxyInstance(serviceIntf, moduleBaseURL, remoteServiceRelativePath, policyMap.get(serviceIntf.getName()), DEFAULT_COOKIE_MANAGER, context);
	}

	protected static Object newProxyInstance(Class<?> serviceIntf, String moduleBaseURL, String remoteServiceRelativePath, String policyName,
			CookieManager cookieManager, Context context) {
		if (cookieManager == null) {
			cookieManager = DEFAULT_COOKIE_MANAGER;
		}

		//TODO implement fetch mechanism
//		if (policyName == null) {
//			
//			try {
//				POLICY_MAP.putAll(RpcPolicyFinder.fetchSerializationPolicyName(moduleBaseURL));
//				policyName = POLICY_MAP.get(serviceIntf.getName());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}

		return Proxy.newProxyInstance(RoboProxy.class.getClassLoader(), new Class[] { serviceIntf }, new RemoteServiceInvocationHandler(moduleBaseURL,
				remoteServiceRelativePath, policyName, cookieManager, context));
	}

	private static RemoteServiceRelativePath extractRemoteServiceRelativePath(Class<?> serviceIntf) {
		RemoteServiceRelativePath relativePathAnn = (RemoteServiceRelativePath) serviceIntf.getAnnotation(RemoteServiceRelativePath.class);
		if (serviceIntf.getName().endsWith("Async")) {
			String className = serviceIntf.getName();
			try {
				Class<?> clazz = Class.forName(className.substring(0, className.length() - 5));
				relativePathAnn = (RemoteServiceRelativePath) clazz.getAnnotation(RemoteServiceRelativePath.class);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		if (relativePathAnn == null) {
			throw new RuntimeException(serviceIntf + " does not has a RemoteServiceRelativePath annotation");
		}
		return relativePathAnn;
	}
}
