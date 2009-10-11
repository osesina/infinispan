package org.infinispan.config;

import org.infinispan.CacheException;
import org.infinispan.Version;
import org.infinispan.executors.DefaultExecutorFactory;
import org.infinispan.executors.DefaultScheduledExecutorFactory;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.SurvivesRestarts;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.jmx.PlatformMBeanServerLookup;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.marshall.VersionAwareMarshaller;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.util.TypedProperties;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Properties;

/**
 * Configuration component that encapsulates the global configuration.
 * 
 * <p>
 * Note that class GlobalConfiguration contains JAXB annotations. These annotations determine how XML
 * configuration files are read into instances of configuration class hierarchy as well as they
 * provide meta data for configuration file XML schema generation. Please modify these annotations
 * and Java element types they annotate with utmost understanding and care.
 * 
 * @configRef name="global",desc="Defines global configuration shared among all cache instances."
 *
 * @author Manik Surtani
 * @author Vladimir Blagojevic
 * @since 4.0
 */
@SurvivesRestarts
@Scope(Scopes.GLOBAL)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder={})
public class GlobalConfiguration extends AbstractConfigurationBean {

   public GlobalConfiguration() {
      super();
   }

   /**
    * Default replication version, from {@link org.infinispan.Version#getVersionShort}.
    */
   public static final short DEFAULT_MARSHALL_VERSION = Version.getVersionShort();
   
   @XmlElement
   private FactoryClassWithPropertiesType asyncListenerExecutor = new FactoryClassWithPropertiesType(DefaultExecutorFactory.class.getName());
   
   @XmlElement
   private FactoryClassWithPropertiesType asyncTransportExecutor= new FactoryClassWithPropertiesType(DefaultExecutorFactory.class.getName());
   
   @XmlElement
   private FactoryClassWithPropertiesType evictionScheduledExecutor= new FactoryClassWithPropertiesType(DefaultScheduledExecutorFactory.class.getName());
   
   @XmlElement
   private FactoryClassWithPropertiesType replicationQueueScheduledExecutor= new FactoryClassWithPropertiesType(DefaultScheduledExecutorFactory.class.getName());
   
   @XmlElement
   private GlobalJmxStatisticsType globalJmxStatistics = new GlobalJmxStatisticsType();
   
   @XmlElement
   private TransportType transport = new TransportType(null);
  
   @XmlElement
   private SerializationType serialization = new SerializationType();
   
   @XmlTransient
   private Configuration defaultConfiguration;
   
   @XmlElement
   private ShutdownType shutdown = new ShutdownType();

   @XmlTransient
   private GlobalComponentRegistry gcr;

   public boolean isExposeGlobalJmxStatistics() {
      return globalJmxStatistics.enabled;
   }
   
   public void setExposeGlobalJmxStatistics(boolean exposeGlobalJmxStatistics) {
      testImmutability("exposeGlobalManagementStatistics");
      globalJmxStatistics.setEnabled(exposeGlobalJmxStatistics);
   }

   /**
    * If JMX statistics are enabled then all 'published' JMX objects will appear under this name. This is optional, if
    * not specified an object name will be created for you by default.
    */
   public void setJmxDomain(String jmxObjectName) {
      globalJmxStatistics.setJmxDomain(jmxObjectName);
   }

   /**
    * @see #setJmxDomain(String)
    */
   public String getJmxDomain() {
      return globalJmxStatistics.jmxDomain;
   }

   public String getMBeanServerLookup() {
      return globalJmxStatistics.mBeanServerLookup;
   }

   public void setMBeanServerLookup(String mBeanServerLookup) {
      globalJmxStatistics.setMBeanServerLookup(mBeanServerLookup);
   }

   public boolean isAllowDuplicateDomains() {
      return globalJmxStatistics.allowDuplicateDomains;
   }

   public void setAllowDuplicateDomains(boolean allowDuplicateDomains) {
      globalJmxStatistics.setAllowDuplicateDomains(allowDuplicateDomains);
   }

