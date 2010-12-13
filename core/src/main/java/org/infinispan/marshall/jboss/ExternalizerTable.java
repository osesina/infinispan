/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.marshall.jboss;

import org.infinispan.CacheException;
import org.infinispan.atomic.AtomicHashMap;
import org.infinispan.atomic.AtomicHashMapDelta;
import org.infinispan.atomic.ClearOperation;
import org.infinispan.atomic.PutOperation;
import org.infinispan.atomic.RemoveOperation;
import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.config.ConfigurationException;
import org.infinispan.config.ExternalizerConfig;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.config.GlobalConfiguration.ExternalizersType;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.ImmortalCacheValue;
import org.infinispan.container.entries.MortalCacheEntry;
import org.infinispan.container.entries.MortalCacheValue;
import org.infinispan.container.entries.TransientCacheEntry;
import org.infinispan.container.entries.TransientCacheValue;
import org.infinispan.container.entries.TransientMortalCacheEntry;
import org.infinispan.container.entries.TransientMortalCacheValue;
import org.infinispan.distribution.ch.DefaultConsistentHash;
import org.infinispan.distribution.ch.NodeTopologyInfo;
import org.infinispan.distribution.ch.TopologyAwareConsistentHash;
import org.infinispan.distribution.ch.UnionConsistentHash;
import org.infinispan.io.UnsignedNumeric;
import org.infinispan.loaders.bucket.Bucket;
import org.infinispan.marshall.Externalizer;
import org.infinispan.marshall.Ids;
import org.infinispan.marshall.Marshalls;
import org.infinispan.marshall.MarshalledValue;
import org.infinispan.marshall.StreamingMarshaller;
import org.infinispan.marshall.exts.ArrayListExternalizer;
import org.infinispan.marshall.exts.LinkedListExternalizer;
import org.infinispan.marshall.exts.MapExternalizer;
import org.infinispan.marshall.exts.ReplicableCommandExternalizer;
import org.infinispan.marshall.exts.SetExternalizer;
import org.infinispan.marshall.exts.SingletonListExternalizer;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.ExtendedResponse;
import org.infinispan.remoting.responses.RequestIgnoredResponse;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.responses.UnsuccessfulResponse;
import org.infinispan.remoting.responses.UnsureResponse;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.transaction.TransactionLog;
import org.infinispan.transaction.xa.DldGlobalTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.ByteArrayKey;
import org.infinispan.util.Immutables;
import org.infinispan.util.ReflectionUtil;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Externalizer table maintains information necessary to be able to map a particular type with the corresponding
 * {@link Externalizer} implementation that it marshall, and it also keeps information of which {@link Externalizer}
 * should be used to read data from a buffer given a particular {@link Externalizer} identifier.
 *
 * These tables govern how either internal Infinispan classes, or user defined classes, are marshalled to a given
 * output, or how these are unmarshalled from a given input.
 *
 * @author Galder Zamarreño
 * @since 5.0
 */
class ExternalizerTable implements ObjectTable {
   private static final Log log = LogFactory.getLog(ExternalizerTable.class);
   private final Set<Class<? extends Externalizer>> internalExternalizers = new HashSet<Class<? extends Externalizer>>();
   // TODO These collection will go once other modules have been enabled to provide their own externalizers
   private static final Set<String> TMP_EXTERNALIZERS = new HashSet<String>();

   static {
      TMP_EXTERNALIZERS.add("org.infinispan.tree.NodeKey$Externalizer");
      TMP_EXTERNALIZERS.add("org.infinispan.tree.Fqn$Externalizer");
      TMP_EXTERNALIZERS.add("org.infinispan.server.core.CacheValue$Externalizer");
      TMP_EXTERNALIZERS.add("org.infinispan.server.memcached.MemcachedValue$Externalizer");
      TMP_EXTERNALIZERS.add("org.infinispan.server.hotrod.TopologyAddress$Externalizer");
      TMP_EXTERNALIZERS.add("org.infinispan.server.hotrod.TopologyView$Externalizer");
   }

   /**
    * Contains mapping of classes to their corresponding Externalizer classes via ExternalizerAdapter instances.
    */
   private final Map<Class<?>, ExternalizerAdapter> writers = new WeakHashMap<Class<?>, ExternalizerAdapter>();

