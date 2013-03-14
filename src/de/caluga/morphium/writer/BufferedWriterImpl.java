package de.caluga.morphium.writer;

import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.StatisticKeys;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * User: Stephan Bösebeck
 * Date: 11.03.13
 * Time: 11:41
 * <p/>
 * TODO: Add documentation here
 */
@SuppressWarnings("EmptyCatchBlock")
public class BufferedWriterImpl implements Writer {

    private Morphium morphium;
    private AnnotationAndReflectionHelper annotationHelper = new AnnotationAndReflectionHelper();
    private Writer directWriter;
    private List<WriteBufferEntry> writeBuffer = new Vector<WriteBufferEntry>(); //synced
    private final Thread housekeeping;
    private boolean running = true;
    private Logger logger = Logger.getLogger(BufferedWriterImpl.class);


    public BufferedWriterImpl() {
        housekeeping = new Thread() {
            @SuppressWarnings("SynchronizeOnNonFinalField")
            public void run() {
                while (running) {
                    //processing and clearing write cache...
                    List<WriteBufferEntry> localBuffer;
                    synchronized (writeBuffer) {
                        localBuffer = writeBuffer;
                        writeBuffer = new Vector<WriteBufferEntry>();
                    }
                    //queueing all ops in queue
                    for (WriteBufferEntry entry : localBuffer) {
                        while (directWriter.writeBufferCount() > morphium.getConfig().getMaxConnections() * morphium.getConfig().getBlockingThreadsMultiplier() * 0.9) {
                            try {

                                if (logger.isDebugEnabled()) {
                                    logger.debug("have to wait - maximum connection limit almost reached");
                                }
                                sleep(500); //wait for threads to finish
                            } catch (InterruptedException e) {
                            }
                        }
                        entry.getToRun().run();
                    }
                    localBuffer = null; //let GC finish the work
                    try {
                        Thread.sleep(morphium.getConfig().getWriteBufferTime());
                    } catch (Exception e) {
                    }
                }
            }
        };
        housekeeping.setDaemon(true);
        housekeeping.start();
    }

    public void addToWriteQueue(Runnable r) {
        WriteBufferEntry wb = new WriteBufferEntry(r, System.currentTimeMillis());
        writeBuffer.add(wb);
    }

    @Override
    public <T> void store(final T o, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.store(o, callback);
            }
        });
    }

    @Override
    public <T> void store(final List<T> lst, AsyncOperationCallback<T> c) {
        if (lst == null || lst.size() == 0) {
//            TODO: c.onOperationSucceeded();
            return;
        }
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.store(lst, callback);
            }
        });
    }

    @Override
    public <T> void updateUsingFields(final T ent, AsyncOperationCallback<T> c, final String... fields) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.updateUsingFields(ent, callback, fields);
            }
        });
    }

    @Override
    public <T> void set(final T toSet, final String field, final Object value, final boolean insertIfNotExists, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.set(toSet, field, value, insertIfNotExists, multiple, callback);
            }
        });
    }


    @Override
    public <T> void set(final Query<T> query, final Map<String, Object> values, final boolean insertIfNotExist, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.set(query, values, insertIfNotExist, multiple, callback);
            }
        });
    }

    @Override
    public <T> void inc(final Query<T> query, final String field, final int amount, final boolean insertIfNotExist, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.inc(query, field, amount, insertIfNotExist, multiple, callback);
            }
        });
    }

    @Override
    public <T> void inc(T toInc, String field, int amount, AsyncOperationCallback<T> callback) {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public void setMorphium(Morphium m) {
        morphium = m;
        annotationHelper = m.getARHelper();
        directWriter = m.getConfig().getWriter();
    }

    @Override
    public <T> void delete(final List<T> lst, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.delete(lst, callback);
            }
        });
    }

    @Override
    public <T> void delete(final T o, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.delete(o, callback);
            }
        });
    }

    @Override
    public <T> void delete(final Query<T> q, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.delete(q, callback);
            }
        });
    }

    @Override
    public <T> void pushPull(final boolean push, final Query<T> query, final String field, final Object value, final boolean insertIfNotExist, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.pushPull(push, query, field, value, insertIfNotExist, multiple, callback);
            }
        });
    }

    @Override
    public <T> void pushPullAll(final boolean push, final Query<T> query, final String field, final List<?> value, final boolean insertIfNotExist, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.pushPullAll(push, query, field, value, insertIfNotExist, multiple, callback);
            }
        });
    }

    @Override
    public <T> void unset(final T toSet, final String field, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.unset(toSet, field, callback);
            }
        });
    }

    @Override
    public <T> void dropCollection(final Class<T> cls, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.dropCollection(cls, callback);
            }
        });
    }

    @Override
    public <T> void ensureIndex(final Class<T> cls, final Map<String, Object> index, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<T>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(new Runnable() {
            @Override
            public void run() {
                directWriter.ensureIndex(cls, index, callback);
            }
        });
    }


    @Override
    public int writeBufferCount() {
        return writeBuffer.size();
    }


    private class WriteBufferEntry {
        private Runnable toRun;
        private long timestamp;

        private WriteBufferEntry(Runnable toRun, long timestamp) {
            this.toRun = toRun;
            this.timestamp = timestamp;
        }

        public Runnable getToRun() {
            return toRun;
        }

        public void setToRun(Runnable toRun) {
            this.toRun = toRun;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    private class AsyncOpAdapter<T> implements AsyncOperationCallback<T> {

        @Override
        public void onOperationSucceeded(AsyncOperationType type, Query<T> q, long duration, List<T> result, T entity, Object... param) {
        }

        @Override
        public void onOperationError(AsyncOperationType type, Query<T> q, long duration, String error, Throwable t, T entity, Object... param) {

        }
    }
}