   /**
    * Behavior of the JVM shutdown hook registered by the cache
    */
   public static enum ShutdownHookBehavior {
      /**
       * By default a shutdown hook is registered if no MBean server (apart from the JDK default) is detected.
       */
      DEFAULT,
      /**
       * Forces the cache to register a shutdown hook even if an MBean server is detected.
       */
      REGISTER,
      /**
       * Forces the cache NOT to register a shutdown hook, even if no MBean server is detected.
       */
      DONT_REGISTER
   }

   @Inject
   private void injectDependencies(GlobalComponentRegistry gcr) {
      this.gcr = gcr;     
      gcr.registerComponent(asyncListenerExecutor, "asyncListenerExecutor");
      gcr.registerComponent(asyncTransportExecutor, "asyncTransportExecutor");
      gcr.registerComponent(evictionScheduledExecutor, "evictionScheduledExecutor");
      gcr.registerComponent(replicationQueueScheduledExecutor, "replicationQueueScheduledExecutor");
      gcr.registerComponent(replicationQueueScheduledExecutor, "replicationQueueScheduledExecutor");
      gcr.registerComponent(globalJmxStatistics, "globalJmxStatistics");
      gcr.registerComponent(transport, "transport");
      gcr.registerComponent(serialization, "serialization");
      gcr.registerComponent(shutdown, "shutdown");
   }

   protected boolean hasComponentStarted() {
      return gcr != null && gcr.getStatus() != null && gcr.getStatus() == ComponentStatus.RUNNING;
   }

   public String getAsyncListenerExecutorFactoryClass() {
      return asyncListenerExecutor.factory;
   }

   public void setAsyncListenerExecutorFactoryClass(String asyncListenerExecutorFactoryClass) {
      asyncListenerExecutor.setFactory(asyncListenerExecutorFactoryClass);
   }

   public String getAsyncTransportExecutorFactoryClass() {
      return asyncTransportExecutor.factory;
   }

   public void setAsyncTransportExecutorFactoryClass(String asyncTransportExecutorFactoryClass) {
      this.asyncTransportExecutor.setFactory(asyncTransportExecutorFactoryClass);
   }

   public String getEvictionScheduledExecutorFactoryClass() {
      return evictionScheduledExecutor.factory;
   }

   public void setEvictionScheduledExecutorFactoryClass(String evictionScheduledExecutorFactoryClass) {
      evictionScheduledExecutor.setFactory(evictionScheduledExecutorFactoryClass);
   }

   public String getReplicationQueueScheduledExecutorFactoryClass() {
      return replicationQueueScheduledExecutor.factory;
   }


   public void setReplicationQueueScheduledExecutorFactoryClass(String replicationQueueScheduledExecutorFactoryClass) {
      replicationQueueScheduledExecutor.setFactory(replicationQueueScheduledExecutorFactoryClass);
   }

   public String getMarshallerClass() {
      return serialization.marshallerClass;
   }

   public void setMarshallerClass(String marshallerClass) {
      serialization.setMarshallerClass(marshallerClass);
   }
   
   public String getTransportNodeName() {
      return transport.nodeName;
   }
   
   public void setTransportNodeName(String nodeName) {
      transport.setNodeName(nodeName);
   }

   public String getTransportClass() {
      return transport.transportClass;
   }


   public void setTransportClass(String transportClass) {
      transport.setTransportClass(transportClass);
   }

   public Properties getTransportProperties() {
      return transport.properties;
   }

   public void setTransportProperties(Properties transportProperties) {
      transport.setProperties(toTypedProperties(transportProperties));
   }
   
   public void setTransportProperties(String transportPropertiesString) {
      transport.setProperties(toTypedProperties(transportPropertiesString));
   }

   public Configuration getDefaultConfiguration() {
      return defaultConfiguration;
   }

