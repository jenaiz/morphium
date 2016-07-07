package de.caluga.morphium;

import de.caluga.morphium.cache.CacheElement;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("StringBufferMayBeStringBuilder")
public class Statistics extends HashMap<String, Double> {
    private static final long serialVersionUID = -2368547656520608318L;
    private final transient Morphium morphium;


    @SuppressWarnings("rawtypes")
    public Statistics(Morphium morphium) {
        this.morphium = morphium;
        for (Map.Entry<StatisticKeys, StatisticValue> et : morphium.getStats().entrySet()) {
            super.put(et.getKey().name(), (double) et.getValue().get());
        }
        double entries = 0;
        Map<Class<?>, Map<String, CacheElement>> cc = morphium.getCache().getCache();
        for (Map.Entry<Class<?>, Map<String, CacheElement>> en : cc.entrySet()) {
            Map<String, CacheElement> lst = en.getValue();
            entries += lst.size();
            super.put("X-Entries for: " + en.getKey().getName(), (double) lst.size());
        }
        super.put(StatisticKeys.CACHE_ENTRIES.name(), entries);


        super.put(StatisticKeys.WRITE_BUFFER_ENTRIES.name(), (double) morphium.getWriteBufferCount());
        super.put(StatisticKeys.CHITSPERC.name(), ((double) morphium.getStats().get(StatisticKeys.CHITS).get()) / (morphium.getStats().get(StatisticKeys.CHITS).get() + morphium.getStats().get(StatisticKeys.CMISS).get() - morphium.getStats().get(StatisticKeys.NO_CACHED_READS).get()) * 100.0);
        super.put(StatisticKeys.CMISSPERC.name(), ((double) morphium.getStats().get(StatisticKeys.CMISS).get()) / (morphium.getStats().get(StatisticKeys.CHITS).get() + morphium.getStats().get(StatisticKeys.CMISS).get() - morphium.getStats().get(StatisticKeys.NO_CACHED_READS).get()) * 100.0);
    }


    @SuppressWarnings("unused")
    public Double get(Enum key) {
        return get(key.name());
    }

    @Override
    public Double put(String arg0, Double arg1) {
        throw new RuntimeException("not allowed!");
    }

    @Override
    public void putAll(@SuppressWarnings("rawtypes") Map arg0) {
        throw new RuntimeException("not allowed");
    }

    @Override
    public Double remove(Object arg0) {
        throw new RuntimeException("not allowed");
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        StatisticKeys[] lst = morphium.getStats().keySet().toArray(new StatisticKeys[morphium.getStats().keySet().size()]);
        Arrays.sort(lst);
        for (StatisticKeys k : lst) {
            b.append("- ");
            b.append(k.toString());
            b.append("\t");
            b.append(morphium.getStats().get(k));
            b.append("\n");
        }
        return b.toString();
    }
}
