package com.github.simonthecat.hbasememtest;

import org.apache.hadoop.hbase.filter.CompareFilter;

import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.apache.commons.lang.ArrayUtils.isEmpty;

public class InMemStore {

    private static final BinaryComparator BINARY_COMPARATOR = new BinaryComparator();

    // HBase data is a multidimensional map
    // rowkey -> family -> column -> timestamp -> value
    private NavigableMap<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> data = new TreeMap<>(new BinaryComparator());

    public InMemStore() {
    }

    public boolean containsKey(byte[] row) {
        return data.containsKey(row);
    }

    public Iterable<byte[]> getRowKeys() {
        return data.keySet();
    }

    public NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> getByKey(byte[] rowKey) {
        if (data.containsKey(rowKey)) {
            return data.get(rowKey);
        }

        return Collections.emptyNavigableMap();
    }


    public NavigableMap<byte[], NavigableMap<Long, byte[]>> getByKeyAndFamily(byte[] rowKey, byte[] family) {
        if (data.containsKey(rowKey) && data.get(rowKey).containsKey(family)) {
            return getByKey(rowKey).get(family);
        }

        return Collections.emptyNavigableMap();
    }

    public NavigableMap<Long, byte[]> getByKeyAndFamilyAndQualifier(byte[] rowKey, byte[] family, byte[] qualifier) {
        if (getByKeyAndFamily(rowKey, family).containsKey(qualifier)) {
            return getByKeyAndFamily(rowKey, family).get(qualifier);
        }

        return Collections.emptyNavigableMap();
    }

    public byte[] getValue(byte[] rowKey, byte[] family, byte[] qualifier, Long ts) {
        return getByKeyAndFamilyAndQualifier(rowKey, family, qualifier).get(ts);
    }


    public NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> getByKeyBetweenTimestamps(byte[] rowKey, Long minTimestamp, Long maxTimestamp) {
        NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> results = new TreeMap<>(BINARY_COMPARATOR);

        for (byte[] family : getByKey(rowKey).keySet()) {
            NavigableMap<byte[], NavigableMap<Long, byte[]>> qualifierTsValueMatching = new TreeMap<>(BINARY_COMPARATOR);

            for (byte[] qualifier : getByKeyAndFamily(rowKey, family).keySet()) {
                NavigableMap<Long, byte[]> tsValueMatching = new TreeMap<>();

                for (Long ts : getByKeyAndFamilyAndQualifier(rowKey, family, qualifier).keySet()) {
                    if (ts > minTimestamp && ts < maxTimestamp) {
                        tsValueMatching.put(ts, getValue(rowKey, family, qualifier, ts));
                    }
                }
                if (!tsValueMatching.isEmpty()) qualifierTsValueMatching.put(qualifier, tsValueMatching);
            }

            if (!qualifierTsValueMatching.isEmpty()) results.put(family, qualifierTsValueMatching);
        }

        return results;
    }

    public NavigableMap<Long, byte[]> getByKeyAndFamilyAndQualifierBetweenTimestamps(byte[] rowKey, byte[] family, byte[] qualifier, Long minTimestamp, Long maxTimestamp) {
        NavigableMap<Long, byte[]> results = new TreeMap<>();

        NavigableMap<Long, byte[]> values = getByKeyAndFamilyAndQualifier(rowKey, family, qualifier);
        for (Long ts : values.keySet()) {
            if (ts > minTimestamp && ts < maxTimestamp) {
                results.put(ts, values.get(ts));
            }
        }

        return results;
    }

    public void insert(byte[] rowKey, byte[] family, byte[] qualifier, long timestamp, byte[] value) {
        if (!data.containsKey(rowKey)) {
            data.put(rowKey, new TreeMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>(BINARY_COMPARATOR));
        }

        if (!data.get(rowKey).containsKey(family)) {
            data.get(rowKey).put(family, new TreeMap<byte[], NavigableMap<Long, byte[]>>(BINARY_COMPARATOR));
        }

        if (!data.get(rowKey).get(family).containsKey(qualifier)) {
            data.get(rowKey).get(family).put(qualifier, new TreeMap<Long, byte[]>());
        }

        data.get(rowKey).get(family).get(qualifier).put(timestamp, value);
    }

    public void deleteByKey(byte[] rowKey) {
        data.remove(rowKey);
    }

    public void deleteByKeyAndFamily(byte[] rowKey, byte[] family, long maxTimestamp) {
        for (byte[] qualifier : data.get(rowKey).get(family).keySet()) {
            deleteByKeyAndFamilyAndQualifier(rowKey, family, qualifier, maxTimestamp);
        }
    }

    public void deleteByKeyAndFamilyAndQualifier(byte[] rowKey, byte[] family, byte[] qualifier, long maxTimestamp) {
        Iterator<Long> valueTsIterator = data.get(rowKey).get(family).get(qualifier).keySet().iterator();
        while (valueTsIterator.hasNext()) {
            if (valueTsIterator.next() <= maxTimestamp) valueTsIterator.remove();
        }

        if (data.get(rowKey).get(family).get(qualifier).isEmpty()) {
            data.get(rowKey).get(family).remove(qualifier);
        }

        if (data.get(rowKey).get(family).isEmpty()) {
            data.get(rowKey).remove(family);
        }

        if (data.get(rowKey).isEmpty()) {
            data.remove(rowKey);
        }
    }

    public boolean check(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value) {
        if (isEmpty(value)) {
            return !data.containsKey(row) || !data.get(row).containsKey(family) || !data.get(row).get(family).containsKey(qualifier);
        } else {
            return data.containsKey(row)
                    && data.get(row).containsKey(family)
                    && data.get(row).get(family).containsKey(qualifier)
                    && BINARY_COMPARATOR.byOperator(getByKeyAndFamilyAndQualifier(row, family, qualifier).lastEntry().getValue(), value, compareOp);
        }
    }
}
