/*
  Minimal Java Networking - a barebones networking library for Java and Android
  Copyright (C) 2017 Luka Jovičić

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published
  by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package rs.lukaj.minnetwork;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Builds a network request. Requests are always executed on the background thread - otherwise, an exception
 * by the Android would've been thrown. It is usually a good idea to set custom id and auth, though this
 * interface doesn't require it (server, on the other hand, might refuse to serve a request without proper
 * Authorization).
 *
 * This provides a way to do both asynchronous (in which case callbacks are needed) and blocking requests.
 * Avoid making blocking requests on UI thread at all costs, as such could cause UI freezing.
 *
 * All methods allow chaining.
 * Created by luka on 4.8.17.
 */
public class NetworkRequestBuilder<Send, Receive> {
    private static final ExecutorService    executor    = Executors.newCachedThreadPool(); //executor for background execution of requests

    public static final Map<String, String> emptyMap    = Collections.emptyMap();
    public static final String              VERB_POST   = "POST";
    public static final String              VERB_GET    = "GET";
    public static final String              VERB_PUT    = "PUT";
    public static final String              VERB_DELETE = "DELETE";
    public static final int                 DEFAULT_ID  = -1;


    private URL url;
    private String verb;
    private AuthTokenManager tokens;

    private int requestId = DEFAULT_ID;
    private ExecutorService executeOn = executor;
    private Map<String, String> data;
    private File file;
    private NetworkExceptionHandler exceptionHandler;

    private NetworkRequestBuilder(URL url, String verb) {
        this.url = url;
        this.verb = verb;
    }

    /**
     * Creates the builder for given url, verb and string parameters to be passed with the request
     * @param url url to which request will be made
     * @param verb should be one of {@link #VERB_GET}, {@link #VERB_POST}, {@link #VERB_PUT} or {@link #VERB_DELETE}
     * @param params string parameters to include with the request
     * @return NetworkRequestBuilder which should be used to specify additional options and execute the request
     */
    public static NetworkRequestBuilder<String, String> create(URL url, String verb, Map<String, String> params) {
        return new NetworkRequestBuilder<String, String>(url, verb).data(params);
    }

    /**
     * Creates the builder for given url and verb with no additional parameters nor receiving files.
     * @param url url to which request will be made
     * @param verb should be one of {@link #VERB_GET}, {@link #VERB_POST}, {@link #VERB_PUT} or {@link #VERB_DELETE}
     * @return NetworkRequestBuilder which should be used to specify additional options and execute the request
     */
    public static NetworkRequestBuilder<String, String> create(URL url, String verb) {
        return create(url, verb, emptyMap);
    }

    /**
     * Creates the builder for given url, verb and a file associated with it. This request will
     * send a file and return a String.
     * @param url url to which request will be made
     * @param verb should be one of {@link #VERB_GET}, {@link #VERB_POST}, {@link #VERB_PUT} or {@link #VERB_DELETE}
     * @param file File which should be sent
     * @return NetworkRequestBuilder which should be used to specify additional options and execute the request
     */
    public static NetworkRequestBuilder<File, String> create(URL url, String verb, File file) {
        return new NetworkRequestBuilder<File, String>(url, verb).file(file);
    }

    /**
     * Creates the builder for given url, verb, file and additional params for request. This request
     * will send urlencoded String map and return a File.
     * @param url url to which the request will be made
     * @param verb should be one of {@link #VERB_GET}, {@link #VERB_POST}, {@link #VERB_PUT} or {@link #VERB_DELETE}
     * @param data parameters for request
     * @param saveTo File to which to save the received data
     * @return NetworkRequestBuilder which should be used to specify additional options and execute the request
     */
    public static NetworkRequestBuilder<String, File> create(URL url, String verb, Map<String, String> data, File saveTo) {
        return new NetworkRequestBuilder<String, File>(url, verb).file(saveTo).data(data);
    }

