package se.aaslin.developer.roboproxy.policy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

public class RpcPolicyFinder {
	private static final String SEPARATOR = "/";
	private static final String TAG = RpcPolicyFinder.class.getSimpleName();
	private static final String GWT_PRC_POLICY_FILE_EXT = ".gwt.rpc";
	private static final Map<String, String> CACHE_POLICY_FILE = new HashMap<String, String>();

	public static String getCachedPolicyFile(String url) {
		return CACHE_POLICY_FILE.get(url);
	}

	public static Map<String, String> searchPolicyFilesInAssets(Context context, String path) {
		AssetManager assetManager = context.getAssets();
		try {
			List<String> gwtRPCPolicies = new ArrayList<String>();
			for (String file : assetManager.list(path)) {
				Log.i(TAG, file);
				if (file.endsWith(GWT_PRC_POLICY_FILE_EXT)) {
					gwtRPCPolicies.add(new StringBuilder().append(path).append(SEPARATOR).append(file).toString());
				}
			}

			Map<String, String> result = new HashMap<String, String>();
			Log.i(TAG, "searchPolicyFilesInAssets " + gwtRPCPolicies.toString());
			for (String gwtRPCPolicy : gwtRPCPolicies) {
				String policyName = gwtRPCPolicy.substring(0, gwtRPCPolicy.length() - GWT_PRC_POLICY_FILE_EXT.length());
				try {
					Log.i(TAG, policyName);
					result.putAll(parsePolicyName(policyName, assetManager.open(gwtRPCPolicy)));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return result;

		} catch (IOException e) {
			Log.w(TAG, "Could'nt extract any gwt rpc policies from /assets " + e.getMessage());
		}

		return null;
	}

	private static Map<String, String> parsePolicyName(String policyName, InputStream in) throws IOException {
		Map<String, String> result = new HashMap<String, String>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		String line = reader.readLine();
		while (line != null) {
			int pos = line.indexOf(", false, false, false, false, _, ");
			if (pos > 0) {
				result.put(line.substring(0, pos), policyName);
				result.put(line.substring(0, pos) + "Async", policyName);
			}
			line = reader.readLine();
		}

		return result;
	}

	/**
	 * Method to fetch serialization policy from server
	 * 
	 * @param moduleBaseURL
	 * @return
	 * @throws IOException
	 */
	public static Map<String, String> fetchSerializationPolicyName(String moduleBaseURL) throws IOException {
		Map<String, String> result = new HashMap<String, String>();

		moduleBaseURL = moduleBaseURL.trim(); // remove outer trim just in case
		String[] urlparts = moduleBaseURL.split(SEPARATOR);
		String moduleNoCacheJs = urlparts[urlparts.length - 1] + ".nocache.js";

		String responseText = "";
		responseText = getResposeText(moduleBaseURL + moduleNoCacheJs);
		// parse the .nocache.js for list of Permutation name
		// Permutation name is 32 chars surrounded by apostrophe
		String regex = "\'([A-Z0-9]){32}\'";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(responseText);
		// while (matcher.find())
		if (matcher.find()) {
			String permutationFile = matcher.group();
			permutationFile = permutationFile.replace("\'", "");

			// Load the first permutation html file
			permutationFile += ".cache.html";
			responseText = getResposeText(moduleBaseURL + permutationFile);
			matcher = pattern.matcher(responseText);
			int i = 0;
			while (matcher.find()) {
				String policyName = matcher.group();
				policyName = policyName.replace("\'", "");
				if (0 == i++) {
					// The first one is the permutation name
					continue;
				}
				responseText = getResposeText(moduleBaseURL + policyName + GWT_PRC_POLICY_FILE_EXT);
				result.putAll(parsePolicyName(policyName, new ByteArrayInputStream(responseText.getBytes("UTF8"))));
			}
		}

		if (result.size() == 0) {
			Log.w(TAG, "No RemoteService fetched from server");
		} else {
			dumpRemoteService(result);
		}

		return result;
	}

	private static String getResposeText(String myurl) throws IOException {
		URL url = new URL(myurl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setInstanceFollowRedirects(true); // follow redirect
		connection.setRequestMethod("GET");
		connection.connect();

		InputStream is = connection.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = is.read(buffer)) > 0) {
			baos.write(buffer, 0, len);
		}
		String responseText = baos.toString("UTF8");

		if (myurl.endsWith(GWT_PRC_POLICY_FILE_EXT)) {
			CACHE_POLICY_FILE.put(myurl, responseText);
		}

		return responseText;
	}

	private static void dumpRemoteService(Map<String, String> result) {
		if (result.size() > 0) {
			Log.i(TAG, "Found following RemoteService(s) in the classpath:");
			String s = "";
			for (String className : result.keySet()) {
				s += className + "\n";
			}
			Log.i(TAG, s);
		} else {
			Log.w(TAG, "No RemoteService in the result");
		}
	}
}