   /**
    * Contains mapping of ids to their corresponding Externalizer classes via ExternalizerAdapter instances.
    * This maps contains mappings for both internal and foreign or user defined externalizers.
    *
    * Internal ids are only allowed to be unsigned bytes (0 to 254). 255 is an special id that signals the
    * arrival of a foreign externalizer id. Foreign externalizers are only allowed to use positive ids that between 0
    * and Integer.MAX_INT. To avoid clashes between foreign and internal ids, foreign ids are transformed into negative
    * values to be stored in this map. This way, we avoid the need of a second map to hold user defined externalizers.
    */
   private final Map<Integer, ExternalizerAdapter> readers = new HashMap<Integer, ExternalizerAdapter>();

   private volatile boolean started;

   private void initInternalExternalizers() {
      internalExternalizers.add(ArrayListExternalizer.class);
      internalExternalizers.add(LinkedListExternalizer.class);
      internalExternalizers.add(MapExternalizer.class);
      internalExternalizers.add(SetExternalizer.class);
      internalExternalizers.add(SingletonListExternalizer.class);

      internalExternalizers.add(GlobalTransaction.Externalizer.class);
      internalExternalizers.add(DldGlobalTransaction.Externalizer.class);
      internalExternalizers.add(JGroupsAddress.Externalizer.class);
      internalExternalizers.add(Immutables.ImmutableMapWrapperExternalizer.class);
      internalExternalizers.add(MarshalledValue.Externalizer.class);

      internalExternalizers.add(TransactionLog.LogEntry.Externalizer.class);
      internalExternalizers.add(ExtendedResponse.Externalizer.class);
      internalExternalizers.add(SuccessfulResponse.Externalizer.class);
      internalExternalizers.add(ExceptionResponse.Externalizer.class);
      internalExternalizers.add(RequestIgnoredResponse.Externalizer.class);
      internalExternalizers.add(UnsuccessfulResponse.Externalizer.class);
      internalExternalizers.add(UnsureResponse.Externalizer.class);

      internalExternalizers.add(ReplicableCommandExternalizer.class);

      internalExternalizers.add(ImmortalCacheEntry.Externalizer.class);
      internalExternalizers.add(MortalCacheEntry.Externalizer.class);
      internalExternalizers.add(TransientCacheEntry.Externalizer.class);
      internalExternalizers.add(TransientMortalCacheEntry.Externalizer.class);
      internalExternalizers.add(ImmortalCacheValue.Externalizer.class);
      internalExternalizers.add(MortalCacheValue.Externalizer.class);
      internalExternalizers.add(TransientCacheValue.Externalizer.class);
      internalExternalizers.add(TransientMortalCacheValue.Externalizer.class);

      internalExternalizers.add(AtomicHashMap.Externalizer.class);
      internalExternalizers.add(Bucket.Externalizer.class);
      internalExternalizers.add(AtomicHashMapDelta.Externalizer.class);
      internalExternalizers.add(PutOperation.Externalizer.class);
      internalExternalizers.add(RemoveOperation.Externalizer.class);
      internalExternalizers.add(ClearOperation.Externalizer.class);
      internalExternalizers.add(DefaultConsistentHash.Externalizer.class);
      internalExternalizers.add(UnionConsistentHash.Externalizer.class);
      internalExternalizers.add(NodeTopologyInfo.Externalizer.class);
      internalExternalizers.add(TopologyAwareConsistentHash.Externalizer.class);
      internalExternalizers.add(ByteArrayKey.Externalizer.class);
   }

   void addInternalExternalizer(Class<? extends Externalizer> extClass) {
      internalExternalizers.add(extClass);
   }

   public void start(RemoteCommandsFactory cmdFactory, StreamingMarshaller ispnMarshaller, GlobalConfiguration globalCfg) {
      initInternalExternalizers();
      loadInternalMarshallables(cmdFactory, ispnMarshaller);
      loadForeignMarshallables(globalCfg);
      started = true;
      if (log.isTraceEnabled()) {
         log.trace("Constant object table was started and contains these externalizer readers: {0}", readers);
         log.trace("The externalizer writers collection contains: {0}", writers);
      }
   }

   public void stop() {
      internalExternalizers.clear();
      writers.clear();
      readers.clear();
      started = false;
      if (log.isTraceEnabled())
         log.trace("Externalizer reader and writer maps have been cleared and constant object table was stopped");
   }

