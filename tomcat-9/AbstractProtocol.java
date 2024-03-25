/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.processing.Processor;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.omg.CORBA.Request;
import org.omg.PortableInterceptor.RequestInfo;

public abstract class AbstractProtocol<S> implements ProtocolHandler, MBeanRegistration {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AbstractProtocol.class);


    /**
     * Counter used to generate unique JMX names for connectors using automatic port binding.
     */
    private static final AtomicInteger nameCounter = new AtomicInteger(0);


    /**
     * Unique ID for this connector. Only used if the connector is configured to use a random port as the port will
     * change if stop(), start() is called.
     */
    private int nameIndex = 0;


    /**
     * Endpoint that provides low-level network I/O - must be matched to the ProtocolHandler implementation
     * (ProtocolHandler using NIO, requires NIO Endpoint etc.).
     */
    private final AbstractEndpoint<S, ?> endpoint;


    private Handler<S> handler;


    private final Set<Processor> waitingProcessors = ConcurrentHashMap.newKeySet();

    /**
     * Controller for the timeout scheduling.
     */
    private ScheduledFuture<?> timeoutFuture = null;
    private ScheduledFuture<?> monitorFuture;

    public AbstractProtocol(AbstractEndpoint<S, ?> endpoint) {
        this.endpoint = endpoint;
        ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
        getEndpoint().setHandler(cHandler);
        setHandler(cHandler);
        setConnectionLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }


    // ----------------------------------------------- Generic property handling

    /**
     * Generic property setter used by the digester. Other code should not need to use this. The digester will only use
     * this method if it can't find a more specific setter. That means the property belongs to the Endpoint, the
     * ServerSocketFactory or some other lower level component. This method ensures that it is visible to both.
     *
     * @param name  The name of the property to set
     * @param value The value, in string form, to set for the property
     *
     * @return <code>true</code> if the property was set successfully, otherwise <code>false</code>
     */
    public boolean setProperty(String name, String value) {
        return endpoint.setProperty(name, value);
    }


    /**
     * Generic property getter used by the digester. Other code should not need to use this.
     *
     * @param name The name of the property to get
     *
     * @return The value of the property converted to a string
     */
    public String getProperty(String name) {
        return endpoint.getProperty(name);
    }


    // ------------------------------- Properties managed by the ProtocolHandler

    /**
     * Name of MBean for the Global Request Processor.
     */
    protected ObjectName rgOname = null;

    public ObjectName getGlobalRequestProcessorMBeanName() {
        return rgOname;
    }

    /**
     * The adapter provides the link between the ProtocolHandler and the connector.
     */
    protected Adapter adapter;

    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }


    /**
     * The maximum number of idle processors that will be retained in the cache and re-used with a subsequent request.
     * The default is 200. A value of -1 means unlimited. In the unlimited case, the theoretical maximum number of
     * cached Processor objects is {@link #getMaxConnections()} although it will usually be closer to
     * {@link #getMaxThreads()}.
     */
    protected int processorCache = 200;

    public int getProcessorCache() {
        return this.processorCache;
    }

    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }


    private String clientCertProvider = null;

    /**
     * When client certificate information is presented in a form other than instances of
     * {@link java.security.cert.X509Certificate} it needs to be converted before it can be used and this property
     * controls which JSSE provider is used to perform the conversion. For example it is used with the AJP connectors,
     * the HTTP APR connector and with the {@link org.apache.catalina.valves.SSLValve}. If not specified, the default
     * provider will be used.
     *
     * @return The name of the JSSE provider to use
     */
    public String getClientCertProvider() {
        return clientCertProvider;
    }

    public void setClientCertProvider(String s) {
        this.clientCertProvider = s;
    }


    private int maxHeaderCount = 100;

    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }

    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    @Override
    public boolean isAprRequired() {
        return false;
    }


    @Override
    public boolean isSendfileSupported() {
        return endpoint.getUseSendfile();
    }


    @Override
    public String getId() {
        return endpoint.getId();
    }


    // ---------------------- Properties that are passed through to the EndPoint

    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }

    @Override
    public void setExecutor(Executor executor) {
        endpoint.setExecutor(executor);
    }


    @Override
    public ScheduledExecutorService getUtilityExecutor() {
        return endpoint.getUtilityExecutor();
    }

    @Override
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
        endpoint.setUtilityExecutor(utilityExecutor);
    }


    public int getMaxThreads() {
        return endpoint.getMaxThreads();
    }

    public void setMaxThreads(int maxThreads) {
        endpoint.setMaxThreads(maxThreads);
    }

    public int getMaxConnections() {
        return endpoint.getMaxConnections();
    }

    public void setMaxConnections(int maxConnections) {
        endpoint.setMaxConnections(maxConnections);
    }


    public int getMinSpareThreads() {
        return endpoint.getMinSpareThreads();
    }

    public void setMinSpareThreads(int minSpareThreads) {
        endpoint.setMinSpareThreads(minSpareThreads);
    }


    public int getThreadPriority() {
        return endpoint.getThreadPriority();
    }

    public void setThreadPriority(int threadPriority) {
        endpoint.setThreadPriority(threadPriority);
    }


    public int getAcceptCount() {
        return endpoint.getAcceptCount();
    }

    public void setAcceptCount(int acceptCount) {
        endpoint.setAcceptCount(acceptCount);
    }


    public boolean getTcpNoDelay() {
        return endpoint.getTcpNoDelay();
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        endpoint.setTcpNoDelay(tcpNoDelay);
    }


    public int getConnectionLinger() {
        return endpoint.getConnectionLinger();
    }

    public void setConnectionLinger(int connectionLinger) {
        endpoint.setConnectionLinger(connectionLinger);
    }


    /**
     * The time Tomcat will wait for a subsequent request before closing the connection. The default is
     * {@link #getConnectionTimeout()}.
     *
     * @return The timeout in milliseconds
     */
    public int getKeepAliveTimeout() {
        return endpoint.getKeepAliveTimeout();
    }

    public void setKeepAliveTimeout(int keepAliveTimeout) {
        endpoint.setKeepAliveTimeout(keepAliveTimeout);
    }

    public InetAddress getAddress() {
        return endpoint.getAddress();
    }

    public void setAddress(InetAddress ia) {
        endpoint.setAddress(ia);
    }


    public int getPort() {
        return endpoint.getPort();
    }

    public void setPort(int port) {
        endpoint.setPort(port);
    }


    public int getPortOffset() {
        return endpoint.getPortOffset();
    }

    public void setPortOffset(int portOffset) {
        endpoint.setPortOffset(portOffset);
    }


    public int getPortWithOffset() {
        return endpoint.getPortWithOffset();
    }


    public int getLocalPort() {
        return endpoint.getLocalPort();
    }

    /*
     * When Tomcat expects data from the client, this is the time Tomcat will wait for that data to arrive before
     * closing the connection.
     */
    public int getConnectionTimeout() {
        return endpoint.getConnectionTimeout();
    }

    public void setConnectionTimeout(int timeout) {
        endpoint.setConnectionTimeout(timeout);
    }

    public long getConnectionCount() {
        return endpoint.getConnectionCount();
    }

    /**
     * NO-OP.
     *
     * @param threadCount Unused
     *
     * @deprecated Will be removed in Tomcat 10.
     */
    @Deprecated
    public void setAcceptorThreadCount(int threadCount) {
    }

    /**
     * Always returns 1.
     *
     * @return Always 1.
     *
     * @deprecated Will be removed in Tomcat 10.
     */
    @Deprecated
    public int getAcceptorThreadCount() {
        return 1;
    }

    public void setAcceptorThreadPriority(int threadPriority) {
        endpoint.setAcceptorThreadPriority(threadPriority);
    }

    public int getAcceptorThreadPriority() {
        return endpoint.getAcceptorThreadPriority();
    }


    // ---------------------------------------------------------- Public methods

    public synchronized int getNameIndex() {
        if (nameIndex == 0) {
            nameIndex = nameCounter.incrementAndGet();
        }

        return nameIndex;
    }


    /**
     * The name will be prefix-address-port if address is non-null and prefix-port if the address is null.
     *
     * @return A name for this protocol instance that is appropriately quoted for use in an ObjectName.
     */
    public String getName() {
        return ObjectName.quote(getNameInternal());
    }


    private String getNameInternal() {
        StringBuilder name = new StringBuilder(getNamePrefix());
        name.append('-');
        String id = getId();
        if (id != null) {
            name.append(id);
        } else {
            if (getAddress() != null) {
                name.append(getAddress().getHostAddress());
                name.append('-');
            }
            int port = getPortWithOffset();
            if (port == 0) {
                // Auto binding is in use. Check if port is known
                name.append("auto-");
                name.append(getNameIndex());
                port = getLocalPort();
                if (port != -1) {
                    name.append('-');
                    name.append(port);
                }
            } else {
                name.append(port);
            }
        }
        return name.toString();
    }


    public void addWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.add", processor));
        }
        waitingProcessors.add(processor);
    }


    public void removeWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.remove", processor));
        }
        waitingProcessors.remove(processor);
    }


    /*
     * Primarily for debugging and testing. Could be exposed via JMX if considered useful.
     */
    public int getWaitingProcessorCount() {
        return waitingProcessors.size();
    }


    // ----------------------------------------------- Accessors for sub-classes

    protected AbstractEndpoint<S, ?> getEndpoint() {
        return endpoint;
    }


    protected Handler<S> getHandler() {
        return handler;
    }

    protected void setHandler(Handler<S> handler) {
        this.handler = handler;
    }


    // -------------------------------------------------------- Abstract methods

    /**
     * Concrete implementations need to provide access to their logger to be used by the abstract classes.
     *
     * @return the logger
     */
    protected abstract Log getLog();


    /**
     * Obtain the prefix to be used when construction a name for this protocol handler. The name will be
     * prefix-address-port.
     *
     * @return the prefix
     */
    protected abstract String getNamePrefix();


    /**
     * Obtain the name of the protocol, (Http, Ajp, etc.). Used with JMX.
     *
     * @return the protocol name
     */
    protected abstract String getProtocolName();


    /**
     * Find a suitable handler for the protocol negotiated at the network layer.
     *
     * @param name The name of the requested negotiated protocol.
     *
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches the requested protocol
     */
    protected abstract UpgradeProtocol getNegotiatedProtocol(String name);


    /**
     * Find a suitable handler for the protocol upgraded name specified. This is used for direct connection protocol
     * selection.
     *
     * @param name The name of the requested negotiated protocol.
     *
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches the requested protocol
     */
    protected abstract UpgradeProtocol getUpgradeProtocol(String name);


    /**
     * Create and configure a new Processor instance for the current protocol implementation.
     *
     * @return A fully configured Processor instance that is ready to use
     */
    protected abstract Processor createProcessor();


    protected abstract Processor createUpgradeProcessor(SocketWrapperBase<?> socket, UpgradeToken upgradeToken);


    // ----------------------------------------------------- JMX related methods

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        oname = name;
        mserver = server;
        domain = name.getDomain();
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        // NOOP
    }

    private ObjectName createObjectName() throws MalformedObjectNameException {
        // Use the same domain as the connector
        domain = getAdapter().getDomain();

        if (domain == null) {
            return null;
        }

        StringBuilder name = new StringBuilder(getDomain());
        name.append(":type=ProtocolHandler,port=");
        int port = getPortWithOffset();
        if (port > 0) {
            name.append(port);
        } else {
            name.append("auto-");
            name.append(getNameIndex());
        }
        InetAddress address = getAddress();
        if (address != null) {
            name.append(",address=");
            name.append(ObjectName.quote(address.getHostAddress()));
        }
        return new ObjectName(name.toString());
    }


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: There is no maintenance of state or checking for valid transitions within this class. It is expected that
     * the connector will maintain state and prevent invalid state transitions.
     */

    
    /**
 
	 * 
	 */
	//代码5
    @Override
    public void init() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.init", getName()));
            logPortOffset();
        }

        if (oname == null) {
            // Component not pre-registered so register it
            oname = createObjectName();
            if (oname != null) {
                Registry.getRegistry(null, null).registerComponent(this, oname, null);
            }
        }

        if (this.domain != null) {
            ObjectName rgOname = new ObjectName(domain + ":type=GlobalRequestProcessor,name=" + getName());
            this.rgOname = rgOname;
            Registry.getRegistry(null, null).registerComponent(getHandler().getGlobal(), rgOname, null);
        }

        String endpointName = getName();
        endpoint.setName(endpointName.substring(1, endpointName.length() - 1));
        endpoint.setDomain(domain);


        // endpoint 的初始化主要包括:创建ServerSocketChannel，并绑定指定地址，端口
        endpoint.init();      // 定义在 NioEndpoint 的父类  AbstractEndpoint
                              // 见该类代码5
    }


    // 代码10
    @Override
    public void start() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.start", getName()));
            logPortOffset();
        }

        endpoint.start();
        monitorFuture = getUtilityExecutor().scheduleWithFixedDelay(() -> {
            startAsyncTimeout();
        }, 0, 60, TimeUnit.SECONDS);
    }


    /**
     * Note: The name of this method originated with the Servlet 3.0 asynchronous processing but evolved over time to
     * represent a timeout that is triggered independently of the socket read/write timeouts.
     */
    protected void startAsyncTimeout() {
        if (timeoutFuture == null || timeoutFuture.isDone()) {
            if (timeoutFuture != null && timeoutFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    timeoutFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    getLog().error(sm.getString("abstractProtocolHandler.asyncTimeoutError"), e);
                }
            }
            timeoutFuture = getUtilityExecutor().scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                for (Processor processor : waitingProcessors) {
                    processor.timeoutAsync(now);
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    protected void stopAsyncTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    @Override
    public void pause() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.pause", getName()));
        }

        endpoint.pause();
    }


    public boolean isPaused() {
        return endpoint.isPaused();
    }


    @Override
    public void resume() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.resume", getName()));
        }

        endpoint.resume();
    }


    @Override
    public void stop() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.stop", getName()));
            logPortOffset();
        }

        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        stopAsyncTimeout();
        // Timeout any waiting processor
        for (Processor processor : waitingProcessors) {
            processor.timeoutAsync(-1);
        }

        endpoint.stop();
    }


    @Override
    public void destroy() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.destroy", getName()));
            logPortOffset();
        }

        try {
            endpoint.destroy();
        } finally {
            if (oname != null) {
                if (mserver == null) {
                    Registry.getRegistry(null, null).unregisterComponent(oname);
                } else {
                    // Possibly registered with a different MBeanServer
                    try {
                        mserver.unregisterMBean(oname);
                    } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                        getLog().info(sm.getString("abstractProtocol.mbeanDeregistrationFailed", oname, mserver));
                    }
                }
            }

            ObjectName rgOname = getGlobalRequestProcessorMBeanName();
            if (rgOname != null) {
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
            }
        }
    }


    @Override
    public void closeServerSocketGraceful() {
        endpoint.closeServerSocketGraceful();
    }


    @Override
    public long awaitConnectionsClose(long waitMillis) {
        getLog().info(sm.getString("abstractProtocol.closeConnectionsAwait", Long.valueOf(waitMillis), getName()));
        return endpoint.awaitConnectionsClose(waitMillis);
    }


    private void logPortOffset() {
        if (getPort() != getPortWithOffset()) {
            getLog().info(sm.getString("abstractProtocolHandler.portOffset", getName(), String.valueOf(getPort()),
                    String.valueOf(getPortOffset())));
        }
    }


 

    protected static class RecycledProcessors extends SynchronizedStack<Processor> {

        private final transient ConnectionHandler<?> handler;
        protected final AtomicInteger size = new AtomicInteger(0);

        public RecycledProcessors(ConnectionHandler<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("sync-override") // Size may exceed cache size a bit
        @Override
        public boolean push(Processor processor) {
            int cacheSize = handler.getProtocol().getProcessorCache();
            boolean offer = cacheSize == -1 ? true : size.get() < cacheSize;
            // avoid over growing our cache or add after we have stopped
            boolean result = false;
            if (offer) {
                result = super.push(processor);
                if (result) {
                    size.incrementAndGet();
                }
            }
            if (!result) {
                handler.unregister(processor);
            }
            return result;
        }

        @SuppressWarnings("sync-override") // OK if size is too big briefly
        @Override
        public Processor pop() {
            Processor result = super.pop();
            if (result != null) {
                size.decrementAndGet();
            }
            return result;
        }

        @Override
        public synchronized void clear() {
            Processor next = pop();
            while (next != null) {
                handler.unregister(next);
                next = pop();
            }
            super.clear();
            size.set(0);
        }
    }

}
