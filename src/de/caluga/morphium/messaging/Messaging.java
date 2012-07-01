package de.caluga.morphium.messaging;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumSingleton;
import de.caluga.morphium.Query;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * User: Stephan Bösebeck
 * Date: 26.05.12
 * Time: 15:48
 * <p/>
 * TODO: Add documentation here
 */
public class Messaging extends Thread {
    private static Logger log = Logger.getLogger(Messaging.class);

    private Morphium morphium;
    private boolean running;
    private int pause = 5000;
    private String id;
    private boolean autoAnswer = false;

    private boolean processMultiple = false;

    private List<MessageListener> listeners;
    private Map<String, List<MessageListener>> listenerByName;

    private ThreadPoolExecutor writers = new ThreadPoolExecutor(500, 1000,
            10000L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    public Messaging(Morphium m, int pause, boolean processMultiple) {
        morphium = m;
        running = true;
        this.pause = pause;
        this.processMultiple = processMultiple;
        id = UUID.randomUUID().toString();
        m.ensureIndex(Msg.class, Msg.Fields.lockedBy, Msg.Fields.timestamp);
        m.ensureIndex(Msg.class, Msg.Fields.lockedBy, Msg.Fields.lstOfIdsAlreadyProcessed);
        m.ensureIndex(Msg.class, Msg.Fields.timestamp);

        listeners = new Vector<MessageListener>();
        listenerByName = new Hashtable<String, List<MessageListener>>();
        writers.setCorePoolSize(m.getConfig().getMaxConnections() / 2);
        writers.setMaximumPoolSize(m.getConfig().getMaxConnections());
    }

    public void run() {
        if (log.isDebugEnabled()) {
            log.info("Messaging " + id + " started");
        }
        Map<String, Object> values = new HashMap<String, Object>();
        while (running) {

            try {
                Query<Msg> q = morphium.createQueryFor(Msg.class);
                //removing all outdated stuff
                q = q.where("this.ttl<" + System.currentTimeMillis() + "-this.timestamp");
                if (log.isDebugEnabled() && q.countAll() > 0) {
                    log.info("Deleting outdate messages: " + q.countAll());
                }
                morphium.delete(q);
                q = q.q();
                //locking messages...
                q.or(q.q().f(Msg.Fields.sender).ne(id).f(Msg.Fields.lockedBy).eq(null).f(Msg.Fields.lstOfIdsAlreadyProcessed).ne(id).f(Msg.Fields.to).eq(null),
                        q.q().f(Msg.Fields.sender).ne(id).f(Msg.Fields.lockedBy).eq(null).f(Msg.Fields.lstOfIdsAlreadyProcessed).ne(id).f(Msg.Fields.to).eq(id));
                values.put("locked_by", id);
                values.put("locked", System.currentTimeMillis());
//            morphium.set(Msg.class,q,Msg.Fields.lockedBy,id);
                //need to set upsert to true in order to INSERT fields as well ;-(
                morphium.set(q, values, false, processMultiple);
                //give mongo time to really store

//                try {
//                    sleep(100);
//                } catch (InterruptedException e) {
//                }
                //maybe others "overlocked" our message, but that's ok - we re read all messages...
                q = q.q();
                q.or(q.q().f(Msg.Fields.lockedBy).eq(id),
                        q.q().f(Msg.Fields.lockedBy).eq("ALL").f(Msg.Fields.lstOfIdsAlreadyProcessed).ne(id).f(Msg.Fields.to).eq(id),
                        q.q().f(Msg.Fields.lockedBy).eq("ALL").f(Msg.Fields.lstOfIdsAlreadyProcessed).ne(id).f(Msg.Fields.to).eq(null));
                q.sort(Msg.Fields.timestamp);

                List<Msg> messagesList = q.asList();
                List<Msg> toStore = new ArrayList<Msg>();

                for (Msg msg : messagesList) {
                    msg = morphium.reread(msg); //make sure it's current version in DB
                    if (msg == null) continue; //was deleted
                    if (!msg.getLockedBy().equals(id) && !msg.getLockedBy().equals("ALL")) {
                        //over-locked by someone else
                        continue;
                    }
                    if (msg.getTtl() < System.currentTimeMillis() - msg.getTimestamp()) {
                        //Delete outdated msg!
                        log.warn("Found outdated message - deleting it!");
                        morphium.deleteObject(msg);
                        continue;
                    }
                    try {
                        for (MessageListener l : listeners) {
                            Msg answer = l.onMessage(msg);
                            if (autoAnswer && answer == null) {
                                answer = new Msg(msg.getName(), "received", "");
                            }
                            if (answer != null) {
                                msg.sendAnswer(this, answer);
                            }
                        }

                        if (listenerByName.get(msg.getName()) != null) {
                            for (MessageListener l : listenerByName.get(msg.getName())) {
                                Msg answer = l.onMessage(msg);
                                if (autoAnswer && answer == null) {
                                    answer = new Msg(msg.getName(), "received", "");
                                }
                                if (answer != null) {
                                    msg.sendAnswer(this, answer);
                                }
                            }
                        }
                    } catch (Throwable t) {
//                        msg.addAdditional("Processing of message failed by "+getSenderId()+": "+t.getMessage());
                        log.error("Processing failed", t);
                    }

                    if (msg.getType().equals(MsgType.SINGLE)) {
                        //removing it
                        morphium.deleteObject(msg);
                    }
                    //updating it to be processed by others...
                    if (msg.getLockedBy().equals("ALL")) {
                        Query<Msg> idq = MorphiumSingleton.get().createQueryFor(Msg.class);
                        idq.f(Msg.Fields.msgId).eq(msg.getMsgId());

                        MorphiumSingleton.get().push(idq, Msg.Fields.lstOfIdsAlreadyProcessed, id);
                    } else {
                        //Exclusive message
                        msg.addProcessedId(id);
                        msg.setLockedBy(null);
                        msg.setLocked(0);
                        toStore.add(msg);
                    }

                }
                morphium.storeList(toStore);
            } catch (Throwable e) {
                log.error("Unhandled exception " + e.getMessage(), e);
            } finally {
                try {
                    sleep(pause);
                } catch (InterruptedException e) {
                }
            }


        }
        if (log.isDebugEnabled()) {
            log.debug("Messaging " + id + " stopped!");
        }
        if (!running) {
            listeners.clear();
            listenerByName.clear();
        }
    }

    public void addListenerForMessageNamed(String n, MessageListener l) {
        if (listenerByName.get(n) == null) {
            listenerByName.put(n, new ArrayList<MessageListener>());
        }
        listenerByName.get(n).add(l);
        l.setMessaging(this);
    }

    public void removeListenerForMessageNamed(String n, MessageListener l) {
        l.setMessaging(null);
        if (listenerByName.get(n) == null) {
            return;
        }
        listenerByName.get(n).remove(l);

    }

    public String getSenderId() {
        return id;
    }

    public void setSenderId(String id) {
        this.id = id;
    }

    public int getPause() {
        return pause;
    }

    public void setPause(int pause) {
        this.pause = pause;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;

    }

    public void addMessageListener(MessageListener l) {
        listeners.add(l);
        l.setMessaging(this);
    }

    public void removeMessageListener(MessageListener l) {
        listeners.remove(l);
        l.setMessaging(null);
    }

    public void queueMessage(final Msg m) {
        m.setSender(id);
        m.addProcessedId(id);
        m.setLockedBy(null);
        m.setLocked(0);
        writers.execute(new Runnable() {
            @Override
            public void run() {
                storeMessage(m);
            }
        });
    }

    public void storeMessage(Msg m) {
        m.setSender(id);
        m.addProcessedId(id);
        m.setLockedBy(null);
        m.setLocked(0);
        morphium.storeNoCache(m);
    }

    public boolean isAutoAnswer() {
        return autoAnswer;
    }

    public void setAutoAnswer(boolean autoAnswer) {
        this.autoAnswer = autoAnswer;
    }
}