   public Writer getObjectWriter(Object o) throws IOException {
      Class clazz = o.getClass();
      Writer writer = writers.get(clazz);
      if (writer == null) {
         if (log.isTraceEnabled())
            log.trace("No externalizer available for {0}", clazz);

         if (Thread.currentThread().isInterrupted())
            throw new IOException(String.format(
                  "Cache manager is shutting down, so type write externalizer for type=%s cannot be resolved. Interruption being pushed up.",
                  clazz.getName()), new InterruptedException());
      }
      return writer;
   }

   public Object readObject(Unmarshaller input) throws IOException, ClassNotFoundException {
      int readerIndex = input.readUnsignedByte();
      if (readerIndex == Ids.MAX_ID) // User defined externalizer
         readerIndex = generateForeignReaderIndex(UnsignedNumeric.readUnsignedInt(input));

      ExternalizerAdapter adapter = readers.get(readerIndex);
      if (adapter == null) {
         if (!started) {
            if (log.isTraceEnabled())
               log.trace("Either the marshaller has stopped or hasn't started. Read externalizers are not propery populated: {0}", readers);

            if (Thread.currentThread().isInterrupted())
               throw new IOException(String.format(
                     "Cache manager is shutting down, so type (id=%d) cannot be resolved. Interruption being pushed up.",
                     readerIndex), new InterruptedException());
            else
               throw new CacheException(String.format(
                     "Cache manager is either starting up or shutting down but it's not interrupted, so type (id=%d) cannot be resolved.",
                     readerIndex));
         } else {
            if (log.isTraceEnabled()) {
               log.trace("Unknown type. Input stream has {0} to read", input.available());
               log.trace("Check contents of read externalizers: {0}", readers);
            }

            throw new CacheException(String.format(
                  "Type of data read is unknown. Id=%d is not amongst known reader indexes.",
                  readerIndex));
         }
      }

      return adapter.readObject(input);
   }

   boolean isMarshallable(Object o) {
      return writers.containsKey(o.getClass());
   }

   int getExternalizerId(Object o) {
      return writers.get(o.getClass()).getExternalizerId();
   }

   private void loadInternalMarshallables(RemoteCommandsFactory cmdFactory, StreamingMarshaller ispnMarshaller) {
      // TODO Remove once submodules can defined their own externalizers
      for (String tmpExtClassName : TMP_EXTERNALIZERS) {
         try {
            Class<? extends Externalizer> tmpExtClass = Util.loadClassStrict(tmpExtClassName);
            internalExternalizers.add(tmpExtClass);
         } catch (ClassNotFoundException e) {
            if (!tmpExtClassName.startsWith("org.infinispan")) {
               if (log.isDebugEnabled()) log.debug("Unable to load class {0}", e.getMessage());
            }
         }
      }

      for (Class<? extends Externalizer> extClass : internalExternalizers) {
         Marshalls marshalls = ReflectionUtil.getAnnotation(extClass, Marshalls.class);
         Externalizer ext = Util.getInstance(extClass);
         if (ext instanceof ReplicableCommandExternalizer)
            ((ReplicableCommandExternalizer) ext).inject(cmdFactory);
         if (ext instanceof MarshalledValue.Externalizer)
            ((MarshalledValue.Externalizer) ext).inject(ispnMarshaller);

         int id = checkInternalIdLimit(marshalls.id(), ext);
         updateExtReadersWritersWithTypes(marshalls, new ExternalizerAdapter(id, ext));
      }
   }

   private void updateExtReadersWritersWithTypes(Marshalls marshalls, ExternalizerAdapter adapter) {
      updateExtReadersWritersWithTypes(marshalls, adapter, adapter.id);
   }

   private void updateExtReadersWritersWithTypes(Marshalls marshalls, ExternalizerAdapter adapter, int readerIndex) {
      Class[] typeClasses = marshalls.typeClasses();
      String[] typeClassNames = marshalls.typeClassNames();
      if (typeClasses.length > 0) {
         for (Class typeClass : typeClasses)
            updateExtReadersWriters(adapter, typeClass, readerIndex);
      } else if (typeClassNames.length > 0) {
         for (String typeClassName : typeClassNames)
            updateExtReadersWriters(adapter, Util.loadClass(typeClassName), readerIndex);
      } else {
         throw new ConfigurationException(String.format(
               "Marshalls annotation in class %s must be populated with either typeClasses or typeClassNames",
               adapter.externalizer.getClass().getName()));
      }
   }