   public void setDefaultConfiguration(Configuration defaultConfiguration) {
      this.defaultConfiguration = defaultConfiguration;
   }

   public String getClusterName() {
      return transport.clusterName;
   }

   public void setClusterName(String clusterName) {
      transport.setClusterName(clusterName);
   }

   public ShutdownHookBehavior getShutdownHookBehavior() {
      return shutdown.hookBehavior;
   }

   public void setShutdownHookBehavior(ShutdownHookBehavior shutdownHookBehavior) {
      shutdown.setHookBehavior(shutdownHookBehavior);
   }

   public void setShutdownHookBehavior(String shutdownHookBehavior) {
      if (shutdownHookBehavior == null)
         throw new ConfigurationException("Shutdown hook behavior cannot be null", "ShutdownHookBehavior");
      ShutdownHookBehavior temp = ShutdownHookBehavior.valueOf(uc(shutdownHookBehavior));
      if (temp == null) {
         log.warn("Unknown shutdown hook behavior '" + shutdownHookBehavior + "', using defaults.");
         temp = ShutdownHookBehavior.DEFAULT;
      }
      setShutdownHookBehavior(temp);
   }

   public Properties getAsyncListenerExecutorProperties() {
      return asyncListenerExecutor.properties;
   }
   

   public void setAsyncListenerExecutorProperties(Properties asyncListenerExecutorProperties) {
      asyncListenerExecutor.setProperties(toTypedProperties(asyncListenerExecutorProperties));
   }

   public void setAsyncListenerExecutorProperties(String asyncListenerExecutorPropertiesString) {
      asyncListenerExecutor.setProperties(toTypedProperties(asyncListenerExecutorPropertiesString));
   }

   public Properties getAsyncTransportExecutorProperties() {
      return asyncTransportExecutor.properties;
   }

   public void setAsyncTransportExecutorProperties(Properties asyncTransportExecutorProperties) {
      this.asyncTransportExecutor.setProperties(toTypedProperties(asyncTransportExecutorProperties));
   }

   public void setAsyncTransportExecutorProperties(String asyncSerializationExecutorPropertiesString) {
      this.asyncTransportExecutor.setProperties(toTypedProperties(asyncSerializationExecutorPropertiesString));
   }

   public Properties getEvictionScheduledExecutorProperties() {
      return evictionScheduledExecutor.properties;
   }
  
   public void setEvictionScheduledExecutorProperties(Properties evictionScheduledExecutorProperties) {
      evictionScheduledExecutor.setProperties(toTypedProperties(evictionScheduledExecutorProperties));
   }

   public void setEvictionScheduledExecutorProperties(String evictionScheduledExecutorPropertiesString) {
      evictionScheduledExecutor.setProperties(toTypedProperties(evictionScheduledExecutorPropertiesString));
   }

   public Properties getReplicationQueueScheduledExecutorProperties() {
      return replicationQueueScheduledExecutor.properties;
   }
     
   public void setReplicationQueueScheduledExecutorProperties(Properties replicationQueueScheduledExecutorProperties) {
      this.replicationQueueScheduledExecutor.setProperties(toTypedProperties(replicationQueueScheduledExecutorProperties));
   }

   public void setReplicationQueueScheduledExecutorProperties(String replicationQueueScheduledExecutorPropertiesString) {
      this.replicationQueueScheduledExecutor.setProperties(toTypedProperties(replicationQueueScheduledExecutorPropertiesString));
   }

   public short getMarshallVersion() {
      return Version.getVersionShort(serialization.version);
   }

   public String getMarshallVersionString() {
      return serialization.version;
   }

   public void setMarshallVersion(short marshallVersion) {
      testImmutability("marshallVersion");
      serialization.version = Version.decodeVersionForSerialization(marshallVersion);
   }
   
   public void setMarshallVersion(String marshallVersion) {
      serialization.setVersion(marshallVersion);
   }