    private NetworkRequestBuilder<Send, Receive> data(Map<String, String> data) {
        this.data = data;
        return this;
    }

    private NetworkRequestBuilder<Send, Receive> file(File file) {
        this.file = file;
        return this;
    }

    /**
     * Sets id for this request, which will be passed to {@link rs.lukaj.minnetwork.Network.NetworkCallbacks}.
     * Should be positive.
     */
    public NetworkRequestBuilder<Send, Receive> id(int id) {
        this.requestId = id;
        return this;
    }

    /**
     * Attaches {@link NetworkExceptionHandler} to the (future) request. If handler is
     * present after making the request, and response isn't normal (i.e.
     * {@link Network.Response#isError()} returns true, handler is called to resolve
     * the error. This applies both to async and blocking requests.
     * @param exceptionHandler handler used to handle possible errors
     * @return this
     */
    public NetworkRequestBuilder<Send, Receive> handler(NetworkExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Sets source for retrieving and potential changing of auth token.
     * If none is specified, no Authorization header is sent.
     */
    public NetworkRequestBuilder<Send, Receive> auth(AuthTokenManager tokens) {
        this.tokens = tokens;
        return this;
    }

    /**
     * Provides specific executor on which this request should be made. Use if you already have an executor,
     * and/or wish to make requests in succession with some single-threaded executor. By default, this method
     * has a static cached thread pool executor on which requests are run.
     * @param executor custom executor on which to execute this request
     * @return this
     */
    public NetworkRequestBuilder<Send, Receive> executor(ExecutorService executor) {
        this.executeOn = executor;
        return this;
    }

    /**
     * Executes this request asynchronously and notifies callbacks of the outcome
     * @param callbacks callbacks which should be notified
     */
    @SuppressWarnings("unchecked") //stupid generics
    public void async(Network.NetworkCallbacks<Receive> callbacks) {
        if(data == null && file == null) throw new InvalidRequest("Need to set either data or file");

        if(exceptionHandler != null) callbacks = extendCallbacksWithHandler(callbacks);
        if(file == null) {
            requestDataAsync(requestId, url, data, tokens, (Network.NetworkCallbacks<String>)callbacks, verb);
        } else {
            if(data != null) {
                requestFileAsync(requestId, url, data, file, tokens, (Network.NetworkCallbacks<File>)callbacks, verb);
            } else {
                sendFileAsync(requestId, url, file, tokens, (Network.NetworkCallbacks<String>)callbacks, verb);
            }
        }
    }

    /**
     * Executes this request in a blocking fashion, but on a separate thread. Nonetheless,
     * current thread will be blocked for the duration. If request isn't finished before
     * the timeout is reached, TimeoutException is thrown.
     * In case NetworkExceptionHandler is supplied, worst-case time this method can take
     * is timeout*2, in case first request almost reaches timeout, and second times out.
     * @param timeout timeout
     * @param unit timeunit
     * @return
     * @throws ExecutionException something unexpected has occurred
     * @throws TimeoutException timeout has been reached, and request hasn't completed
     * @throws IOException some IO exception occurred. Could be network issue, could be
     *      something with files, could be something else.
     */
    @SuppressWarnings("unchecked")
    public Network.Response<Receive> blocking(long timeout, TimeUnit unit)
            throws ExecutionException, TimeoutException, IOException {
        if(data == null && file == null) throw new InvalidRequest("Need to set either data or file");

        Network.Response<Receive> response;
        if(file == null) {
            response = (Network.Response<Receive>)requestDataBlocking(requestId, url, data, tokens, timeout, unit, verb);
        } else {
            if(data != null) {
                response = (Network.Response<Receive>)requestFileBlocking(requestId, url, data, file, tokens, timeout, unit, verb);
            } else {
                response = (Network.Response<Receive>)sendFileBlocking(requestId, url, file, tokens, timeout, unit, verb);
            }
        }

        if(exceptionHandler != null && response.isError()) {
            response = getTaskResult(getExceptionHandlerTask(response), timeout, unit);
        }
        return response;
    }

    private Network.NetworkCallbacks<Receive> extendCallbacksWithHandler(final Network.NetworkCallbacks<Receive> callbacks) {
        return new Network.NetworkCallbacks<Receive>() {
            @Override
            public void onRequestCompleted(int id, Network.Response<Receive> response) {
                if(response.isError()) {
                    response = response.handleErrorCode(exceptionHandler);
                }
                callbacks.onRequestCompleted(id, response);
            }

            @Override
            public void onExceptionThrown(int id, Throwable ex) {
                callbacks.onExceptionThrown(id, ex);
            }
        };
    }

    private Future<Network.Response<Receive>> getExceptionHandlerTask(final Network.Response<Receive> response) {
        return executeOn.submit(new Callable<Network.Response<Receive>>() {
            @Override
            public Network.Response<Receive> call() {
                return response.handleErrorCode(exceptionHandler);
            }
        });
    }

    private void requestDataAsync(int requestId, URL url, Map<String, String> data, AuthTokenManager tokens,
                                         Network.NetworkCallbacks<String> callback, String verb) {
        executeOn.submit(new Network.StringRequest(requestId, url, tokens, data, verb, callback));
    }

    private void sendFileAsync(int requestId, URL url, File data,  AuthTokenManager tokens,
                                         Network.NetworkCallbacks<String> callbacks, String verb) {
        executeOn.submit(new Network.FileStringRequest(requestId, url, tokens, data, verb, callbacks));
    }
    private void requestFileAsync(int requestId, URL url, Map<String, String> data, File saveTo, AuthTokenManager tokens,
                                  Network.NetworkCallbacks<File> callbacks, String verb) {
        executeOn.submit(new Network.StringFileRequest(requestId, url, tokens, data, saveTo, verb, callbacks));
    }

    //this also does request on executor (i.e. on background thread); HOWEVER it returns value after the timeout has passed, if it's done
    //if not, it throws TimeoutException
    private Network.Response<String> requestDataBlocking(int requestId, URL url, Map<String, String> data,
                                                                AuthTokenManager tokens, long timeout,
                                                                TimeUnit unit, String verb)
            throws ExecutionException, TimeoutException, IOException {
        Future<Network.Response<String>> task = executeOn.submit(new Network.StringRequest(requestId, url,
                                                                                          tokens, data,
                                                                                          verb, null));
        return getTaskResult(task, timeout, unit);
    }

    private Network.Response<File> requestFileBlocking(int requestId, URL url, Map<String, String> send, File saveTo,
                                                                AuthTokenManager tokens, long timeout,
                                                                TimeUnit unit, String verb)
            throws ExecutionException, TimeoutException, IOException {
        Future<Network.Response<File>> task = executeOn
                .submit(new Network.StringFileRequest(requestId, url, tokens, send, saveTo, verb, null));
        return getTaskResult(task, timeout, unit);
    }

    private Network.Response<String> sendFileBlocking(int requestId, URL url, File send, AuthTokenManager tokens,
                                                      long timeout, TimeUnit unit, String verb)
            throws ExecutionException, TimeoutException, IOException {
        Future<Network.Response<String>> task = executeOn
                .submit(new Network.FileStringRequest(requestId, url, tokens, send, verb, null));
        return getTaskResult(task, timeout, unit);
    }

    private static <T> Network.Response<T> getTaskResult(Future<Network.Response<T>> task, long timeout, TimeUnit unit)
            throws TimeoutException, IOException, ExecutionException {
        try {
            return task.get(timeout, unit);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return null;
        } catch (ExecutionException ex) {
            if(ex.getCause() instanceof FileNotFoundException)
                throw (FileNotFoundException)ex.getCause();
            else if(ex.getCause() instanceof IOException)
                throw (IOException)ex.getCause();
            else
                throw ex;
        }
    }
}