   private void loadForeignMarshallables(GlobalConfiguration globalCfg) {
      if (log.isTraceEnabled())
         log.trace("Loading user defined externalizers");
      ExternalizersType type = globalCfg.getExternalizersType();
      List<ExternalizerConfig> configs = type.getExternalizerConfigs();
      for (ExternalizerConfig config : configs) {
         Externalizer ext = config.getExternalizer() != null ? config.getExternalizer()
               : (Externalizer) Util.getInstance(config.getExternalizerClass());

         Marshalls marshalls = ReflectionUtil.getAnnotation(ext.getClass(), Marshalls.class);
         // If no XML or programmatic config, id in annotation is used
         // as long as it's not default one (meaning, user did not set it).
         // If XML or programmatic config in use ignore @Marshalls annotation and use value in config.
         int id = marshalls.id();
         if (config.getId() == null && id == Integer.MAX_VALUE)
            throw new ConfigurationException(String.format(
                  "No externalizer identifier set for externalizer %s", ext.getClass().getName()));
         else if (config.getId() != null)
            id = config.getId();

         id = checkForeignIdLimit(id, ext);
         updateExtReadersWritersWithTypes(marshalls, new ForeignExternalizerAdapter(id, ext), generateForeignReaderIndex(id));
      }
   }

   private void updateExtReadersWriters(ExternalizerAdapter adapter, Class typeClass, int readerIndex) {
      writers.put(typeClass, adapter);
      ExternalizerAdapter prevReader = readers.put(readerIndex, adapter);
      // Several externalizers might share same id (i.e. HashMap and TreeMap use MapExternalizer)
      // but a duplicate is only considered when that particular index has already been entered
      // in the readers map and the externalizers are different (they're from different classes)
      if (prevReader != null && !prevReader.equals(adapter))
         throw new ConfigurationException(String.format(
               "Duplicate id found! Externalizer id=%d for %s is shared by another externalizer (%s). Reader index is %d",
               adapter.id, typeClass, prevReader.externalizer.getClass().getName(), readerIndex));

      if (log.isTraceEnabled())
         log.trace("Loaded externalizer {0} for {1} with id {2} and reader index {3}",
                   adapter.externalizer.getClass().getName(), typeClass, adapter.id, readerIndex);

   }

   private int checkInternalIdLimit(int id, Externalizer ext) {
      if (id >= Ids.MAX_ID)
         throw new ConfigurationException(String.format(
               "Internal %s externalizer is using an id(%d) that exceeed the limit. It needs to be smaller than %d",
               ext, id, Ids.MAX_ID));
      return id;
   }

   private int checkForeignIdLimit(int id, Externalizer ext) {
      if (id < 0)
         throw new ConfigurationException(String.format(
               "Foreign %s externalizer is using a negative id(%d). Only positive id values are allowed.",
               ext, id));
      return id;
   }

   private int generateForeignReaderIndex(int foreignId) {
      return 0x80000000 | foreignId;
   }

   static class ExternalizerAdapter implements Writer {
      final int id;
      final Externalizer externalizer;

      ExternalizerAdapter(int id, Externalizer externalizer) {
         this.id = id;
         this.externalizer = externalizer;
      }

      public Object readObject(Unmarshaller input) throws IOException, ClassNotFoundException {
         return externalizer.readObject(input);
      }

      public void writeObject(Marshaller output, Object object) throws IOException {
         output.write(id);
         externalizer.writeObject(output, object);
      }

      int getExternalizerId() {
         return id;
      }

      @Override
      public String toString() {
         // Each adapter is represented by the externalizer it delegates to, so just return the class name
         return externalizer.getClass().getName();
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         ExternalizerAdapter that = (ExternalizerAdapter) o;
         if (id != that.id) return false;
         if (externalizer != null ? !externalizer.getClass().equals(that.externalizer.getClass()) : that.externalizer != null) return false;
         return true;
      }

      @Override
      public int hashCode() {
         int result = id;
         result = 31 * result + (externalizer.getClass() != null ? externalizer.getClass().hashCode() : 0);
         return result;
      }
   }

   static class ForeignExternalizerAdapter extends ExternalizerAdapter {
      final int foreignId;

      ForeignExternalizerAdapter(int foreignId, Externalizer externalizer) {
         super(Ids.MAX_ID, externalizer);
         this.foreignId = foreignId;
      }

      @Override
      int getExternalizerId() {
         return foreignId;
      }

      @Override
      public void writeObject(Marshaller output, Object object) throws IOException {
         output.write(id);
         // Write as an unsigned, variable length, integer to safe space
         UnsignedNumeric.writeUnsignedInt(output, foreignId);
         externalizer.writeObject(output, object);
      }
   }
}