   public long getDistributedSyncTimeout() {
      return transport.distributedSyncTimeout;
   }
   
   public void setDistributedSyncTimeout(long distributedSyncTimeout) {
      transport.distributedSyncTimeout = distributedSyncTimeout;
   }
   
    public void accept(ConfigurationBeanVisitor v) {        
        asyncListenerExecutor.accept(v);
        asyncTransportExecutor.accept(v);
        evictionScheduledExecutor.accept(v);
        globalJmxStatistics.accept(v);
        replicationQueueScheduledExecutor.accept(v);
        serialization.accept(v);
        shutdown.accept(v);
        transport.accept(v);   
        v.visitGlobalConfiguration(this);
    }

@Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GlobalConfiguration that = (GlobalConfiguration) o;

      if (!serialization.version.equals(that.serialization.version)) return false;
      if (asyncListenerExecutor.factory != null ? !asyncListenerExecutor.factory.equals(that.asyncListenerExecutor.factory) : that.asyncListenerExecutor.factory != null)
         return false;
      if (asyncListenerExecutor.properties != null ? !asyncListenerExecutor.properties.equals(that.asyncListenerExecutor.properties) : that.asyncListenerExecutor.properties != null)
         return false;
      if (asyncTransportExecutor.factory != null ? !asyncTransportExecutor.factory.equals(that.asyncTransportExecutor.factory) : that.asyncTransportExecutor.factory != null)
         return false;
      if (asyncTransportExecutor.properties != null ? !asyncTransportExecutor.properties.equals(that.asyncTransportExecutor.properties) : that.asyncTransportExecutor.properties != null)
         return false;
      if (transport.clusterName != null ? !transport.clusterName.equals(that.transport.clusterName) : that.transport.clusterName != null) 
         return false;
      if (defaultConfiguration != null ? !defaultConfiguration.equals(that.defaultConfiguration) : that.defaultConfiguration != null)
         return false;
      if (evictionScheduledExecutor.factory != null ? !evictionScheduledExecutor.factory.equals(that.evictionScheduledExecutor.factory) : that.evictionScheduledExecutor.factory != null)
         return false;
      if (evictionScheduledExecutor.properties != null ? !evictionScheduledExecutor.properties.equals(that.evictionScheduledExecutor.properties) : that.evictionScheduledExecutor.properties  != null)
         return false;
      if (serialization.marshallerClass != null ? !serialization.marshallerClass .equals(that.serialization.marshallerClass ) : that.serialization.marshallerClass  != null)
         return false;
      if (replicationQueueScheduledExecutor.factory != null ? !replicationQueueScheduledExecutor.factory .equals(that.replicationQueueScheduledExecutor.factory ) : that.replicationQueueScheduledExecutor.factory  != null)
         return false;
      if (replicationQueueScheduledExecutor.properties != null ? !replicationQueueScheduledExecutor.properties.equals(that.replicationQueueScheduledExecutor.properties) : that.replicationQueueScheduledExecutor.properties != null)
         return false;
      if (shutdown.hookBehavior != null ? !shutdown.hookBehavior.equals(that.shutdown.hookBehavior) : that.shutdown.hookBehavior != null) 
         return false;
      if (transport.transportClass != null ? !transport.transportClass.equals(that.transport.transportClass) : that.transport.transportClass != null)
         return false;
      if (transport.properties != null ? !transport.properties.equals(that.transport.properties) : that.transport.properties != null)
         return false;
      return !(transport.distributedSyncTimeout != null && !transport.distributedSyncTimeout.equals(that.transport.distributedSyncTimeout));

   }

   @Override
   public int hashCode() {
      int result = asyncListenerExecutor.factory != null ? asyncListenerExecutor.factory.hashCode() : 0;
      result = 31 * result + (asyncListenerExecutor.properties != null ? asyncListenerExecutor.properties.hashCode() : 0);
      result = 31 * result + (asyncTransportExecutor.factory != null ? asyncTransportExecutor.factory.hashCode() : 0);
      result = 31 * result + (asyncTransportExecutor.properties != null ? asyncTransportExecutor.properties.hashCode() : 0);
      result = 31 * result + (evictionScheduledExecutor.factory != null ? evictionScheduledExecutor.factory.hashCode() : 0);
      result = 31 * result + ( evictionScheduledExecutor.properties  != null ? evictionScheduledExecutor.properties.hashCode() : 0);
      result = 31 * result + (replicationQueueScheduledExecutor.factory  != null ? replicationQueueScheduledExecutor.factory.hashCode() : 0);
      result = 31 * result + (replicationQueueScheduledExecutor.properties != null ? replicationQueueScheduledExecutor.properties.hashCode() : 0);
      result = 31 * result + (serialization.marshallerClass  != null ? serialization.marshallerClass .hashCode() : 0);
      result = 31 * result + (transport.transportClass != null ? transport.transportClass.hashCode() : 0);
      result = 31 * result + (transport.properties  != null ? transport.properties .hashCode() : 0);
      result = 31 * result + (defaultConfiguration != null ? defaultConfiguration.hashCode() : 0);
      result = 31 * result + (transport.clusterName  != null ? transport.clusterName .hashCode() : 0);
      result = 31 * result + (shutdown.hookBehavior.hashCode());
      result = 31 * result + ((int) serialization.version.hashCode());
      result = (int) (31 * result + transport.distributedSyncTimeout);
      return result;
   }

   @Override
   public GlobalConfiguration clone() {
      try {
         GlobalConfiguration dolly = (GlobalConfiguration) super.clone();
         if (asyncListenerExecutor != null) dolly.asyncListenerExecutor = asyncListenerExecutor.clone();
         if (asyncTransportExecutor != null) dolly.asyncTransportExecutor = asyncTransportExecutor.clone();
         if (evictionScheduledExecutor != null) dolly.evictionScheduledExecutor = evictionScheduledExecutor.clone();
         if (replicationQueueScheduledExecutor != null) dolly.replicationQueueScheduledExecutor = replicationQueueScheduledExecutor.clone();
         if (globalJmxStatistics != null) dolly.globalJmxStatistics = (GlobalJmxStatisticsType) globalJmxStatistics.clone();
         if (transport != null) dolly.transport = transport.clone();
         if (serialization != null) dolly.serialization = (SerializationType) serialization.clone();
         if (shutdown != null) dolly.shutdown = (ShutdownType) shutdown.clone();
         return dolly;
      }
      catch (CloneNotSupportedException e) {
         throw new CacheException("Problems cloning configuration component!", e);
      }
   }

   /**
    * Helper method that gets you a default constructed GlobalConfiguration, preconfigured to use the default clustering
    * stack.
    *
    * @return a new global configuration
    */
   public static GlobalConfiguration getClusteredDefault() {
      GlobalConfiguration gc = new GlobalConfiguration();
      gc.setTransportClass(JGroupsTransport.class.getName());
      gc.setTransportProperties((Properties) null);
      Properties p = new Properties();
      p.setProperty("threadNamePrefix", "asyncTransportThread");
      gc.setAsyncTransportExecutorProperties(p);
      return gc;
   }

   /**
    * Helper method that gets you a default constructed GlobalConfiguration, preconfigured for use in LOCAL mode
    *
    * @return a new global configuration
    */
   public static GlobalConfiguration getNonClusteredDefault() {
      GlobalConfiguration gc = new GlobalConfiguration();
      gc.setTransportClass(null);
      gc.setTransportProperties((Properties) null);
      return gc;
   }
   
   /**
    * 
    * @configRef name="asyncListenerExecutor",desc="Executor for listeners."
    * @configRef name="asyncTransportExecutor",desc="Transport executor."
    * @configRef name="evictionScheduledExecutor",desc="Eviction executor."
    * @configRef name="replicationQueueScheduledExecutor",desc="Executor for replication."
    */
   @XmlAccessorType(XmlAccessType.PROPERTY)
   public static class FactoryClassWithPropertiesType extends AbstractConfigurationBeanWithGCR {
      
      /** @configRef desc="Executor fully qualified class name" */
      @XmlAttribute
      protected String factory;
      
      /** 
       * @configPropertyRef name="maxThreads",desc="Number of threads for this executor"
       * @configPropertyRef name="threadNamePrefix",desc="Name prefix for threads created in this executor"
       * */
      @XmlElement(name="properties")
      protected TypedProperties properties = EMPTY_PROPERTIES;

      public FactoryClassWithPropertiesType(String factory) {
         super();
         this.factory = factory;
      }   
      
      public void accept(ConfigurationBeanVisitor v) {
         v.visitFactoryClassWithPropertiesType(this);
      }

      public FactoryClassWithPropertiesType() {
         super();
         this.factory = "";
      }

      public void setFactory(String factory) {
         testImmutability("factory");
         this.factory = factory;
      }

      public void setProperties(TypedProperties properties) {
         testImmutability("properties");
         this.properties = properties;
      }

      @Override
      public FactoryClassWithPropertiesType clone() throws CloneNotSupportedException {
         FactoryClassWithPropertiesType dolly = (FactoryClassWithPropertiesType) super.clone();
         dolly.properties = (TypedProperties) properties.clone();
         return dolly;
      }
   }
   
   /**
    * 
    * @configRef name="transport",desc="Determines Infinispan transport type and accompanying properties." 
    */   
   @XmlAccessorType(XmlAccessType.PROPERTY)
   public static class TransportType extends AbstractConfigurationBeanWithGCR {
     
      /** @configRef desc="Cluster name where all cache instances defined are connected" */
      protected String clusterName = "Infinispan-Cluster";
      
      /** @configRef desc="todo" */ 
      protected Long distributedSyncTimeout = 60000L; // default
     
      /** @configRef desc="Fully qualified name of a class that implements network transport"*/
      protected String transportClass = null; // this defaults to a non-clustered cache.
      
      /**
       * @configRef desc="Node name for the underlying transport channel"
       */
      protected String nodeName = null;
      
      protected TypedProperties properties = EMPTY_PROPERTIES;

      public TransportType() {
         super();
         transportClass = JGroupsTransport.class.getName();
      }
      
      public void accept(ConfigurationBeanVisitor v) {
         v.visitTransportType(this);
      }

      public TransportType(String transportClass) {
         super();
         this.transportClass = transportClass;
      }

      @XmlAttribute
      public void setClusterName(String clusterName) {
         testImmutability("clusterName");
         this.clusterName = clusterName;
      }

      @XmlAttribute
      public void setDistributedSyncTimeout(Long distributedSyncTimeout) {
         testImmutability("distributedSyncTimeout");
         this.distributedSyncTimeout = distributedSyncTimeout;
      }

      @XmlAttribute
      public void setTransportClass(String transportClass) {
         testImmutability("transportClass");
         this.transportClass = transportClass;
      }

      @XmlAttribute
      public void setNodeName(String nodeName) {
         testImmutability("nodeName");
         this.nodeName = nodeName;
      }

    @XmlElement
      public void setProperties(TypedProperties properties) {
         testImmutability("properties");
         this.properties = properties;
      }

      @Override
      public TransportType clone() throws CloneNotSupportedException {
         TransportType dolly = (TransportType) super.clone();
         dolly.properties = (TypedProperties) properties.clone();
         return dolly;
      }
   }
   
   /**
    * 
    * @configRef name="serialization",desc="Serialization and marshalling settings."
    */   
   @XmlAccessorType(XmlAccessType.PROPERTY)
   public static class SerializationType extends AbstractConfigurationBeanWithGCR {
      
      /** @configRef desc="Fully qualified name of a class that marshalls objects between cache nodes"*/
      protected String marshallerClass = VersionAwareMarshaller.class.getName(); // the default
      
      /** @configRef desc="Marshalling serialization version"*/
      protected String version = Version.getMajorVersion();
      
      public SerializationType() {        
         super();
      }

      public void accept(ConfigurationBeanVisitor v) {
         v.visitSerializationType(this);
      }

      @XmlAttribute
      public void setMarshallerClass(String marshallerClass) {
         testImmutability("marshallerClass");
         this.marshallerClass = marshallerClass;
      }

      @XmlAttribute
      public void setVersion(String version) {
         testImmutability("version");
         this.version = version;
      }                      
   }
   
   /**
    * 
    * @configRef name="globalJmxStatistics",desc="Determines global JMX settings for all cache instances." 
    */
   @XmlAccessorType(XmlAccessType.PROPERTY)
   public static class GlobalJmxStatisticsType extends AbstractConfigurationBeanWithGCR {
      
      /** @configRef desc="Toggle to enable/disable exposing Infinispan objects to JMX" */
      protected Boolean enabled = false;
      
      /** @configRef desc="JMX domain name where all relevant JMX exposed objects will be bound" */
      protected String jmxDomain = "infinispan";
      
      /** @configRef desc="Fully qualified name of class that will attempt to find JMX MBean server" */
      protected String mBeanServerLookup = PlatformMBeanServerLookup.class.getName();
      
      /** @configRef desc="If true, multiple cache manager instances could be configured under the same JMX domain" */
      protected Boolean allowDuplicateDomains = false;

      @XmlAttribute
      public void setEnabled(Boolean enabled) {
         testImmutability("enabled");
         this.enabled = enabled;
      }

      public void accept(ConfigurationBeanVisitor v) {
         v.visitGlobalJmxStatisticsType(this);
      }

      @XmlAttribute
      public void setJmxDomain(String jmxDomain) {
         testImmutability("jmxDomain");
         this.jmxDomain = jmxDomain;
      }

      @XmlAttribute
      public void setMBeanServerLookup(String beanServerLookup) {
         testImmutability("mBeanServerLookup");
         mBeanServerLookup = beanServerLookup;
      }

      @XmlAttribute
      public void setAllowDuplicateDomains(Boolean allowDuplicateDomains) {
         testImmutability("allowDuplicateDomains");
         this.allowDuplicateDomains = allowDuplicateDomains;
      }            
   }
   
   /**
    * 
    * @configRef name="shutdown",desc="Determines shutdown hook settings. 
    * By default a shutdown hook is registered even if no MBean server (apart from the JDK default) is detected."
    */   
   @XmlAccessorType(XmlAccessType.PROPERTY)
   public static class ShutdownType extends AbstractConfigurationBeanWithGCR {
      
      /** @configRef desc="Behavior of the JVM shutdown hook registered by the cache" */
      protected ShutdownHookBehavior hookBehavior = ShutdownHookBehavior.DEFAULT;

      @XmlAttribute
      public void setHookBehavior(ShutdownHookBehavior hookBehavior) {
         testImmutability("hookBehavior");
         this.hookBehavior = hookBehavior;
      }

      public void accept(ConfigurationBeanVisitor v) {
         v.visitShutdownType(this);
      }
   }
}

abstract class AbstractConfigurationBeanWithGCR extends AbstractConfigurationBean{

   GlobalComponentRegistry gcr = null;
   
   
   @Inject
   public void inject(GlobalComponentRegistry gcr) {
      this.gcr = gcr;
   }


   @Override
   protected boolean hasComponentStarted() {
      return gcr != null && gcr.getStatus() != null && gcr.getStatus() == ComponentStatus.RUNNING;
   }
   
}
 class PropertiesType {
    
   @XmlElement(name = "property")
   Property properties[];
}

class Property {
   
   @XmlAttribute
   String name;
   
   @XmlAttribute
   String value;
}
