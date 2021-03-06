/**
 * Copyright (C) 2015 Stubhub.
 */

package io.bigdime.libs.hdfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author adwang, Neeraj Jain, mnamburi
 *
 */
public class WebHdfs {
	private static Logger logger = LoggerFactory.getLogger(WebHdfs.class);
	private String host = null;
	private int port = 0;
	private HttpClient httpClient = null;
	private URI uri = null;
	private HttpRequestBase httpRequest = null;
	private ObjectNode jsonParameters = null;
	// private final String SANDBOX = "sandbox";
	// private final String SANDBOX_HDP = "sandbox.hortonworks.com";
	private RoundRobinStrategy roundRobinStrategy = RoundRobinStrategy.getInstance();
	private List<Header> headers;

	public WebHdfs setParameters(ObjectNode jsonParameters) {
		Iterator<String> keys = jsonParameters.getFieldNames();
		while (keys.hasNext()) {
			String key = keys.next();
			JsonNode value = jsonParameters.get(key);
			if (value.getTextValue() != null) {
				this.jsonParameters.put(key, value.getTextValue());
			}
		}
		return this;
	}

	/**
	 * HDP 2.0 Content type : Content-Type : application/octet-stream
	 * 
	 * @param headers
	 * @return
	 */
	public WebHdfs addHeaders(List<Header> headers) {
		this.headers = headers;
		return this;
	}

	/**
	 * HDP 2.0 Content type : Content-Type : application/octet-stream
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public WebHdfs addHeader(String key, String value) {
		if (headers == null) {
			headers = new ArrayList<Header>();
		}
		headers.add(new BasicHeader(key, value));
		return this;
	}

	public ObjectNode getJson() {
		return jsonParameters;
	}

	public WebHdfs addParameter(String key, String value) {
		jsonParameters.put(key, value);
		return this;
	}

	protected WebHdfs(String host, int port) {
		this.host = host;
		this.port = port;
		this.httpClient = HttpClientBuilder.create().build();// new
		// DefaultHttpClient();
		// httpClient.clearResponseInterceptors();
		ObjectMapper mapper = new ObjectMapper();
		this.jsonParameters = mapper.createObjectNode();
		roundRobinStrategy.setHosts(host);
	}

	public static WebHdfs getInstance(String host, int port) {
		return new WebHdfs(host, port);
	}

	public String getHost() {
		return roundRobinStrategy.getNextServiceHost();
	}

	// field checking?
	public WebHdfs buildURI(String op, String HdfsPath) {
		URIBuilder uriBuilder = new URIBuilder();
		uriBuilder.setScheme("http").setHost(roundRobinStrategy.getNextServiceHost()).setPort(this.port)
				.setPath(HdfsPath).addParameter("op", op);
		Iterator<String> keys = jsonParameters.getFieldNames();
		while (keys.hasNext()) {
			String key = keys.next();
			JsonNode value = jsonParameters.get(key);
			String valueStr = value.getTextValue();
			if (valueStr != null) {
				uriBuilder.addParameter(key, valueStr);
			}
		}
		//jsonParameters.removeAll();
		try {
			this.uri = uriBuilder.build();
		} catch (URISyntaxException e) {
			/* this shouldn't occur */
			logger.info("URI Syntax Error:" + e.getMessage());
		}
		return this;
	}

	// MKDIR, RENAME
	private HttpResponse put() throws ClientProtocolException, IOException {
		httpRequest = new HttpPut(uri);
		uri = null;
		logger.debug("First curl in put(): " + httpRequest.getRequestLine());
		return httpClient.execute(httpRequest);
	}

	// CREATE
	private HttpResponse put(String filePath) throws ClientProtocolException, IOException {
		HttpPut httpRequest1 = new HttpPut(uri);
		uri = null;
		logger.debug("First curl: " + httpRequest1.getRequestLine());
		HttpPut httpPut = new HttpPut(temporaryRedirectURI(httpRequest1));
		if (headers != null) {
			for (Header header : headers) {
				httpPut.addHeader(header);
			}
		}
		FileEntity entity = new FileEntity(new File(filePath));
		httpPut.setEntity(entity);
		httpRequest = httpPut;
		logger.debug("Second curl: " + httpRequest.getRequestLine());
		return httpClient.execute(httpRequest);
	}

	// CREATE
	private HttpResponse put(InputStream in) throws ClientProtocolException, IOException {
		HttpPut httpRequest1 = new HttpPut(uri);
		uri = null;
		logger.debug("First curl: " + httpRequest1.getRequestLine());
		HttpPut httpPut = new HttpPut(temporaryRedirectURI(httpRequest1));
		if (headers != null) {
			for (Header header : headers) {
				httpPut.addHeader(header);
			}
		}
		BasicHttpEntity entity = new BasicHttpEntity();
		entity.setContent(in);
		httpPut.setEntity(entity);
		httpRequest = httpPut;
		logger.debug("Second curl: " + httpRequest.getRequestLine());
		return httpClient.execute(httpPut);
	}

	// APPEND
	private HttpResponse post(String filePath) throws ClientProtocolException, IOException {
		HttpPost httpRequest1 = new HttpPost(uri);
		uri = null;
		logger.debug("First curl: " + httpRequest1.getRequestLine());
		HttpPost httpPost = new HttpPost(temporaryRedirectURI(httpRequest1));
		if (headers != null) {
			for (Header header : headers) {
				httpPost.addHeader(header);
			}
		}
		FileEntity entity = new FileEntity(new File(filePath));
		httpPost.setEntity(entity);
		httpRequest = httpPost;
		logger.debug("Second curl: " + httpRequest.getRequestLine());
		return httpClient.execute(httpPost);
	}

	// APPEND
	private HttpResponse post(InputStream in) throws ClientProtocolException, IOException {
		HttpPost httpRequest1 = new HttpPost(uri);
		uri = null;
		logger.debug("First curl: " + httpRequest1.getRequestLine());
		HttpPost httpPost = new HttpPost(temporaryRedirectURI(httpRequest1));
		if (headers != null) {
			for (Header header : headers) {
				httpPost.addHeader(header);
			}
		}
		BasicHttpEntity entity = new BasicHttpEntity();
		entity.setContent(in);
		httpPost.setEntity(entity);
		httpRequest = httpPost;
		logger.debug("Second curl: " + httpRequest.getRequestLine());

		return httpClient.execute(httpRequest);
	}

	/*
	 * Method used to get first redirect when doing a post or put " Note that
	 * the reason of having two-step create/append is for preventing clients to
	 * send out data before the redirect. This issue is addressed by the
	 * "Expect: 100-continue" header in HTTP/1.1; see RFC 2616, Section 8.2.3.
	 * Unfortunately, there are software library bugs (e.g. Jetty 6 HTTP server
	 * and Java 6 HTTP client), which do not correctly implement
	 * "Expect: 100-continue". The two-step create/append is a temporary
	 * workaround for the software library bugs." MAPR does not need redirect
	 * for post and puts.
	 */
	// Need to check for permission!
	// TODO: pass in a method parameter: e.g.
	private String temporaryRedirectURI(HttpRequestBase request) throws ClientProtocolException, IOException {
		HttpResponse response = httpClient.execute(request);
		request.releaseConnection();
		Header[] headers = response.getAllHeaders();
		String redirectLocation = null;
		for (Header h : headers) {
			// TODO use a method parameter here.
			if (h.getName().equals("Location")) {
				redirectLocation = h.getValue();
				break;
			}
		}
		return redirectLocation;
	}

	// LISTSTATUS, OPEN, GETFILESTATUS, GETCHECKSUM,
	private HttpResponse get() throws ClientProtocolException, IOException {
		httpRequest = new HttpGet(uri);
		logger.debug("File status request: {}", httpRequest.getURI());
		uri = null;
		return httpClient.execute(httpRequest);
	}

	private HttpResponse delete() throws ClientProtocolException, IOException {
		httpRequest = new HttpDelete(uri);
		logger.info("Deleting resource: " + uri);
		uri = null;
		return httpClient.execute(httpRequest);
	}

	public static void printResponseStatus(HttpResponse response) {
		logger.info("Response is: " + response.toString());
		logger.info("Response headers are: ");
		Header[] headers = response.getAllHeaders();
		boolean isJsonContent = false;
		for (Header h : headers) {
			if (h.getName().equals("Content-Type") && h.getValue().equals("application/json")) {
				isJsonContent = true;
			}
			logger.info("   " + h.toString());
		}
		logger.info("Attached Stream: ");
		InputStream responseStream;
		try {
			if (isJsonContent) {
				printJsonResponse(response);
			} else {
				responseStream = response.getEntity().getContent();
				StringWriter writer = new StringWriter();
				IOUtils.copy(responseStream, writer);
				String theString = writer.toString();
				logger.info("   " + theString);
			}
		} catch (Exception e) {
			// DO NOTHING
		}
	}

	public static void printJsonResponse(HttpResponse response) throws IllegalStateException, IOException {
		InputStream is = response.getEntity().getContent();
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonObj = mapper.readTree(is);
		ObjectWriter ow = mapper.writerWithDefaultPrettyPrinter();
		logger.info(ow.writeValueAsString(jsonObj));
		is.close();
	}

	public void releaseConnection() {
		if (httpRequest != null) {
			this.httpRequest.releaseConnection();
			httpRequest = null;
		}
	}

	public void openConnection() {
		if (this.host != null) {
			// httpClient.clearResponseInterceptors();
			ObjectMapper mapper = new ObjectMapper();
			this.jsonParameters = mapper.createObjectNode();
			if (roundRobinStrategy.hostList == null) {
				roundRobinStrategy.setHosts(this.host);
			}
		}
	}

	// Release connection done here not in the rest methods because
	// put can return a stream (EX: openFile) or a Json in the HttpResponses
	// (most others).
	// Should not release connection becuase stream may be needed to see
	// response
	public HttpResponse createAndWrite(String hdfsPath, InputStream in) throws ClientProtocolException, IOException {
		logger.info("HDFS path: " + hdfsPath + " size " + in.available());
		HttpResponse response = buildURI("CREATE", hdfsPath).put(in);
		return response;
	}

	public HttpResponse createAndWrite(String hdfsPath, String filePath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("CREATE", hdfsPath).put(filePath);
		return response;
	}

	public HttpResponse append(String hdfsPath, InputStream in) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("APPEND", hdfsPath).post(in);
		return response;
	}

	public HttpResponse append(String hdfsPath, String filePath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("APPEND", hdfsPath).post(filePath);
		return response;
	}

	public HttpResponse openFile(String hdfsPath) throws ClientProtocolException, IOException {
		return buildURI("OPEN", hdfsPath).get();
	}

	public HttpResponse mkdir(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("MKDIRS", hdfsPath).put();
		return response;
	}

	public HttpResponse rename(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("RENAME", hdfsPath).put();
		return response;
	}

	public HttpResponse deleteFile(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("DELETE", hdfsPath).delete();
		return response;
	}

	public HttpResponse fileStatus(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("GETFILESTATUS", hdfsPath).get();
		return response;
	}

	public HttpResponse listStatus(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("LISTSTATUS", hdfsPath).get();
		return response;
	}

	public HttpResponse checksum(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("GETFILECHECKSUM", hdfsPath).get();
		return response;
	}

	public HttpResponse setPermission(String hdfsPath) throws ClientProtocolException, IOException {
		HttpResponse response = buildURI("SETPERMISSION", hdfsPath).put();
		return response;
	}
	public URI getURI(){
		return uri;
	}
}